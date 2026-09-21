package de.ronny.pololauncher;

import android.app.*;
import android.content.Context;
import android.os.*;
import org.mapsforge.map.reader.MapFile;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

public final class OfflineMapAsyncTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void await(CountDownLatch latch){RoadPositionMatcherTest.await(latch);}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("polo-map-async-");
        try {
            Context app=new Context();app.externalFilesDir=root.toFile();
            DownloadManager manager=new DownloadManager();app.services.put(Context.DOWNLOAD_SERVICE,manager);
            Activity download=new Activity(),main=new Activity();download.app=app;main.app=app;
            Files.createDirectories(root.resolve("offline_maps"));
            try(RandomAccessFile f=new RandomAccessFile(root.resolve("offline_maps/sachsen.download").toFile(),"rw")){f.setLength(1_000_000);}
            app.getSharedPreferences("offline_maps_v1",0).edit().putLong("download_id",1)
                .putString("downloading","sachsen").putBoolean("activate_after_download",true).apply();
            CountDownLatch validating=new CountDownLatch(1),release=new CountDownLatch(1);
            MapFile.openHook=()->{validating.countDown();await(release);};
            int[] calls={0,0};
            OfflineMapStore.finishIfCompleteAsync(download,(o,m)->calls[0]++);await(validating);
            download.finishing=true;
            OfflineMapStore.finishIfCompleteAsync(main,(o,m)->calls[1]++);
            // Repeated resume/poll joins once; closing the first owner loses no notification.
            OfflineMapStore.finishIfCompleteAsync(main,(o,m)->calls[1]++);
            release.countDown();RoadPositionMatcherTest.pumpUntil(()->calls[1]==1);
            check(calls[0]==0 && calls[1]==1,"New main Activity gets completion once after old owner closes");
            check(OfflineMapStore.installed(app,"sachsen") && OfflineMapStore.selected(app).equals("sachsen"),"Validated map really activated");
            MapFile.openHook=null;

            // Block the actual DownloadManager query made by the UI snapshot.
            app.getSharedPreferences("offline_maps_v1",0).edit().putLong("download_id",2).putString("downloading","bayern").apply();
            CountDownLatch querying=new CountDownLatch(1),queryRelease=new CountDownLatch(1);
            Thread uiThread=Thread.currentThread();boolean[] offMain={false};
            manager.queryHook=()->{offMain[0]=Thread.currentThread()!=uiThread;querying.countDown();await(queryRelease);};
            OfflineMapStore.UiState[] snapshot={null};
            OfflineMapStore.loadUiStateAsync(main,(o,s,e)->{
                check(Thread.currentThread()==uiThread,"UI result dispatched on main thread");
                check(e==null,"Snapshot succeeds");snapshot[0]=s;
            });
            await(querying);check(offMain[0],"Blocking query runs away from UI thread");
            boolean[] responsive={false};new Handler(Looper.getMainLooper()).post(()->responsive[0]=true);Handler.drain();
            check(responsive[0] && snapshot[0]==null,"UI processes events while storage query is stalled");
            queryRelease.countDown();RoadPositionMatcherTest.pumpUntil(()->snapshot[0]!=null);
            check(snapshot[0].installed[OfflineMapStore.indexOf("sachsen")] && snapshot[0].used==1_000_000,"Snapshot includes installed maps and sizes");
            manager.queryHook=null;
            boolean[] finished={false};
            OfflineMapStore.runActionAsync(main,c->{check(Thread.currentThread()!=uiThread,"Mutations stay off UI thread");return OfflineMapStore.cancelActive(c);},
                (o,m)->{check(Thread.currentThread()==uiThread,"Action result on main thread");finished[0]=true;});
            RoadPositionMatcherTest.pumpUntil(()->finished[0]);
            check(OfflineMapStore.activeId(app)==-1,"Async cancellation completes");
            System.out.println("OfflineMapAsyncTest: owner handoff, blocked UI query and asynchronous mutation passed");
        } finally {
            MapFile.openHook=null;
            try(var files=Files.walk(root)){for(Path p:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
