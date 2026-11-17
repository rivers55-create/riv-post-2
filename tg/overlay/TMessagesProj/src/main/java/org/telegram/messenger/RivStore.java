package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** Riv Post: a list of small JSON records kept in RivPrefs under one key (notes, tags, starred, logs...). */
public final class RivStore {

    private RivStore() {
    }

    public static ArrayList<JSONObject> load(String key) {
        ArrayList<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(RivPrefs.s(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(String key, ArrayList<JSONObject> list) {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);
            RivPrefs.set(key, arr.toString());
        } catch (Throwable ignored) {
        }
    }
}
