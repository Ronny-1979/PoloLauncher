package de.ronny.pololauncher;
import android.location.Location;
import android.os.SystemClock;
public final class GpsFollowFilterTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Location fix(double latitude,float accuracy){Location p=new Location("gps");p.setLatitude(latitude);p.setLongitude(13);p.setAccuracy(accuracy);p.setSpeed(0);return p;}
    public static void main(String[] args){
        GpsFollowFilter filter=new GpsFollowFilter();
        check(filter.update(fix(52,150))==null,"Bad first fix rejected");
        check(filter.update(fix(52,Float.NaN))==null,"NaN first accuracy rejected");
        check(filter.update(fix(Double.NaN,5))==null,"NaN coordinates rejected");
        check(filter.update(fix(91,5))==null,"Out of range coordinates rejected");
        Location valid=filter.update(fix(51,5));check(valid.getLatitude()==51,"First good fix accepted");
        SystemClock.now+=1000;
        check(filter.update(fix(51.00001,5)).getLatitude()==51,"Stationary jitter suppressed");
        SystemClock.now+=6000;
        check(filter.update(fix(52,150)).getLatitude()==51,"Bad fix after gap held");
        SystemClock.now+=1000;
        check(filter.update(fix(51.001,5)).getLatitude()==51.001,"Good fix reacquired after gap");
        System.out.println("GpsFollowFilterTest: all cases passed");
    }
}
