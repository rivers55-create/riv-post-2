package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivClipboard;
import org.telegram.messenger.RivT;

import java.util.ArrayList;

/** Riv Post: the last 20 things you copied, tap to copy one again. Off until you turn it on. */
public class RivClipboardActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Clipboard history", "سجل الحافظة");
    }

    @Override
    protected void build() {
        addSwitch(RivT.t("Remember what I copy", "تذكّر ما أنسخه"),
                RivT.t("Off by default. Copied passwords and codes would be saved here too.", "متوقف افتراضياً. ستُحفظ هنا كلمات المرور والرموز المنسوخة أيضاً."),
                RivClipboard.ON, false, false);
        addInfo(RivT.t("Android only lets an app see the clipboard while that app is in front, so this records copies made while Riv Post is open.",
                "يسمح أندرويد للتطبيق برؤية الحافظة فقط وهو في المقدمة، لذلك تُسجَّل النسخات أثناء فتح Riv Post."));
        ArrayList<JSONObject> list = RivClipboard.all();
        if (list.isEmpty()) {
            addInfo(RivT.t("Nothing saved yet.", "لا يوجد شيء محفوظ بعد."));
            return;
        }
        addHeader(RivT.t("Recent", "الأحدث"));
        for (int i = 0; i < list.size(); i++) {
            final String text = list.get(i).optString("t");
            String shown = text.replace('\n', ' ');
            addCell(shown.length() > 70 ? shown.substring(0, 70) + "…" : shown, "", i != list.size() - 1, () -> {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("riv", text));
                toast(LocaleController.getString(R.string.RivCopied));
            }, () -> confirm(RivT.t("Remove", "إزالة"), RivT.t("Remove this item?", "إزالة هذا العنصر؟"), LocaleController.getString(R.string.Delete), () -> {
                RivClipboard.remove(text);
                rebuild();
            }));
        }
        addAction(RivT.t("Clear all", "مسح الكل"), false, () -> confirm(RivT.t("Clear all", "مسح الكل"), RivT.t("Delete the whole history?", "حذف السجل بالكامل؟"), LocaleController.getString(R.string.Delete), () -> {
            RivClipboard.clear();
            rebuild();
        }));
    }
}
