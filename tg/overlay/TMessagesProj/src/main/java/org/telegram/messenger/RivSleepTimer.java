package org.telegram.messenger;

/** Riv Post: a simple music sleep timer. Pauses playback once the delay elapses. */
public class RivSleepTimer {

    private static Runnable pending;
    private static long endAtMs;

    public static void schedule(long atMs) {
        cancel();
        endAtMs = atMs;
        long delay = Math.max(0, atMs - System.currentTimeMillis());
        pending = () -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m != null) {
                MediaController.getInstance().pauseMessage(m);
            }
            pending = null;
            endAtMs = 0;
        };
        AndroidUtilities.runOnUIThread(pending, delay);
    }

    public static void cancel() {
        if (pending != null) {
            AndroidUtilities.cancelRunOnUIThread(pending);
            pending = null;
        }
        endAtMs = 0;
    }

    public static long endAtMs() {
        return endAtMs;
    }

    public static boolean isActive() {
        return pending != null && endAtMs > System.currentTimeMillis();
    }
}
