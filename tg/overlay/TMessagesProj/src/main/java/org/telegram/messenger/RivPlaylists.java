package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Riv Post: user-made playlists for the music library. Stored as JSON in RivPrefs, same pattern as RivReminders. */
public class RivPlaylists {

    private static final String KEY = "musicPlaylists";

    /** name -> ordered list of song keys ("acc_dialogId_msgId"). */
    public static LinkedHashMap<String, ArrayList<String>> load() {
        LinkedHashMap<String, ArrayList<String>> out = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONArray(RivPrefs.s(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name");
                ArrayList<String> keys = new ArrayList<>();
                JSONArray songs = o.optJSONArray("songs");
                if (songs != null) {
                    for (int j = 0; j < songs.length(); j++) keys.add(songs.optString(j));
                }
                out.put(name, keys);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void save(LinkedHashMap<String, ArrayList<String>> map) {
        try {
            JSONArray arr = new JSONArray();
            for (java.util.Map.Entry<String, ArrayList<String>> e : map.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("name", e.getKey());
                o.put("songs", new JSONArray(e.getValue()));
                arr.put(o);
            }
            RivPrefs.set(KEY, arr.toString());
        } catch (Throwable ignored) {
        }
    }

    public static void create(String name) {
        LinkedHashMap<String, ArrayList<String>> map = load();
        if (!map.containsKey(name)) {
            map.put(name, new ArrayList<>());
            save(map);
        }
    }

    public static void delete(String name) {
        LinkedHashMap<String, ArrayList<String>> map = load();
        map.remove(name);
        save(map);
    }

    public static void addTo(String name, String songKey) {
        LinkedHashMap<String, ArrayList<String>> map = load();
        ArrayList<String> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        if (!list.contains(songKey)) list.add(songKey);
        save(map);
    }

    public static void removeFrom(String name, String songKey) {
        LinkedHashMap<String, ArrayList<String>> map = load();
        ArrayList<String> list = map.get(name);
        if (list != null) {
            list.remove(songKey);
            save(map);
        }
    }
}
