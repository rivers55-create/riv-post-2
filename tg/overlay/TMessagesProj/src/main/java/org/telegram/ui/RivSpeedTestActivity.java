package org.telegram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.RivT;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Riv Post: speed test. Measures your internet (delay, download, upload) against Cloudflare's test
 * servers, and separately the ping from Telegram's own connection to the proxy that is on now.
 * The proxy only carries Telegram's traffic, so the internet numbers do not go through it.
 */
public class RivSpeedTestActivity extends RivToolScreen {

    private volatile boolean running;
    private String delay = "", down = "", up = "", proxyLine = "", error = "";
    private String date = "";

    @Override
    protected String title() {
        return RivT.t("Speed test", "اختبار السرعة");
    }

    @Override
    protected void build() {
        addAction(running ? RivT.t("Testing…", "جارٍ الاختبار…") : RivT.t("Start test", "ابدأ الاختبار"), false, this::start);
        if (!date.isEmpty()) addHeader(RivT.t("Result", "النتيجة") + "  ·  " + date);
        if (!delay.isEmpty()) addCell(RivT.t("Delay", "التأخير"), delay, true, null, null);
        if (!down.isEmpty()) addCell(RivT.t("Download", "التنزيل"), down, true, null, null);
        if (!up.isEmpty()) addCell(RivT.t("Upload", "الرفع"), up, true, null, null);
        if (!proxyLine.isEmpty()) addCell(RivT.t("Telegram proxy ping", "بينغ وكيل تيليجرام"), proxyLine, false, null, null);
        if (!error.isEmpty()) addInfo(error);
        addInfo(RivT.t("Uses about 20 MB of data. The internet numbers are for your connection; Telegram's proxy only carries Telegram's own traffic, so its ping is shown on its own line.",
                "يستهلك نحو 20 ميغابايت. أرقام الإنترنت لاتصالك؛ الوكيل يحمل بيانات تيليجرام فقط لذلك يظهر بينغه في سطر مستقل."));
    }

    private void start() {
        if (running) return;
        running = true;
        delay = down = up = proxyLine = error = "";
        rebuild();
        // Telegram's own proxy, if one is on
        try {
            if (SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null) {
                ConnectionsManager.getInstance(currentAccount).checkProxy(SharedConfig.currentProxy.settings, time -> AndroidUtilities.runOnUIThread(() -> {
                    proxyLine = time < 0 ? RivT.t("no answer", "لا استجابة") : time + " ms";
                    rebuild();
                }));
            } else {
                proxyLine = RivT.t("no proxy is on", "لا يوجد وكيل مفعل");
            }
        } catch (Throwable ignored) {
        }
        Utilities.globalQueue.postRunnable(() -> {
            String d = "", dl = "", ul = "", err = "";
            try {
                // delay: a tiny request, best of 3
                long best = Long.MAX_VALUE;
                for (int i = 0; i < 3; i++) {
                    long t0 = System.nanoTime();
                    HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=0").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.getResponseCode();
                    c.disconnect();
                    best = Math.min(best, (System.nanoTime() - t0) / 1000000L);
                }
                d = best + " ms";
                // download 12 MB
                long t1 = System.nanoTime();
                HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=12000000").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                InputStream in = c.getInputStream();
                byte[] buf = new byte[65536];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) total += n;
                in.close();
                double secs = Math.max(0.001, (System.nanoTime() - t1) / 1e9);
                dl = String.format(java.util.Locale.US, "%.1f Mbit/s", total * 8 / secs / 1e6);
                // upload 4 MB
                long t2 = System.nanoTime();
                HttpURLConnection u = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
                u.setConnectTimeout(8000);
                u.setReadTimeout(15000);
                u.setDoOutput(true);
                u.setRequestMethod("POST");
                u.setFixedLengthStreamingMode(4000000);
                OutputStream out = u.getOutputStream();
                byte[] chunk = new byte[65536];
                int sent = 0;
                while (sent < 4000000) {
                    int w = Math.min(chunk.length, 4000000 - sent);
                    out.write(chunk, 0, w);
                    sent += w;
                }
                out.close();
                u.getResponseCode();
                double secs2 = Math.max(0.001, (System.nanoTime() - t2) / 1e9);
                ul = String.format(java.util.Locale.US, "%.1f Mbit/s", sent * 8 / secs2 / 1e6);
            } catch (Throwable t) {
                err = RivT.t("The test could not finish: ", "تعذر إكمال الاختبار: ") + t.getMessage();
            }
            final String fd = d, fdl = dl, ful = ul, ferr = err;
            AndroidUtilities.runOnUIThread(() -> {
                delay = fd;
                down = fdl;
                up = ful;
                error = ferr;
                date = when(System.currentTimeMillis());
                running = false;
                rebuild();
            });
        });
    }
}
