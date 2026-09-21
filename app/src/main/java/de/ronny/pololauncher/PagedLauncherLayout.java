package de.ronny.pololauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Small dependency-free horizontal pager tailored for the car launcher. */
public final class PagedLauncherLayout extends ViewGroup {
    public interface PageListener { void onPageChanged(int page); }
    public interface PageGestureListener {
        void onGestureStart();
        void onPageSwipe();
    }

    private final int touchSlop;
    private final int minimumFling;
    private float downX;
    private float downY;
    private int downScrollX;
    private boolean dragging;
    private boolean swipeAllowed;
    private int currentPage;
    private VelocityTracker velocity;
    private ValueAnimator animator;
    private PageListener listener;
    private PageGestureListener gestureListener;

    public PagedLauncherLayout(Context context) { this(context, null); }

    public PagedLauncherLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minimumFling = vc.getScaledMinimumFlingVelocity() * 3;
        setClipToPadding(false);
    }

    public void setPageListener(PageListener value) { listener = value; }
    public void setPageGestureListener(PageGestureListener value) { gestureListener = value; }
    public int getCurrentPage() { return currentPage; }

    public void setCurrentPage(int page, boolean animated) {
        int target = Math.max(0, Math.min(Math.max(0, getChildCount() - 1), page));
        if (animated && getWidth() > 0) {
            settleTo(target);
            return;
        }
        if (animator != null) animator.cancel();
        currentPage = target;
        scrollTo(currentPage * Math.max(0, getWidth()), 0);
        if (listener != null) listener.onPageChanged(currentPage);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(childWidth, childHeight);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * width, 0, (i + 1) * width, height);
        }
        if (!dragging && (animator == null || !animator.isRunning())) scrollTo(currentPage * width, 0);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginGesture(event);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) velocity.addMovement(event);
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (swipeAllowed && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    dragging = true;
                    if (gestureListener != null) gestureListener.onPageSwipe();
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A two-finger gesture belongs to the map (zoom/tilt), never to pages.
                swipeAllowed = false;
                dragging = false;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                recycleVelocity();
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginGesture(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (!swipeAllowed || Math.abs(dx) <= touchSlop || Math.abs(dx) <= Math.abs(dy) * 1.15f) return true;
                    dragging = true;
                    if (gestureListener != null) gestureListener.onPageSwipe();
                }
                int wanted = downScrollX + Math.round(downX - event.getX());
                int max = Math.max(0, (getChildCount() - 1) * getWidth());
                scrollTo(Math.max(0, Math.min(max, wanted)), 0);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                swipeAllowed = false;
                settleTo(currentPage);
                dragging = false;
                return false;
            case MotionEvent.ACTION_UP:
                velocity.computeCurrentVelocity(1000);
                float vx = velocity.getXVelocity();
                float totalDx = event.getX() - downX;
                int next = currentPage;
                if (dragging && (Math.abs(totalDx) > getWidth() * 0.16f || Math.abs(vx) > minimumFling)) {
                    next += totalDx < 0 ? 1 : -1;
                } else if (dragging) {
                    next = Math.round(getScrollX() / (float)Math.max(1, getWidth()));
                }
                settleTo(next);
                dragging = false;
                recycleVelocity();
                return true;
            case MotionEvent.ACTION_CANCEL:
                settleTo(currentPage);
                dragging = false;
                recycleVelocity();
                return true;
        }
        return true;
    }

    @Override public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // A clearly horizontal drag always belongs to the launcher pager. A tap,
        // vertical gesture or pinch still reaches the map normally.
        if (disallowIntercept && swipeAllowed) return;
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    private void beginGesture(MotionEvent event) {
        if (animator != null) animator.cancel();
        downX = event.getX();
        downY = event.getY();
        downScrollX = getScrollX();
        dragging = false;
        swipeAllowed = getChildCount() > 1;
        if (gestureListener != null) gestureListener.onGestureStart();
        recycleVelocity();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(event);
    }

    private void settleTo(int requested) {
        int targetPage = Math.max(0, Math.min(Math.max(0, getChildCount() - 1), requested));
        int start = getScrollX();
        int end = targetPage * getWidth();
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(260L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> scrollTo((Integer)a.getAnimatedValue(), 0));
        animator.start();
        if (targetPage != currentPage) {
            currentPage = targetPage;
            if (listener != null) listener.onPageChanged(currentPage);
        }
    }

    private void recycleVelocity() {
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        recycleVelocity();
        super.onDetachedFromWindow();
    }

}
