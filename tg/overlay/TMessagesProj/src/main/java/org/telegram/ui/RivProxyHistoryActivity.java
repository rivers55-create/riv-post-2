package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.RivT;
import org.telegram.messenger.RivProxyHistory;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Riv Post: how each saved proxy's ping changed over time. "Measure now" adds a sample for every proxy. */
public class RivProxyHistoryActivity extends RivToolScreen {

    private boolean measuring;

    @Override
    protected String title() {
        return RivT.t("Proxy history", "سجل الوكلاء");
    }

    @Override
    protected void build() {
        addAction(measuring ? RivT.t("Measuring…", "جارٍ القياس…") : RivT.t("Measure now", "قِس الآن"), false, () -> {
            if (measuring) return;
            measuring = true;
            rebuild();
            RivProxyHistory.measureAll(currentAccount, () -> {
                measuring = false;
                rebuild();
            });
        });
        SharedConfig.loadProxyList();
        if (SharedConfig.proxyList.isEmpty()) {
            addInfo(RivT.t("No proxies saved.", "لا توجد وكلاء محفوظة."));
            return;
        }
        for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
            String key = RivProxyHistory.keyOf(p);
            ArrayList<long[]> s = RivProxyHistory.samples(key);
            String name = RivProxyImport.nameOf(p);
            addHeader(name != null ? name + "  ·  " + key : key);
            if (s.isEmpty()) {
                addText(RivT.t("No measurements yet.", "لا توجد قياسات بعد."), false);
                continue;
            }
            long sum = 0, ok = 0, best = Long.MAX_VALUE, last = s.get(s.size() - 1)[1];
            for (long[] x : s) {
                if (x[1] >= 0) {
                    sum += x[1];
                    ok++;
                    best = Math.min(best, x[1]);
                }
            }
            String stats = RivT.t("Last: ", "الأخير: ") + (last < 0 ? RivT.t("no answer", "لا استجابة") : last + " ms")
                    + "   " + RivT.t("Average: ", "المتوسط: ") + (ok == 0 ? "—" : (sum / ok) + " ms")
                    + "   " + RivT.t("Best: ", "الأفضل: ") + (ok == 0 ? "—" : best + " ms")
                    + "   " + RivT.t("Answered: ", "استجاب: ") + (ok * 100 / s.size()) + "%  (" + s.size() + ")";
            addText(stats, false).setTextSize(13);
            layout.addView(new Graph(ctx, s), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70));
        }
        addInfo(RivT.t("Each dot is one measurement; red dots on the bottom line are times the proxy did not answer. Up to 96 measurements are kept per proxy.",
                "كل نقطة قياس واحد؛ النقاط الحمراء على الخط السفلي تعني أن الوكيل لم يستجب. يُحتفظ بما يصل إلى 96 قياساً لكل وكيل."));
    }

    private static class Graph extends View {
        private final ArrayList<long[]> samples;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bad = new Paint(Paint.ANTI_ALIAS_FLAG);

        Graph(Context context, ArrayList<long[]> samples) {
            super(context);
            this.samples = samples;
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(AndroidUtilities.dp(2));
            line.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            dot.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            bad.setColor(0xFFE53935);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float pad = AndroidUtilities.dp(14);
            long max = 100;
            for (long[] x : samples) max = Math.max(max, x[1]);
            int n = samples.size();
            Path path = new Path();
            boolean started = false;
            for (int i = 0; i < n; i++) {
                float x = n == 1 ? w / 2f : pad + (w - 2 * pad) * i / (n - 1);
                long p = samples.get(i)[1];
                if (p < 0) {
                    canvas.drawCircle(x, h - pad / 2, AndroidUtilities.dp(3), bad);
                    started = false;
                    continue;
                }
                float y = h - pad - (h - 2 * pad) * p / (float) max;
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else path.lineTo(x, y);
                canvas.drawCircle(x, y, AndroidUtilities.dp(2.5f), dot);
            }
            canvas.drawPath(path, line);
        }
    }
}
