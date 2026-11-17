package org.telegram.ui;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivConnLog;
import org.telegram.messenger.RivT;

import java.util.ArrayList;

/** Riv Post: when Telegram's connection changed, on which network, and whether a proxy was on. */
public class RivConnLogActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Connection log", "سجل الاتصال");
    }

    @Override
    protected void build() {
        ArrayList<JSONObject> list = RivConnLog.all();
        if (list.isEmpty()) {
            addInfo(RivT.t("Nothing recorded yet. Changes are logged from now on, while the app is running.", "لا شيء مسجل بعد. تُسجل التغييرات من الآن فصاعداً أثناء عمل التطبيق."));
            return;
        }
        addHeader(RivT.t("Latest first", "الأحدث أولاً") + " · " + list.size());
        for (int i = 0; i < list.size(); i++) {
            JSONObject o = list.get(i);
            String net = "wifi".equals(o.optString("net")) ? "Wi-Fi" : "mobile".equals(o.optString("net")) ? RivT.t("Mobile data", "بيانات الجوال") : RivT.t("No network", "لا شبكة");
            String extra = net + (o.optBoolean("proxy") ? "  ·  " + RivT.t("proxy on", "الوكيل مفعل") : "");
            addCell(RivConnLog.describe(o.optInt("state")), when(o.optLong("time")), true, null, null);
            addText(extra, false).setTextSize(12);
        }
        addAction(RivT.t("Clear log", "مسح السجل"), false, () -> confirm(RivT.t("Clear log", "مسح السجل"), RivT.t("Delete the log?", "حذف السجل؟"), LocaleController.getString(R.string.Delete), () -> {
            RivConnLog.clear();
            rebuild();
        }));
    }
}
