package android.view;
import android.content.Context;
import android.util.AttributeSet;
import java.util.*;
public class ViewGroup extends View {
    private final List<View> children=new ArrayList<>();
    public ViewGroup(Context context,AttributeSet attrs){}
    public int getChildCount(){return children.size();}
    public View getChildAt(int i){return children.get(i);}
    public void addView(View v){children.add(v);}
    public void setClipToPadding(boolean value){}
    protected void onMeasure(int w,int h){}
    protected void onLayout(boolean changed,int l,int t,int r,int b){}
    public boolean onInterceptTouchEvent(MotionEvent event){return false;}
    public boolean onTouchEvent(MotionEvent event){return false;}
    public void requestDisallowInterceptTouchEvent(boolean value){}
}
