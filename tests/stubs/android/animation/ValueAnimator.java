package android.animation;
public class ValueAnimator {
    public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator animator);}
    private int value;private boolean running;
    public static ValueAnimator ofInt(int... values){ValueAnimator a=new ValueAnimator();a.value=values[0];return a;}
    public ValueAnimator setDuration(long duration){return this;}
    public void setInterpolator(Object interpolator){}
    public void addUpdateListener(AnimatorUpdateListener listener){}
    public Object getAnimatedValue(){return value;}
    public void start(){running=true;}public void cancel(){running=false;}public boolean isRunning(){return running;}
}
