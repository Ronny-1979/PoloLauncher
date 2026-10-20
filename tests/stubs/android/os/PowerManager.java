package android.os;
public class PowerManager {
 public static final int PARTIAL_WAKE_LOCK=1;
 public WakeLock newWakeLock(int level,String tag){return new WakeLock();}
 public static class WakeLock {
   public void setReferenceCounted(boolean b){}
   public void acquire(long timeout){}
   public boolean isHeld(){return true;}
   public void release(){}
 }
}
