package android.view;
public class View {
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    public boolean post(Runnable r){return handler.post(r);}
    public boolean postDelayed(Runnable r,long delay){return handler.postDelayed(r,delay);}
    public boolean removeCallbacks(Runnable r){handler.removeCallbacks(r);return true;}
    public static final int VISIBLE=0,GONE=8;
    private int width=1000,scroll;
    public int getWidth(){return width;}
    public int getScrollX(){return scroll;}
    public void scrollTo(int x,int y){scroll=x;}
    public void measure(int w,int h){}
    public void layout(int l,int t,int r,int b){width=r-l;}
    protected void setMeasuredDimension(int w,int h){width=w;}
    protected void onDetachedFromWindow(){}
    public static class MeasureSpec {
        public static final int EXACTLY=0x40000000;
        public static int getSize(int s){return s&0x3fffffff;}
        public static int makeMeasureSpec(int s,int mode){return s|mode;}
    }
}
