package android.location;

public class Location {
    private String provider;
    private double latitude, longitude;
    private long time, elapsed;
    private Float accuracy, speed, bearing, bearingAccuracy, speedAccuracy;
    public Location(String provider) { this.provider = provider; }
    public Location(Location o) { provider=o.provider; latitude=o.latitude; longitude=o.longitude; time=o.time; elapsed=o.elapsed; accuracy=o.accuracy; speed=o.speed; bearing=o.bearing; bearingAccuracy=o.bearingAccuracy; speedAccuracy=o.speedAccuracy; }
    public String getProvider(){return provider;} public double getLatitude(){return latitude;} public void setLatitude(double v){latitude=v;}
    public double getLongitude(){return longitude;} public void setLongitude(double v){longitude=v;}
    public long getTime(){return time;} public void setTime(long v){time=v;} public long getElapsedRealtimeNanos(){return elapsed;} public void setElapsedRealtimeNanos(long v){elapsed=v;}
    public boolean hasAccuracy(){return accuracy!=null;} public float getAccuracy(){return accuracy;} public void setAccuracy(float v){accuracy=v;}
    public boolean hasSpeed(){return speed!=null;} public float getSpeed(){return speed;} public void setSpeed(float v){speed=v;}
    public boolean hasBearing(){return bearing!=null;} public float getBearing(){return bearing;} public void setBearing(float v){bearing=v;}
    public boolean hasBearingAccuracy(){return bearingAccuracy!=null;} public float getBearingAccuracyDegrees(){return bearingAccuracy;} public void setBearingAccuracyDegrees(float v){bearingAccuracy=v;}
    public boolean hasSpeedAccuracy(){return speedAccuracy!=null;} public float getSpeedAccuracyMetersPerSecond(){return speedAccuracy;} public void setSpeedAccuracyMetersPerSecond(float v){speedAccuracy=v;}
    public float distanceTo(Location o){float[] x=new float[1];distanceBetween(latitude,longitude,o.latitude,o.longitude,x);return x[0];}
    public float bearingTo(Location o){double a=Math.toRadians(latitude),b=Math.toRadians(o.latitude),dl=Math.toRadians(o.longitude-longitude);return (float)((Math.toDegrees(Math.atan2(Math.sin(dl)*Math.cos(b),Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(dl)))+360)%360);}
    public static void distanceBetween(double a,double b,double c,double d,float[] out){double x=Math.toRadians(d-b)*Math.cos(Math.toRadians((a+c)/2)),y=Math.toRadians(c-a);out[0]=(float)(Math.sqrt(x*x+y*y)*6371000);}
}
