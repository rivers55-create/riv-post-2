package org.telegram.messenger;

import android.media.audiofx.Equalizer;

import java.util.ArrayList;

/**
 * Riv Post: a real equalizer for songs, using the phone's own audio effects. Attached to each
 * song's audio as it starts (hook in MediaController), with the band levels saved in settings.
 */
public class RivEq {

    private static Equalizer eq;
    private static int session;

    public static synchronized void attach(int sessionId) {
        try {
            if (!RivPrefs.b(RivPrefs.EQ_ON)) {
                release();
                return;
            }
            if (sessionId == 0) return;
            if (eq != null && session == sessionId) {
                applySaved();
                return;
            }
            release();
            eq = new Equalizer(0, sessionId);
            session = sessionId;
            applySaved();
            eq.setEnabled(true);
        } catch (Throwable t) {
            FileLog.e(t);
            eq = null;
            session = 0;
        }
    }

    public static synchronized void release() {
        try {
            if (eq != null) eq.release();
        } catch (Throwable ignored) {
        }
        eq = null;
        session = 0;
    }

    /** The live equalizer for the song playing now (created if needed), or null. */
    public static synchronized Equalizer current() {
        if (eq == null) {
            boolean was = RivPrefs.b(RivPrefs.EQ_ON);
            RivPrefs.set(RivPrefs.EQ_ON, true);
            attach(MediaController.getInstance().rivAudioSessionId());
            if (!was && eq == null) RivPrefs.set(RivPrefs.EQ_ON, false);
        }
        return eq;
    }

    public static synchronized void setEnabled(boolean on) {
        RivPrefs.set(RivPrefs.EQ_ON, on);
        if (on) {
            attach(MediaController.getInstance().rivAudioSessionId());
        } else {
            release();
        }
    }

    public static ArrayList<Integer> savedLevels() {
        ArrayList<Integer> out = new ArrayList<>();
        for (String p : RivPrefs.list(RivPrefs.EQ_BANDS)) {
            try {
                out.add(Integer.parseInt(p));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static synchronized void setBand(short band, int level) {
        try {
            if (eq == null) return;
            short[] range = eq.getBandLevelRange();
            short lv = (short) Math.max(range[0], Math.min(range[1], level));
            eq.setBandLevel(band, lv);
            save();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static synchronized void usePreset(short preset) {
        try {
            if (eq == null) return;
            eq.usePreset(preset);
            RivPrefs.set(RivPrefs.EQ_PRESET, eq.getPresetName(preset));
            save();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void save() {
        if (eq == null) return;
        ArrayList<String> levels = new ArrayList<>();
        for (short b = 0; b < eq.getNumberOfBands(); b++) {
            levels.add(String.valueOf(eq.getBandLevel(b)));
        }
        RivPrefs.setList(RivPrefs.EQ_BANDS, levels);
    }

    private static void applySaved() {
        if (eq == null) return;
        try {
            ArrayList<Integer> levels = savedLevels();
            short[] range = eq.getBandLevelRange();
            for (short b = 0; b < eq.getNumberOfBands() && b < levels.size(); b++) {
                eq.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], levels.get(b))));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
