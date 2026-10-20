// PoloLauncher Karten-Downloader: Oberfläche (Windows Forms).
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Windows.Forms;

namespace PoloMaps
{
    public sealed class MainForm : Form
    {
        readonly TextBox folderBox = new TextBox();
        readonly CheckedListBox regionList = new CheckedListBox();
        readonly CheckBox mapsBox = new CheckBox();
        readonly CheckBox trafficBox = new CheckBox();
        readonly Button startButton = new Button();
        readonly Button cancelButton = new Button();
        readonly ProgressBar bar = new ProgressBar();
        readonly Label status = new Label();
        readonly TextBox logBox = new TextBox();
        MapDownloader downloader;
        Thread worker;
        DateTime startedAt;

        public static string FindStickFolder()
        {
            try
            {
                foreach (DriveInfo d in DriveInfo.GetDrives())
                    if (d.DriveType == DriveType.Removable && d.IsReady)
                        return Path.Combine(d.RootDirectory.FullName, MapDownloader.FolderName);
            }
            catch (Exception) { }
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), MapDownloader.FolderName);
        }

        public MainForm()
        {
            Text = "PoloLauncher Karten-Downloader";
            ClientSize = new Size(620, 640);
            MinimumSize = new Size(560, 600);
            Font = new Font("Segoe UI", 9.5f);
            StartPosition = FormStartPosition.CenterScreen;

            Label hint = new Label();
            hint.Text = "Ein Klick: lädt alle Karten und Tempolimit-Daten auf den USB-Stick.\r\nDanach den Stick ins Radio stecken und im Launcher \"Von USB-Stick importieren\" wählen.";
            hint.SetBounds(12, 10, 596, 40);

            Label folderLabel = new Label();
            folderLabel.Text = "Zielordner (am besten Ordner \"PoloMaps\" auf dem Stick):";
            folderLabel.SetBounds(12, 56, 596, 20);
            folderBox.SetBounds(12, 78, 500, 26);
            folderBox.Text = FindStickFolder();
            folderBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            Button browse = new Button();
            browse.Text = "Ändern…";
            browse.SetBounds(520, 76, 88, 30);
            browse.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            browse.Click += delegate
            {
                using (FolderBrowserDialog dlg = new FolderBrowserDialog())
                {
                    dlg.Description = "Ordner für die Karten wählen (z. B. PoloMaps auf dem USB-Stick)";
                    if (dlg.ShowDialog(this) == DialogResult.OK)
                    {
                        string p = dlg.SelectedPath;
                        folderBox.Text = p.EndsWith(MapDownloader.FolderName, StringComparison.OrdinalIgnoreCase)
                            ? p : Path.Combine(p, MapDownloader.FolderName);
                    }
                }
            };

            mapsBox.Text = "Karten";
            mapsBox.Checked = true;
            mapsBox.SetBounds(12, 116, 90, 24);
            trafficBox.Text = "Tempolimits und Blitzer-Daten";
            trafficBox.Checked = true;
            trafficBox.SetBounds(110, 116, 260, 24);

            regionList.SetBounds(12, 146, 596, 190);
            regionList.CheckOnClick = true;
            regionList.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            for (int i = 0; i < MapDownloader.RegionNames.Length; i++)
                regionList.Items.Add(MapDownloader.RegionNames[i], true);
            regionList.MultiColumn = true;
            regionList.ColumnWidth = 290;

            startButton.Text = "Alles herunterladen";
            startButton.Font = new Font(Font.FontFamily, 11f, FontStyle.Bold);
            startButton.SetBounds(12, 346, 400, 46);
            startButton.Click += delegate { Start(); };
            cancelButton.Text = "Abbrechen";
            cancelButton.SetBounds(420, 346, 188, 46);
            cancelButton.Enabled = false;
            cancelButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            cancelButton.Click += delegate { if (downloader != null) downloader.Cancel(); };

            bar.SetBounds(12, 404, 596, 24);
            bar.Maximum = 1000;
            bar.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            status.SetBounds(12, 432, 596, 40);
            status.Text = "Bereit.";
            status.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

            logBox.SetBounds(12, 476, 596, 150);
            logBox.Multiline = true;
            logBox.ReadOnly = true;
            logBox.ScrollBars = ScrollBars.Vertical;
            logBox.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;

            Controls.AddRange(new Control[] { hint, folderLabel, folderBox, browse, mapsBox, trafficBox, regionList,
                startButton, cancelButton, bar, status, logBox });

            FormClosing += delegate (object s, FormClosingEventArgs e)
            {
                if (worker != null && worker.IsAlive)
                {
                    if (MessageBox.Show(this, "Der Download läuft noch. Wirklich beenden? (Er kann später fortgesetzt werden.)",
                        Text, MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) { e.Cancel = true; return; }
                    downloader.Cancel();
                }
            };
        }

        void AddLog(string line)
        {
            if (InvokeRequired) { BeginInvoke(new Action<string>(AddLog), line); return; }
            logBox.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + line + "\r\n");
        }

        void Start()
        {
            List<int> picked = new List<int>();
            for (int i = 0; i < regionList.Items.Count; i++) if (regionList.GetItemChecked(i)) picked.Add(i);
            if (picked.Count == 0 || (!mapsBox.Checked && !trafficBox.Checked))
            {
                MessageBox.Show(this, "Bitte mindestens ein Bundesland und eine Datenart wählen.", Text);
                return;
            }
            string dir = folderBox.Text.Trim();
            if (dir.Length == 0) return;
            SetRunning(true);
            logBox.Clear();
            startedAt = DateTime.Now;
            bool maps = mapsBox.Checked, traffic = trafficBox.Checked;
            downloader = new MapDownloader(MapDownloader.DefaultBaseUrl, dir);
            downloader.Log = AddLog;
            downloader.Progress = OnProgress;
            MapDownloader d = downloader;
            worker = new Thread(delegate ()
            {
                string result;
                try
                {
                    List<MapJob> jobs = d.BuildJobs(picked, maps, traffic);
                    SetStatus("Prüfe, was auf dem Server liegt…");
                    d.Probe(jobs);
                    int failed = d.Run(jobs);
                    int ok = 0;
                    foreach (MapJob j in jobs) if (j.Done) ok++;
                    if (failed == 0 && !d.IsCancelled)
                        result = "Fertig! " + ok + " Dateien liegen im Ordner:\r\n" + dir + "\r\nJetzt den Stick sicher entfernen und im Radio importieren.";
                    else if (d.IsCancelled)
                        result = "Abgebrochen. Beim nächsten Start wird fortgesetzt.";
                    else
                        result = failed + " Datei(en) konnten nicht geladen werden. Bitte nochmal starten – Fertiges wird übersprungen.";
                }
                catch (Exception e) { result = "Fehler: " + e.Message; }
                BeginInvoke(new Action(delegate
                {
                    SetRunning(false);
                    status.Text = result;
                    bar.Value = result.StartsWith("Fertig") ? bar.Maximum : bar.Value;
                    AddLog(result.Replace("\r\n", " "));
                    if (result.StartsWith("Fertig")) MessageBox.Show(this, result, Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
                }));
            });
            worker.IsBackground = true;
            worker.Start();
        }

        void SetStatus(string text)
        {
            if (InvokeRequired) { BeginInvoke(new Action<string>(SetStatus), text); return; }
            status.Text = text;
        }

        void SetRunning(bool running)
        {
            startButton.Enabled = !running;
            cancelButton.Enabled = running;
            regionList.Enabled = !running;
            folderBox.Enabled = !running;
            mapsBox.Enabled = !running;
            trafficBox.Enabled = !running;
        }

        long lastBytes;
        DateTime lastTick = DateTime.MinValue;
        double speed;

        void OnProgress(long done, long total, string what)
        {
            if (InvokeRequired) { BeginInvoke(new Action<long, long, string>(OnProgress), done, total, what); return; }
            DateTime now = DateTime.Now;
            if (lastTick == DateTime.MinValue || done < lastBytes) { lastTick = now; lastBytes = done; }
            else if ((now - lastTick).TotalSeconds >= 1.5)
            {
                double inst = (done - lastBytes) / (now - lastTick).TotalSeconds;
                speed = speed <= 0 ? inst : speed * 0.7 + inst * 0.3;
                lastTick = now; lastBytes = done;
            }
            bar.Value = total <= 0 ? 0 : (int)Math.Min(bar.Maximum, done * 1000L / total);
            string eta = "";
            if (speed > 1024 && total > done)
            {
                TimeSpan left = TimeSpan.FromSeconds((total - done) / speed);
                eta = string.Format(" – noch ca. {0}:{1:00} h", (int)left.TotalHours, left.Minutes);
            }
            status.Text = string.Format("{0}\r\n{1} von {2} ({3}/s){4}", what, MapDownloader.Human(done),
                MapDownloader.Human(total), MapDownloader.Human((long)speed), eta);
        }

        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
