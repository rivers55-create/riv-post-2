package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;

/**
 * Riv Post: remembers earlier versions of edited messages so "Edit history" can show what a
 * message said before. A version is saved when an edit arrives for a message that is loaded in
 * an open chat (Telegram doesn't send the old text with an edit).
 */
public class RivEdits {

    private static final String SEP_ITEM = "\u0001";
    private static final String SEP_PART = "\u0002";
    private static final int MAX_VERSIONS = 20;

    public static class Version {
        public final int date;
        public final String text;

        Version(int date, String text) {
            this.date = date;
            this.text = text;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("rivedits", Context.MODE_PRIVATE);
    }

    private static String key(MessageObject m) {
        return m.currentAccount + "_" + m.getDialogId() + "_" + m.getId();
    }

    private static String textOf(MessageObject m) {
        if (m == null || m.messageOwner == null) return null;
        return m.messageOwner.message;
    }

    /** Called when an open chat swaps a message for its edited version. */
    public static void record(MessageObject oldMessage, MessageObject newMessage) {
        try {
            if (!RivPrefs.b(RivPrefs.EDIT_HISTORY, true)) return;
            if (oldMessage == null || newMessage == null || newMessage.messageOwner == null) return;
            if (newMessage.messageOwner.edit_date == 0) return;
            String before = textOf(oldMessage);
            String after = textOf(newMessage);
            if (before == null || TextUtils.equals(before, after)) return;
            int when = oldMessage.messageOwner.edit_date != 0 ? oldMessage.messageOwner.edit_date : oldMessage.messageOwner.date;
            ArrayList<Version> list = get(newMessage);
            if (!list.isEmpty() && TextUtils.equals(list.get(list.size() - 1).text, before)) return;
            list.add(new Version(when, before));
            while (list.size() > MAX_VERSIONS) list.remove(0);
            StringBuilder sb = new StringBuilder();
            for (Version v : list) {
                if (sb.length() > 0) sb.append(SEP_ITEM);
                sb.append(v.date).append(SEP_PART).append(v.text.replace(SEP_ITEM, " ").replace(SEP_PART, " "));
            }
            prefs().edit().putString(key(newMessage), sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static ArrayList<Version> get(MessageObject m) {
        ArrayList<Version> out = new ArrayList<>();
        try {
            if (m == null) return out;
            String raw = prefs().getString(key(m), null);
            if (raw == null || raw.isEmpty()) return out;
            for (String item : raw.split(SEP_ITEM)) {
                int i = item.indexOf(SEP_PART);
                if (i <= 0) continue;
                out.add(new Version(Integer.parseInt(item.substring(0, i)), item.substring(i + 1)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static boolean has(MessageObject m) {
        try {
            return RivPrefs.b(RivPrefs.EDIT_HISTORY, true) && m != null && prefs().contains(key(m));
        } catch (Throwable t) {
            return false;
        }
    }
}
