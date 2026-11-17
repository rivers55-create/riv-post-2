package org.telegram.messenger;

import android.content.Context;

/** Riv Post: starts the small always-on helpers (connection log, clipboard history) once per run. */
public final class RivWatchers {

    private RivWatchers() {
    }

    public static void start(Context context) {
        try {
            RivConnLog.start();
            RivClipboard.start(context);
        } catch (Throwable ignored) {
        }
    }
}
