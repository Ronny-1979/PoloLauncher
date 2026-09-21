package de.ronny.pololauncher;
public final class CompletionListenersTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final class Screen { boolean closed; int calls; }
    public static void main(String[] args) {
        CompletionListeners<Screen> listeners = new CompletionListeners<>();
        Screen download = new Screen(), main = new Screen();
        check(listeners.join(1, download, (s,m)->{if(!s.closed)s.calls++;}), "First caller starts work");
        download.closed = true;
        check(!listeners.join(1, main, (s,m)->s.calls++), "Main screen joins existing work");
        check(!listeners.join(1, main, (s,m)->s.calls++), "Repeated polling does not duplicate observers");
        listeners.finish(1, "ready");
        check(download.calls == 0 && main.calls == 1, "Closed download page does not lose main-screen notification");
        check(listeners.join(1, main, (s,m)->s.calls++), "Next poll can start after nonterminal completion");
        listeners.finish(1, null); check(main.calls == 1, "Pending download creates no false ready notification");
        check(listeners.join(1, main, (s,m)->s.calls++), "Cancelled/rejected work can be retried");
        listeners.finish(1, "done"); check(main.calls == 2, "Retry succeeds");
        System.out.println("CompletionListenersTest: all cases passed");
    }
}
