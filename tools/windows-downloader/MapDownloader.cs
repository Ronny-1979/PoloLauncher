// PoloLauncher Karten-Downloader: Logik ohne Oberfläche (testbar).
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Threading;

namespace PoloMaps
{
    public sealed class MapJob
    {
        public string Region;
        public string RegionName;
        public string FileName;
        public string Url;
        public bool IsMap;
        public long Size = -1;
        public DateTime Modified = DateTime.MinValue; // UTC, MinValue = unbekannt
        public bool ProbeOk;
        public bool AlreadyComplete;
        public bool Done;
        public string Error;
    }

    public sealed class MapDownloader
    {
        public static readonly string[] Regions = {
            "baden-wuerttemberg", "bayern", "brandenburg", "hamburg",
            "hessen", "mecklenburg-vorpommern", "niedersachsen", "nordrhein-westfalen",
            "rheinland-pfalz", "saarland", "sachsen-anhalt", "sachsen",
            "schleswig-holstein", "thueringen" };

        public static readonly string[] RegionNames = {
            "Baden-Württemberg", "Bayern", "Brandenburg (mit Berlin)", "Hamburg",
            "Hessen", "Mecklenburg-Vorpommern", "Niedersachsen (mit Bremen)", "Nordrhein-Westfalen",
            "Rheinland-Pfalz", "Saarland", "Sachsen-Anhalt", "Sachsen",
            "Schleswig-Holstein", "Thüringen" };

        public const string DefaultBaseUrl = "https://download.geofabrik.de/europe/germany/";
        public const string FolderName = "PoloMaps";
        const int Attempts = 5;
        const long ToleranceSeconds = 2;

        readonly string baseUrl;
        readonly string targetDir;
        volatile bool cancelled;

        /// <summary>Meldung fürs Protokoll.</summary>
        public Action<string> Log = delegate { };
        /// <summary>Gesamtfortschritt: fertige Bytes, Gesamtbytes, aktuelle Datei.</summary>
        public Action<long, long, string> Progress = delegate { };

        public MapDownloader(string baseUrl, string targetDir)
        {
            this.baseUrl = baseUrl.EndsWith("/") ? baseUrl : baseUrl + "/";
            this.targetDir = targetDir;
        }

        public void Cancel() { cancelled = true; }
        public bool IsCancelled { get { return cancelled; } }

        public static string MapFile(string region) { return region + "-shortbread-1.0.mbtiles"; }
        public static string TrafficFile(string region) { return region + "-latest.osm.pbf"; }

        public List<MapJob> BuildJobs(IEnumerable<int> regionIndexes, bool maps, bool traffic)
        {
            List<MapJob> jobs = new List<MapJob>();
            foreach (int i in regionIndexes)
            {
                string r = Regions[i];
                if (maps) jobs.Add(NewJob(i, MapFile(r), true));
                if (traffic) jobs.Add(NewJob(i, TrafficFile(r), false));
            }
            return jobs;
        }

        MapJob NewJob(int regionIndex, string file, bool isMap)
        {
            MapJob j = new MapJob();
            j.Region = Regions[regionIndex];
            j.RegionName = RegionNames[regionIndex];
            j.FileName = file;
            j.Url = baseUrl + file;
            j.IsMap = isMap;
            return j;
        }

        /// <summary>Fragt Größe und Datum aller Dateien ab (HEAD) und erkennt bereits fertige Dateien.</summary>
        public void Probe(List<MapJob> jobs)
        {
            foreach (MapJob j in jobs)
            {
                if (cancelled) return;
                j.ProbeOk = false;
                for (int attempt = 1; attempt <= 3 && !j.ProbeOk && !cancelled; attempt++)
                {
                    try
                    {
                        HttpWebRequest req = NewRequest(j.Url, "HEAD");
                        using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                        {
                            j.Size = resp.ContentLength;
                            j.Modified = resp.LastModified.ToUniversalTime();
                            if (resp.Headers[HttpResponseHeader.LastModified] == null) j.Modified = DateTime.MinValue;
                            j.ProbeOk = j.Size > 0;
                        }
                    }
                    catch (Exception e)
                    {
                        j.Error = e.Message;
                        Thread.Sleep(500 * attempt);
                    }
                }
                if (j.ProbeOk) j.Error = null;
                j.AlreadyComplete = j.ProbeOk && IsComplete(j);
            }
        }

        bool IsComplete(MapJob j)
        {
            string path = Path.Combine(targetDir, j.FileName);
            FileInfo f = new FileInfo(path);
            if (!f.Exists || f.Length != j.Size) return false;
            if (j.Modified == DateTime.MinValue) return true;
            return Math.Abs((f.LastWriteTimeUtc - j.Modified).TotalSeconds) <= ToleranceSeconds;
        }

