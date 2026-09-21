package de.ronny.pololauncher;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Coalesces work, but keeps every interested owner. Never holds a lock during I/O. */
final class CompletionListeners<O> {
    private final Map<Long, List<Target<O>>> pending = new HashMap<>();

    synchronized boolean join(long id, O owner, BiConsumer<O, String> callback) {
        List<Target<O>> targets = pending.get(id);
        boolean first = targets == null;
        if (first) { targets = new ArrayList<>(); pending.put(id, targets); }
        targets.removeIf(t -> t.owner.get() == null || t.owner.get() == owner);
        if (callback != null) targets.add(new Target<>(owner, callback));
        return first;
    }

    void finish(long id, String message) {
        List<Target<O>> targets;
        synchronized (this) { targets = pending.remove(id); }
        if (targets == null || message == null) return;
        for (Target<O> target : targets) {
            O owner = target.owner.get();
            if (owner != null) target.callback.accept(owner, message);
        }
    }

    private static final class Target<O> {
        final WeakReference<O> owner;
        final BiConsumer<O, String> callback;
        Target(O owner, BiConsumer<O, String> callback) {
            this.owner = new WeakReference<>(owner);
            this.callback = callback;
        }
    }
}
