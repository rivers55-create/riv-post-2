package org.telegram.messenger;

import org.json.JSONArray;

import java.util.ArrayList;

/** Riv Post: saved quick-reply snippets. Stored as a JSON array in RivPrefs, same pattern as RivReminders. */
public class RivQuickReplies {

    private static final String KEY = "quickReplies";

    public static ArrayList<String> load() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(RivPrefs.s(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i);
                if (s != null && !s.isEmpty()) out.add(s);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void save(ArrayList<String> list) {
        try {
            RivPrefs.set(KEY, new JSONArray(list).toString());
        } catch (Throwable ignored) {
        }
    }

    public static void add(String text) {
        ArrayList<String> list = load();
        if (!list.contains(text)) {
            list.add(text);
            save(list);
        }
    }

    public static void remove(String text) {
        ArrayList<String> list = load();
        list.remove(text);
        save(list);
    }
}
