package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Riv Post: tracks how often and how recently each song was played, so the music home
 * screen can show "Jump back in" (most recent) and "Most played" shelves. Nothing here
 * changes playback; it only records a timestamp/counter each time a song starts.
 */
public class RivMusicStats {

    private static SharedPreferences prefs;

    private static SharedPreferences prefs() {
        if (prefs == null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("riv_music_stats", Context.MODE_PRIVATE);
        }
        return prefs;
    }

    public static void recordPlay(String key) {
        try {
            SharedPreferences.Editor e = prefs().edit();
            e.putLong("last_" + key, System.currentTimeMillis());
            e.putInt("count_" + key, prefs().getInt("count_" + key, 0) + 1);
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    public static long lastPlayed(String key) {
        try {
            return prefs().getLong("last_" + key, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int playCount(String key) {
        try {
            return prefs().getInt("count_" + key, 0);
        } catch (Throwable t) {
            return 0;
        }
    }
}
