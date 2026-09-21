package android.os;
import java.util.*;
public final class Handler {
    private record Task(Handler owner, Runnable runnable, long due, long order) {}
    private static final List<Task> tasks = new ArrayList<>();
    private static long sequence;
    public Handler(Looper looper) {}
    public boolean post(Runnable r) { return postDelayed(r, 0); }
    public boolean postDelayed(Runnable r, long delay) {
        synchronized(tasks) { tasks.add(new Task(this, r, SystemClock.now + delay, ++sequence)); }
        return true;
    }
    public void removeCallbacks(Runnable r) {
        synchronized(tasks) { tasks.removeIf(t -> t.owner == this && t.runnable == r); }
    }
    public void removeCallbacksAndMessages(Object token) {
        synchronized(tasks) { tasks.removeIf(t -> t.owner == this); }
    }
    public static void drain() {
        while(true) {
            Task next;
            synchronized(tasks) {
                next = tasks.stream().filter(t -> t.due <= SystemClock.now)
                    .min(Comparator.comparingLong(Task::due).thenComparingLong(Task::order)).orElse(null);
                if(next == null) return;
                tasks.remove(next);
            }
            next.runnable.run();
        }
    }
}
