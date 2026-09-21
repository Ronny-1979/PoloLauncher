package android.view;
public class VelocityTracker {
    public static VelocityTracker obtain(){return new VelocityTracker();}
    public void addMovement(MotionEvent event){}public void computeCurrentVelocity(int units){}
    public float getXVelocity(){return 0;}public void recycle(){}
}
