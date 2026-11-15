package org.telegram.messenger;

import java.util.Calendar;

/** Riv Post: quiet hours. Chosen chats make no notification between two times (e.g. 23:00-07:00). */
public class RivQuiet {

    public static int from() {
        return RivPrefs.i(RivPrefs.QUIET_FROM, 23 * 60);
    }

    public static int to() {
        return RivPrefs.i(RivPrefs.QUIET_TO, 7 * 60);
    }

    public static boolean nowInQuietHours() {
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int f = from(), t = to();
        if (f == t) return false;
        return f < t ? (now >= f && now < t) : (now >= f || now < t);
    }

    /** True when notifications for this chat should be silent right now. */
    public static boolean isQuiet(long dialogId) {
        try {
            return RivPrefs.inList(RivPrefs.QUIET_CHATS, dialogId) && nowInQuietHours();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String format(int minutes) {
        return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
