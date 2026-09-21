package de.ronny.pololauncher;

import android.location.Location;
import android.os.*;
import android.view.View;
import org.mapsforge.core.model.*;
import org.mapsforge.map.datastore.*;
import org.mapsforge.map.reader.MapFile;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class RoadPositionMatcherTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void await(CountDownLatch latch) {
        try {check(latch.await(3,TimeUnit.SECONDS),"Reader latch timed out");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
    }
    static void pumpUntil(BooleanSupplier done) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!done.getAsBoolean() && System.nanoTime()<until){Handler.drain();Thread.sleep(1);}
        Handler.drain();check(done.getAsBoolean(),"Async map callback timed out");
    }
    static Location fix(long time,double longitude){
        Location l=new Location("gps");l.setLatitude(51.00005);l.setLongitude(longitude);
        l.setAccuracy(10);l.setElapsedRealtimeNanos(time*1_000_000L);return l;
    }
    public static void main(String[] args)throws Exception {
        MapReadResult data=new MapReadResult();Way way=new Way();
        way.tags=List.of(new Tag("highway","residential"));
        way.latLongs=new LatLong[][]{{new LatLong(51,12),new LatLong(51,13)}};
        data.ways.add(way);MapFile.result=data;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        MapFile.readHook=()->{entered.countDown();await(release);};
        List<Location> delivered=new ArrayList<>();List<Boolean> snapped=new ArrayList<>();
        RoadPositionMatcher.Listener listener=(source,display,snap)->{delivered.add(display);snapped.add(snap);};
        RoadPositionMatcher matcher=new RoadPositionMatcher(new File("fake.map"),new View(),listener);
        matcher.accept(fix(1000,12.1));await(entered);
        // Frequent fixes must not push the original fallback deadline back.
        SystemClock.now+=100;matcher.accept(fix(1100,12.1001));
        SystemClock.now+=100;matcher.accept(fix(1200,12.1002));
        SystemClock.now+=150;Handler.drain();
        check(delivered.size()==1 && delivered.get(0).getLongitude()==12.1002 && !snapped.get(0),
                "Hung reader yields latest raw GPS at the bounded deadline");
        matcher.accept(fix(1350,12.1003));SystemClock.now+=350;Handler.drain();
        check(delivered.size()==2 && delivered.get(1).getLongitude()==12.1003,"GPS continues while reader remains blocked");
        MapFile.readHook=null;release.countDown();
        var busy=RoadPositionMatcher.class.getDeclaredField("inFlight");busy.setAccessible(true);
        pumpUntil(()->{try{return !busy.getBoolean(matcher);}catch(Exception e){throw new RuntimeException(e);}});
        check(delivered.size()==2,"Late matched results do not move the camera back or deliver twice");
        matcher.accept(fix(1700,12.1004));pumpUntil(()->delivered.size()==3);
        check(snapped.get(2) && delivered.get(2).getLatitude()==51.0,"Fast subsequent matching recovers");
        SystemClock.now+=350;Handler.drain();check(delivered.size()==3,"Fast match cancels its raw timer");
        matcher.accept(fix(2050,12.1005));matcher.close();
        SystemClock.now+=350;Handler.drain();check(delivered.size()==3,"Close cancels pending positions");
        System.out.println("RoadPositionMatcherTest: stalled reader, deadline, latest fix, recovery and close passed");
    }
}