        /// <summary>Lädt alle noch fehlenden Dateien. Liefert die Zahl der fehlgeschlagenen Dateien.</summary>
        public int Run(List<MapJob> jobs)
        {
            Directory.CreateDirectory(targetDir);
            long total = 0, done = 0;
            foreach (MapJob j in jobs)
            {
                if (!j.ProbeOk) continue;
                total += j.Size;
                if (j.AlreadyComplete) done += j.Size;
            }
            CheckSpace(jobs);
            int failed = 0;
            foreach (MapJob j in jobs)
            {
                if (cancelled) break;
                if (!j.ProbeOk)
                {
                    Log("Übersprungen (Server nicht erreichbar): " + j.FileName);
                    failed++;
                    continue;
                }
                if (j.AlreadyComplete)
                {
                    Log("Schon vorhanden: " + j.FileName);
                    j.Done = true;
                    continue;
                }
                long before = done;
                bool ok = DownloadWithRetry(j, delegate (long fileDone)
                {
                    Progress(before + fileDone, total, j.RegionName + (j.IsMap ? " – Karte" : " – Tempolimits/Blitzer"));
                });
                if (ok) { done += j.Size; j.Done = true; Progress(done, total, j.RegionName); }
                else if (!cancelled) failed++;
            }
            return failed;
        }

        void CheckSpace(List<MapJob> jobs)
        {
            long need = 0;
            foreach (MapJob j in jobs)
            {
                if (!j.ProbeOk || j.AlreadyComplete) continue;
                long have = 0;
                FileInfo part = new FileInfo(Path.Combine(targetDir, j.FileName + ".part"));
                if (part.Exists) have = Math.Min(part.Length, j.Size);
                need += j.Size - have;
            }
            try
            {
                string root = Path.GetPathRoot(Path.GetFullPath(targetDir));
                DriveInfo drive = new DriveInfo(root);
                long free = drive.AvailableFreeSpace;
                if (free < need + 200L * 1024 * 1024)
                    throw new IOException(string.Format("Zu wenig freier Speicher: {0} frei, {1} nötig.",
                        Human(free), Human(need + 200L * 1024 * 1024)));
            }
            catch (IOException) { throw; }
            catch (Exception) { /* Netzwerkpfad o. Ä.: Prüfung nicht möglich */ }
        }

        bool DownloadWithRetry(MapJob j, Action<long> progress)
        {
            for (int attempt = 1; attempt <= Attempts && !cancelled; attempt++)
            {
                try
                {
                    Download(j, progress);
                    return true;
                }
                catch (OperationCanceledException) { return false; }
                catch (Exception e)
                {
                    j.Error = e.Message;
                    Log(string.Format("Fehler bei {0} (Versuch {1}/{2}): {3}", j.FileName, attempt, Attempts, e.Message));
                    for (int s = 0; s < 3 * attempt && !cancelled; s++) Thread.Sleep(1000);
                }
            }
            return false;
        }

        void Download(MapJob j, Action<long> progress)
        {
            string final = Path.Combine(targetDir, j.FileName);
            string part = final + ".part";
            long have = 0;
            FileInfo pi = new FileInfo(part);
            if (pi.Exists)
            {
                if (pi.Length > j.Size) pi.Delete();
                else have = pi.Length;
            }
            if (have == j.Size) { Finish(j, part, final); return; }

            Log(string.Format("Lade {0} ({1}){2}", j.FileName, Human(j.Size), have > 0 ? ", setze bei " + Human(have) + " fort" : ""));
            HttpWebRequest req = NewRequest(j.Url, "GET");
            req.Timeout = 30000;
            req.ReadWriteTimeout = 30000;
            if (have > 0) req.AddRange(have);
            using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
            {
                bool resumed = have > 0 && resp.StatusCode == HttpStatusCode.PartialContent;
                if (have > 0 && !resumed) have = 0; // Server ignoriert Range: von vorn
                using (Stream input = resp.GetResponseStream())
                using (FileStream output = new FileStream(part, resumed ? FileMode.Append : FileMode.Create,
                    FileAccess.Write, FileShare.Read, 1024 * 1024))
                {
                    byte[] buffer = new byte[256 * 1024];
                    long got = have;
                    int read;
                    long lastReport = 0;
                    while ((read = input.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        if (cancelled) throw new OperationCanceledException();
                        output.Write(buffer, 0, read);
                        got += read;
                        if (got - lastReport >= 1024 * 1024) { lastReport = got; progress(got); }
                    }
                    output.Flush();
                    progress(got);
                }
            }
            Finish(j, part, final);
        }

        void Finish(MapJob j, string part, string final)
        {
            long len = new FileInfo(part).Length;
            if (len != j.Size)
            {
                // Zu kurz: der nächste Versuch setzt fort. Zu lang oder kaputt: verwerfen.
                if (len > j.Size) File.Delete(part);
                throw new IOException(string.Format("Unvollständig ({0} von {1})", Human(len), Human(j.Size)));
            }
            if (File.Exists(final)) File.Delete(final);
            File.Move(part, final);
            if (j.Modified != DateTime.MinValue) File.SetLastWriteTimeUtc(final, j.Modified);
            Log("Fertig: " + j.FileName);
        }

        static HttpWebRequest NewRequest(string url, string method)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | (SecurityProtocolType)768 | (SecurityProtocolType)192;
            HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = method;
            req.UserAgent = "PoloMapsDownloader/1.0 (PoloLauncher)";
            req.AllowAutoRedirect = true;
            req.Timeout = 15000;
            req.AutomaticDecompression = DecompressionMethods.None;
            return req;
        }

        public static string Human(long bytes)
        {
            double v = bytes;
            string[] units = { "B", "KB", "MB", "GB", "TB" };
            int u = 0;
            while (v >= 1024 && u < units.Length - 1) { v /= 1024; u++; }
            return string.Format(System.Globalization.CultureInfo.GetCultureInfo("de-DE"), u == 0 ? "{0:0} {1}" : "{0:0.0} {1}", v, units[u]);
        }
    }
}
