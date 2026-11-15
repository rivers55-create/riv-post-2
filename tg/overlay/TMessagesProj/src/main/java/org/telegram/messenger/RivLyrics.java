package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Riv Post: synced lyrics for the song playing, looked up on LRCLIB (free, no key needed) by
 * artist, title and length, and kept on the phone afterwards so each song is looked up once.
 */
public class RivLyrics {

    public static class Line {
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public static class Result {
        public final ArrayList<Line> lines = new ArrayList<>();
        public boolean synced;
        public String plain;
    }

    public interface Callback {
        void onResult(Result result);
    }

    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]");

    private static SharedPreferences cache() {
        return ApplicationLoader.applicationContext.getSharedPreferences("rivlyrics", Context.MODE_PRIVATE);
    }

    private static String cacheKey(String artist, String title) {
        return (artist == null ? "" : artist.trim().toLowerCase()) + "|" + (title == null ? "" : title.trim().toLowerCase());
    }

    public static void load(MessageObject m, Callback callback) {
        if (m == null) {
            callback.onResult(null);
            return;
        }
        final String artist = m.getMusicAuthor();
        final String title = m.getMusicTitle();
        final int duration = (int) m.getDuration();
        final String key = cacheKey(artist, title);
        String cached = cache().getString(key, null);
        if (cached != null) {
            callback.onResult(cached.isEmpty() ? null : parse(cached));
            return;
        }
        if (!RivPrefs.b(RivPrefs.LYRICS_ONLINE, true) || title == null || title.isEmpty()) {
            callback.onResult(null);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String text = fetch(artist, title, duration);
            if (text != null) {
                cache().edit().putString(key, text).apply();
            }
            Result r = text == null || text.isEmpty() ? null : parse(text);
            AndroidUtilities.runOnUIThread(() -> callback.onResult(r));
        });
    }

    /** Returns the lyrics text ("" when the song has none, null when the lookup failed). */
    private static String fetch(String artist, String title, int duration) {
        HttpURLConnection c = null;
        try {
            Uri.Builder b = Uri.parse("https://lrclib.net/api/get").buildUpon()
                    .appendQueryParameter("track_name", title);
            if (artist != null && !artist.isEmpty()) b.appendQueryParameter("artist_name", artist);
            if (duration > 0) b.appendQueryParameter("duration", String.valueOf(duration));
            c = (HttpURLConnection) new URL(b.build().toString()).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "RivPost (private app)");
            int code = c.getResponseCode();
            if (code == 404) return "";
            if (code != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            JSONObject o = new JSONObject(sb.toString());
            String synced = o.optString("syncedLyrics", "");
            if (synced != null && !synced.isEmpty() && !"null".equals(synced)) return synced;
            String plain = o.optString("plainLyrics", "");
            return plain == null || "null".equals(plain) ? "" : plain;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static Result parse(String text) {
        Result r = new Result();
        for (String raw : text.split("\n")) {
            Matcher mt = TAG.matcher(raw);
            int end = 0;
            ArrayList<Long> times = new ArrayList<>();
            while (mt.find() && mt.start() == end) {
                long min = Long.parseLong(mt.group(1));
                long sec = Long.parseLong(mt.group(2));
                long frac = 0;
                if (mt.group(3) != null) {
                    String f = mt.group(3);
                    frac = Long.parseLong(f);
                    if (f.length() == 1) frac *= 100;
                    else if (f.length() == 2) frac *= 10;
                }
                times.add(min * 60000 + sec * 1000 + frac);
                end = mt.end();
            }
            String line = raw.substring(end).trim();
            for (Long t : times) r.lines.add(new Line(t, line));
        }
        if (!r.lines.isEmpty()) {
            java.util.Collections.sort(r.lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
            r.synced = true;
        } else {
            r.plain = text.trim();
        }
        return r;
    }
}
