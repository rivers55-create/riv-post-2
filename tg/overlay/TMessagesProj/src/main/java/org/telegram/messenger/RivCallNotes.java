package org.telegram.messenger;

import org.json.JSONObject;

import java.util.ArrayList;

/** Riv Post: a short note kept with a call-log row (key = the call message's date + user). */
public final class RivCallNotes {

    private static final String KEY = "rivCallNotes";

    private RivCallNotes() {
    }

    public static String keyOf(long userId, int date) {
        return userId + "_" + date;
    }

    public static String get(String key) {
        for (JSONObject o : RivStore.load(KEY)) if (key.equals(o.optString("k"))) return o.optString("n");
        return "";
    }

    public static void set(String key, String note) {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) if (key.equals(list.get(i).optString("k"))) list.remove(i);
        if (note != null && !note.trim().isEmpty()) {
            try {
                JSONObject o = new JSONObject();
                o.put("k", key);
                o.put("n", note.trim());
                list.add(o);
            } catch (Throwable ignored) {
            }
        }
        RivStore.save(KEY, list);
    }
}
