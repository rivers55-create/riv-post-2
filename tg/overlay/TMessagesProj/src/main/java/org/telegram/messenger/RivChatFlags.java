package org.telegram.messenger;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Riv Post: small per-chat switches that several features share (block screenshots, hide link
 * previews, local pin, auto-clean time, text size). Reads are cached because the chat list asks
 * about every row while sorting.
 */
public final class RivChatFlags {

    public static final String SECURE = "secureChats";
    public static final String NO_PREVIEW = "noPreviewChats";
    public static final String LOCAL_PINS = "localPins";       // newest first
    public static final String EXPIRE = "expireChats";         // "id:millis"
    public static final String FONT = "fontChats";             // "id:size"

    private RivChatFlags() {
    }

    private static String lastRaw = null;
    private static ArrayList<String> lastPins = new ArrayList<>();
    private static final java.util.HashMap<String, String> rawCache = new java.util.HashMap<>();
    private static final java.util.HashMap<String, HashSet<String>> setCache = new java.util.HashMap<>();

    private static boolean has(String key, long id) {
        String raw = RivPrefs.s(key, "");
        if (raw == null || raw.isEmpty()) return false;
        HashSet<String> set;
        synchronized (setCache) {
            if (!raw.equals(rawCache.get(key))) {
                set = new HashSet<>();
                for (String p : raw.split(",")) {
                    if (!p.isEmpty()) set.add(p);
                }
                rawCache.put(key, raw);
                setCache.put(key, set);
            } else {
                set = setCache.get(key);
            }
        }
        return set.contains(String.valueOf(id));
    }

    public static boolean secure(long id) {
        return has(SECURE, id);
    }

    public static boolean noPreview(long id) {
        return has(NO_PREVIEW, id);
    }

    public static void set(String key, long id, boolean on) {
        if (RivPrefs.inList(key, id) != on) RivPrefs.toggleInList(key, id);
    }

    // ---- local pins: -1 = not pinned, otherwise higher = nearer the top
    public static int localPinRank(long id) {
        String raw = RivPrefs.s(LOCAL_PINS, "");
        if (raw == null || raw.isEmpty()) return -1;
        ArrayList<String> pins;
        synchronized (RivChatFlags.class) {
            if (!raw.equals(lastRaw)) {
                lastRaw = raw;
                lastPins = RivPrefs.list(LOCAL_PINS);
            }
            pins = lastPins;
        }
        int i = pins.indexOf(String.valueOf(id));
        return i < 0 ? -1 : pins.size() - i;
    }

    public static void setLocalPin(long id, boolean on) {
        ArrayList<String> l = RivPrefs.list(LOCAL_PINS);
        l.remove(String.valueOf(id));
        if (on) l.add(0, String.valueOf(id));
        RivPrefs.setList(LOCAL_PINS, l);
    }

    // ---- "id:value" maps
    public static long value(String key, long id, long def) {
        String prefix = id + ":";
        for (String e : RivPrefs.list(key)) {
            if (e.startsWith(prefix)) {
                try {
                    return Long.parseLong(e.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return def;
    }

    public static void setValue(String key, long id, long value) {
        String prefix = id + ":";
        ArrayList<String> l = RivPrefs.list(key);
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).startsWith(prefix)) l.remove(i);
        }
        if (value > 0) l.add(prefix + value);
        RivPrefs.setList(key, l);
    }

    public static long expireMs(long id) {
        return value(EXPIRE, id, 0);
    }

    public static int fontSize(long id) {
        return (int) value(FONT, id, 0);
    }
}
