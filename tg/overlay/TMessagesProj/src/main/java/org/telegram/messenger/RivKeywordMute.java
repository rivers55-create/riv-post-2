package org.telegram.messenger;

import java.util.ArrayList;

/** Riv Post: incoming messages that contain one of your keywords never make a notification. */
public final class RivKeywordMute {

    public static final String KEY = "muteKeywords"; // one keyword per line

    private RivKeywordMute() {
    }

    public static ArrayList<String> words() {
        ArrayList<String> out = new ArrayList<>();
        String raw = RivPrefs.s(KEY, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String w : raw.split("\n")) {
            w = w.trim();
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    public static void setWords(ArrayList<String> words) {
        RivPrefs.set(KEY, android.text.TextUtils.join("\n", words));
    }

    /** Used by NotificationsController: returns the messages that may still notify. */
    public static ArrayList<MessageObject> filter(ArrayList<MessageObject> in) {
        try {
            if (in == null || in.isEmpty()) return in;
            ArrayList<String> words = words();
            if (words.isEmpty()) return in;
            ArrayList<String> lower = new ArrayList<>(words.size());
            for (String w : words) lower.add(w.toLowerCase());
            ArrayList<MessageObject> out = new ArrayList<>(in.size());
            for (MessageObject m : in) {
                String text = m == null || m.messageOwner == null ? null : m.messageOwner.message;
                boolean hit = false;
                if (m != null && !m.isOutOwner() && text != null && !text.isEmpty()) {
                    String t = text.toLowerCase();
                    for (String w : lower) {
                        if (t.contains(w)) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (!hit) out.add(m);
            }
            return out.size() == in.size() ? in : out;
        } catch (Throwable t) {
            FileLog.e(t);
            return in;
        }
    }
}
