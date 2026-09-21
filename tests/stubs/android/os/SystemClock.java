package android.os;
public class SystemClock {public static long now=1000;public static long elapsedRealtime(){return now;}public static long elapsedRealtimeNanos(){return now*1000000L;}public static long uptimeMillis(){return now;}}
