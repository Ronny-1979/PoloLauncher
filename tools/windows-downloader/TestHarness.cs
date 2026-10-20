using System;
using System.Collections.Generic;
using System.IO;
using PoloMaps;
class T {
  static int Main(string[] a) {
    string dir = a[1];
    MapDownloader d = new MapDownloader(a[0], dir);
    d.Log = Console.WriteLine;
    List<MapJob> jobs = d.BuildJobs(new int[]{3,7}, true, true);
    d.Probe(jobs);
    foreach (MapJob j in jobs) Console.WriteLine("probe " + j.FileName + " " + j.Size + " " + j.Modified.ToString("o") + " ok=" + j.ProbeOk + " complete=" + j.AlreadyComplete);
    int failed = d.Run(jobs);
    Console.WriteLine("failed=" + failed);
    return failed;
  }
}
