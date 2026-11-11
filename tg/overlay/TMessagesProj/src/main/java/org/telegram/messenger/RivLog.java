package org.telegram.messenger;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Riv Post: one debug log the user can share from Settings.
 * Collects the app's own logcat plus Android's reports of earlier crashes,
 * including crashes inside the native call engine.
 */
public class RivLog {

    public static void send(Activity activity) {
        try {
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "logs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, "riv_debug_log.txt");
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
                w.write("Riv Post debug log\n");
                w.write("App version: " + BuildVars.BUILD_VERSION_STRING + "\n");
                w.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + "\n");
                w.write("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()) + "\n\n");

                w.write("==== Earlier exits and crashes (newest first) ====\n");
                writeExitInfo(w);

                w.write("\n==== App log (logcat) ====\n");
                w.flush();
                writeLogcat(out);
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.applicationContext.getPackageName() + ".provider", out);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Riv Post debug log"));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void writeExitInfo(OutputStreamWriter w) {
        try {
            if (Build.VERSION.SDK_INT < 30) {
                w.write("(needs Android 11 or newer)\n");
                return;
            }
            ActivityManager am = (ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(null, 0, 8);
            if (exits == null || exits.isEmpty()) {
                w.write("(none recorded)\n");
                return;
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            for (ApplicationExitInfo e : exits) {
                w.write(f.format(new Date(e.getTimestamp())) + "  reason=" + e.getReason() + "  " + String.valueOf(e.getDescription()) + "\n");
                if (e.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE || e.getReason() == ApplicationExitInfo.REASON_CRASH || e.getReason() == ApplicationExitInfo.REASON_ANR) {
                    try (InputStream in = e.getTraceInputStream()) {
                        if (in != null) {
                            BufferedReader r = new BufferedReader(new InputStreamReader(in));
                            String line;
                            int lines = 0;
                            while ((line = r.readLine()) != null && lines < 400) {
                                w.write("    " + line + "\n");
                                lines++;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            try {
                w.write("(couldn't read: " + t + ")\n");
            } catch (Throwable ignored) {
            }
        }
    }

    private static void writeLogcat(File out) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "4000"});
            try (InputStream in = p.getInputStream(); FileOutputStream fo = new FileOutputStream(out, true)) {
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0 && total < 2_000_000) {
                    fo.write(buf, 0, n);
                    total += n;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
