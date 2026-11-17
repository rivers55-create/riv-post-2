package org.telegram.messenger;

import android.content.Context;
import android.widget.Toast;

/** Riv Post: starts the small always-on helpers (connection log, clipboard history, auto-reply, auto-clean, call reminder) once per run. */
public final class RivWatchers implements NotificationCenter.NotificationCenterDelegate {

    public static final String CALL_REMINDER = "callReminder";
    private static RivWatchers instance;

    private RivWatchers() {
    }

    public static void start(Context context) {
        try {
            RivConnLog.start();
            RivClipboard.start(context);
        } catch (Throwable ignored) {
        }
        try {
            RivAutoReply.start();
            RivLocalExpire.start();
            synchronized (RivWatchers.class) {
                if (instance == null) {
                    instance = new RivWatchers();
                    NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.didStartedCall);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didStartedCall && RivPrefs.b(CALL_REMINDER, false)) {
            Toast.makeText(ApplicationLoader.applicationContext,
                    RivT.t("Reminder: tell the other person before you record this call.", "تذكير: أخبر الطرف الآخر قبل تسجيل هذه المكالمة."), Toast.LENGTH_LONG).show();
        }
    }
}
