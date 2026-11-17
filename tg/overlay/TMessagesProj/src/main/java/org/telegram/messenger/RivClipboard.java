package org.telegram.messenger;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Riv Post: clipboard history. OFF until you turn it on, because copied text can be private
 * (passwords, codes). Android only lets an app see the clipboard while it is in front, so this
 * remembers what you copy while Riv Post is open or being used, not when another app is.
 */
public final class RivClipboard {

    public static final String ON = "rivClipboardOn";
    private static final String KEY = "rivClipboard";
    private static final int MAX = 20;
    private static boolean started;

    private RivClipboard() {
    }

    public static boolean on() {
        return RivPrefs.b(ON, false);
    }

    public static synchronized void start(Context context) {
        if (started || context == null) return;
        started = true;
        try {
            final ClipboardManager cm = (ClipboardManager) context.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.addPrimaryClipChangedListener(() -> {
                if (!on()) return;
                try {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return;
                    CharSequence cs = clip.getItemAt(0).coerceToText(context);
                    if (cs == null) return;
                    add(cs.toString());
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void add(String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 3000) return;
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) if (t.equals(list.get(i).optString("t"))) list.remove(i);
        try {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("time", System.currentTimeMillis());
            list.add(0, o);
        } catch (Throwable ignored) {
        }
        while (list.size() > MAX) list.remove(list.size() - 1);
        RivStore.save(KEY, list);
    }

    public static ArrayList<JSONObject> all() {
        return RivStore.load(KEY);
    }

    public static void clear() {
        RivPrefs.set(KEY, "[]");
    }

    public static void remove(String text) {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) if (text.equals(list.get(i).optString("t"))) list.remove(i);
        RivStore.save(KEY, list);
    }
}
