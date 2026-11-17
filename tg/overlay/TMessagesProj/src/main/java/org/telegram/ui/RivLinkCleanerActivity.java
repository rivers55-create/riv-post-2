package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.RivLinkClean;
import org.telegram.messenger.RivT;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Riv Post: link cleaner. Paste a link (or any text with links) and take the tracking parts off. */
public class RivLinkCleanerActivity extends RivToolScreen {

    private String input = "";
    private String output = "";

    @Override
    protected String title() {
        return RivT.t("Link cleaner", "منظف الروابط");
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    protected void build() {
        EditText edit = new EditText(ctx);
        edit.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        edit.setHint(RivT.t("Paste a link or text here", "الصق رابطاً أو نصاً هنا"));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setMinLines(3);
        edit.setGravity(android.view.Gravity.TOP);
        edit.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        edit.setText(input);
        layout.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addAction(RivT.t("Paste from clipboard", "لصق من الحافظة"), true, () -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
                input = cs == null ? "" : cs.toString();
                output = RivLinkClean.cleanAll(input);
                rebuild();
            }
        });
        addAction(RivT.t("Clean", "تنظيف"), false, () -> {
            input = edit.getText().toString();
            output = RivLinkClean.cleanAll(input);
            rebuild();
        });
        if (!output.isEmpty()) {
            addHeader(RivT.t("Cleaned", "بعد التنظيف"));
            addText(output, false);
            addAction(RivT.t("Copy", "نسخ"), true, () -> copy(output));
            addAction(RivT.t("Share", "مشاركة"), false, () -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, output);
                Intent chooser = Intent.createChooser(share, title());
                ctx.startActivity(chooser);
            });
            addInfo(input.equals(output) ? RivT.t("Nothing to remove: this link had no tracking parts.", "لا يوجد ما يُزال: لا تحتوي هذه الروابط على أجزاء تتبع.")
                    : RivT.t("Removed tracking parts such as utm_*, fbclid, gclid and si.", "تمت إزالة أجزاء التتبع مثل utm_* و fbclid و gclid و si."));
        }
    }
}
