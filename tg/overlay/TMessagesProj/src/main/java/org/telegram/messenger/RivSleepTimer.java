package org.telegram.messenger;

/**
 * Riv Post: music sleep timer. With "Fade out" on (default), the volume goes down gently over
 * the last 30 seconds before the music pauses, then goes back to normal for next time.
 */
public class RivSleepTimer {

    private static final long FADE_MS = 30_000;

    private static Runnable pending;
    private static Runnable fadeStep;
    private static long endAtMs;

    public static void schedule(long atMs) {
        cancel();
        if (RivPlayback.songsLeft() > 0) RivPlayback.stopAfterSongs(0);
        endAtMs = atMs;
        boolean fade = RivPrefs.b(RivPrefs.SLEEP_FADE, true);
        long delay = Math.max(0, atMs - System.currentTimeMillis() - (fade ? FADE_MS : 0));
        pending = () -> {
            if (fade) {
                startFade();
            } else {
                stopNow();
            }
        };
        AndroidUtilities.runOnUIThread(pending, delay);
    }

    private static void startFade() {
        fadeStep = new Runnable() {
            @Override
            public void run() {
                long left = endAtMs - System.currentTimeMillis();
                if (left <= 0) {
                    stopNow();
                    return;
                }
                float v = Math.max(0f, Math.min(1f, left / (float) FADE_MS));
                MediaController.getInstance().rivSetVolume(v);
                AndroidUtilities.runOnUIThread(this, 500);
            }
        };
        fadeStep.run();
    }

    private static void stopNow() {
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        if (m != null) {
            MediaController.getInstance().pauseMessage(m);
        }
        MediaController.getInstance().rivSetVolume(1f);
        pending = null;
        fadeStep = null;
        endAtMs = 0;
    }

    public static void cancel() {
        if (pending != null) {
            AndroidUtilities.cancelRunOnUIThread(pending);
            pending = null;
        }
        if (fadeStep != null) {
            AndroidUtilities.cancelRunOnUIThread(fadeStep);
            fadeStep = null;
            MediaController.getInstance().rivSetVolume(1f);
        }
        endAtMs = 0;
    }

    public static long endAtMs() {
        return endAtMs;
    }

    public static boolean isFading() {
        return fadeStep != null;
    }

    public static boolean isActive() {
        return (pending != null || fadeStep != null) && endAtMs > System.currentTimeMillis();
    }
}
