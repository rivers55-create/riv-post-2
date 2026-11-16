package org.telegram.messenger;

/**
 * Riv Post: one place that follows music playback, called by MediaController each time a song
 * starts (see apply.sh). It handles:
 *  - play counts for the "Most played" tab (every song that starts, not only ones tapped);
 *  - fading between songs (RivPrefs.CROSSFADE_SEC): the end of a song fades out and the start
 *    of the next fades in. Telegram plays one song at a time, so this is a fade-out/fade-in,
 *    not two songs overlapping;
 *  - the sleep timer's "after N songs" mode: when the last allowed song ends, music pauses
 *    (with a gentle fade over its last 10 seconds when "Fade out" is on).
 */
public final class RivPlayback {

    private static int songsLeft;          // 0 = off; 1 = stop when this song ends
    private static Runnable ticker;
    private static float lastVolume = -1f;

    private RivPlayback() {
    }

    /** Called from MediaController right after a music track starts playing. */
    public static void onSongStart(MessageObject m) {
        if (m == null || !m.isMusic()) return;
        try {
            RivMusicStats.recordPlay(org.telegram.ui.RivMusicActivity.keyOf(m));
        } catch (Throwable ignored) {
        }
        if (songsLeft > 0) {
            songsLeft--;
            if (songsLeft == 0) {
                // the allowed songs are done: stop at the very start of the next one
                MediaController.getInstance().pauseMessage(m);
                setVolume(1f);
                return;
            }
        }
        lastVolume = -1f; // Telegram resets the volume for each new song
        setVolume(volumeFor(m));
        startTicker();
    }

    /** Sleep timer: stop after this many songs (1 = at the end of the current song). 0 cancels. */
    public static void stopAfterSongs(int n) {
        RivSleepTimer.cancel();
        songsLeft = Math.max(0, n);
        if (songsLeft > 0) startTicker();
        else setVolume(1f);
    }

    public static int songsLeft() {
        return songsLeft;
    }

    private static void startTicker() {
        if (ticker != null) return;
        ticker = new Runnable() {
            @Override
            public void run() {
                MessageObject m = MediaController.getInstance().getPlayingMessageObject();
                if (m == null || !m.isMusic()) {
                    ticker = null;
                    setVolume(1f);
                    return;
                }
                if (!RivSleepTimer.isFading()) { // the timed sleep fade controls the volume itself
                    setVolume(volumeFor(m));
                }
                AndroidUtilities.runOnUIThread(this, 200);
            }
        };
        AndroidUtilities.runOnUIThread(ticker, 200);
    }

    private static float volumeFor(MessageObject m) {
        double dur = m.getDuration();
        if (dur <= 0) return 1f;
        float pos = (float) (m.audioProgress * dur);
        float left = (float) (dur - pos);
        float v = 1f;
        int fade = RivPrefs.i(RivPrefs.CROSSFADE_SEC, 0);
        if (fade > 0 && dur > fade * 3) {
            v = Math.min(v, Math.min(pos / fade, left / fade));
        }
        if (songsLeft == 1 && RivPrefs.b(RivPrefs.SLEEP_FADE, true)) {
            v = Math.min(v, left / 10f);
        }
        return Math.max(0f, Math.min(1f, v));
    }

    private static void setVolume(float v) {
        if (Math.abs(v - lastVolume) < 0.01f) return;
        lastVolume = v;
        MediaController.getInstance().rivSetVolume(v);
    }
}
