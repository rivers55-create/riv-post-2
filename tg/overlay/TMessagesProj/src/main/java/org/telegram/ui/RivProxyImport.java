package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.utils.proxy.ProxySettings;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Riv Post: "Paste multiple proxies" on the proxy screen.
 *
 * Paste any text holding proxy links (one per line, or a whole channel post) and every link in it
 * is added: t.me / telegram.me / telegram.dog links (proxy, socks, webproxy) and tg:// links.
 * Each link goes through Telegram's own link reader, so only links Telegram itself would accept
 * get in. Duplicates are skipped. Nothing is switched on: the proxies are just added to the list,
 * where Telegram checks their speed as usual.
 */
public final class RivProxyImport {

    private static final Pattern LINK = Pattern.compile(
            "(?i)(?:https?://)?(?:t\\.me|telegram\\.me|telegram\\.dog)/(?:proxy|socks|webproxy)\\?[^\\s\"'<>]+"
                    + "|tg:(?://)?(?:proxy|socks|webproxy)\\?[^\\s\"'<>]+");

    private RivProxyImport() {
    }

    public static void show(BaseFragment fragment, Runnable onAdded) {
        Context context = fragment.getParentActivity();
        if (context == null) return;

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);

        TextView hint = new TextView(context);
        hint.setText(LocaleController.getString(R.string.RivProxyPasteInfo));
        hint.setTextSize(13);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        box.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setSingleLine(false);
        field.setMinLines(4);
        field.setMaxLines(10);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setTextSize(13);
        field.setHint("https://t.me/proxy?server=…&port=…&secret=…");
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        // Start with whatever is on the clipboard if it holds proxy links.
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).coerceToText(context);
                if (t != null && LINK.matcher(t).find()) field.setText(t);
            }
        } catch (Throwable ignored) {
        }
        box.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.RivProxyPasteTitle));
        builder.setView(box);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.RivProxyPasteAdd), (d, w) -> {
            int[] r = addAll(field.getText().toString());
            String msg = LocaleController.formatString(R.string.RivProxyPasteResult, r[0], r[1], r[2]);
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            if (r[0] > 0 && onAdded != null) onAdded.run();
        });
        fragment.showDialog(builder.create());
    }

    /** Returns {added, alreadyThere, notValid}. */
    public static int[] addAll(String text) {
        ArrayList<ProxySettings> found = new ArrayList<>();
        int invalid = 0;
        Matcher m = LINK.matcher(text == null ? "" : text);
        while (m.find()) {
            String link = m.group();
            // tidy up what chat posts usually wrap around a link
            link = link.replace("&amp;", "&");
            while (!link.isEmpty() && ").,;]}>*_".indexOf(link.charAt(link.length() - 1)) >= 0) {
                link = link.substring(0, link.length() - 1);
            }
            if (!link.regionMatches(true, 0, "http", 0, 4) && !link.regionMatches(true, 0, "tg:", 0, 3)) {
                link = "https://" + link;
            }
            ProxySettings s = null;
            try {
                s = ProxySettings.fromUri(Uri.parse(link));
            } catch (Throwable ignored) {
            }
            if (s == null || !s.isValid()) {
                invalid++;
                continue;
            }
            boolean dup = false;
            for (ProxySettings f : found) {
                if (f.equals(s)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) found.add(s);
        }
        int added = 0, existing = 0;
        SharedConfig.loadProxyList();
        // addProxy puts each new one at the top, so go backwards to keep the pasted order
        for (int i = found.size() - 1; i >= 0; i--) {
            SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(found.get(i));
            SharedConfig.ProxyInfo result = SharedConfig.addProxy(info);
            if (result == info) added++;
            else existing++;
        }
        return new int[]{added, existing, invalid};
    }
}
