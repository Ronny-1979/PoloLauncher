package de.ronny.pololauncher;
import android.content.Context;
import android.view.*;
public final class PagerGestureTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static MotionEvent event(int action,float x,float y){return new MotionEvent(action,x,y);}
    static PagedLauncherLayout pager() {
        PagedLauncherLayout result=new PagedLauncherLayout(new Context());
        result.addView(new View());result.addView(new View());return result;
    }
    public static void main(String[] args)throws Exception {
        PagedLauncherLayout pager=pager();boolean[] following={true}, saved={true};
        pager.setPageGestureListener(new PagedLauncherLayout.PageGestureListener(){
            public void onGestureStart(){saved[0]=following[0];}
            public void onPageSwipe(){following[0]=saved[0];}
        });
        check(!pager.onInterceptTouchEvent(event(0,100,100)),"Down reaches map");
        check(!pager.onInterceptTouchEvent(event(2,110,120)),"Initial diagonal move reaches map");
        following[0]=false; // Map briefly interprets this movement as a pan.
        check(pager.onInterceptTouchEvent(event(2,200,120)),"Horizontal continuation becomes page swipe");
        check(following[0],"Page swipe restores previous GPS following");
        following[0]=false;pager.onInterceptTouchEvent(event(0,100,100));
        pager.onInterceptTouchEvent(event(2,200,100));
        check(!following[0],"Deliberately paused following is not changed");
        pager.onInterceptTouchEvent(event(0,100,100));pager.onInterceptTouchEvent(event(5,100,100));
        check(!pager.onInterceptTouchEvent(event(2,300,100)),"Pinch does not become page swipe");
        PagedLauncherLayout animated=pager();animated.setCurrentPage(1,true);animated.scrollTo(123,0);
        var layout=PagedLauncherLayout.class.getDeclaredMethod("onLayout",boolean.class,int.class,int.class,int.class,int.class);
        layout.setAccessible(true);layout.invoke(animated,false,0,0,1000,600);
        check(animated.getScrollX()==123,"Layout must not snap a running page animation");
        System.out.println("PagerGestureTest: all cases passed");
    }
}
