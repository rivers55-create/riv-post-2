package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Riv Post: before Telegram opens a web link, check it for the usual tricks (raw IP address,
 * look-alike letters, a link shortener, a brand name on someone else's domain, a hidden user name
 * before an @, a download that installs software). If something looks off, ask first.
 */
public final class RivLinkSafety {

    public static final String KEY = "linkSafety";
    private static String allowed = null;

    private static final String[] SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "ow.ly", "shorturl.at", "tiny.cc", "buff.ly", "rebrand.ly", "s.id", "v.gd", "lnkd.in"};
    private static final String[] BRANDS = {"paypal", "google", "apple", "icloud", "facebook", "instagram", "telegram", "whatsapp", "microsoft", "amazon", "netflix", "binance", "coinbase", "tiktok", "twitter", "spotify", "steam", "discord"};
    private static final String[] BAD_FILES = {".apk", ".exe", ".scr", ".bat", ".msi", ".cmd", ".vbs", ".jar", ".ps1", ".lnk"};
    private static final Pattern IP = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private RivLinkSafety() {
    }

    /** Returns true when the link was held back (a question is on screen). */
    public static boolean guard(Context context, Uri uri) {
        try {
            if (uri == null || !RivPrefs.b(KEY, true)) return false;
            String url = uri.toString();
            if (url.equals(allowed)) {
                allowed = null;
                return false;
            }
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return false;
            ArrayList<String> why = problems(uri);
            if (why.isEmpty()) return false;
            Activity activity = context instanceof Activity ? (Activity) context : LaunchActivity.instance;
            if (activity == null) return false;
            StringBuilder sb = new StringBuilder();
            for (String w : why) sb.append("• ").append(w).append('\n');
            sb.append('\n').append(url.length() > 160 ? url.substring(0, 160) + "…" : url);
            AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle(RivT.t("Check this link first", "افحص هذا الرابط أولاً"));
            b.setMessage(sb.toString());
            b.setPositiveButton(RivT.t("Open anyway", "افتح على أي حال"), (d, w) -> {
                allowed = url;
                org.telegram.messenger.browser.Browser.openUrl(activity, uri);
            });
            b.setNeutralButton(RivT.t("Copy link", "نسخ الرابط"), (d, w) -> AndroidUtilities.addToClipboard(url));
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            b.show();
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static ArrayList<String> problems(Uri uri) {
        ArrayList<String> out = new ArrayList<>();
        String host = uri.getHost();
        if (host == null) return out;
        host = host.toLowerCase();
        if (host.endsWith("telegram.org") || host.equals("t.me") || host.endsWith(".t.me") || host.equals("telegra.ph")) return out;
        if (uri.getUserInfo() != null) {
            out.add(RivT.t("It has a name before an @ that hides the real site.", "فيه اسم قبل @ يخفي الموقع الحقيقي."));
        }
        if (IP.matcher(host).matches()) {
            out.add(RivT.t("It goes to a raw IP address, not a website name.", "يذهب إلى عنوان IP مباشر وليس اسم موقع."));
        }
        if (host.contains("xn--")) {
            out.add(RivT.t("The address uses look-alike letters (punycode).", "العنوان يستخدم أحرفاً مشابهة (punycode)."));
        }
        for (String s : SHORTENERS) {
            if (host.equals(s)) {
                out.add(RivT.t("It is a shortened link, so the real site is hidden.", "رابط مختصر، فالموقع الحقيقي مخفي."));
                break;
            }
        }
        String reg = registrable(host);
        for (String brand : BRANDS) {
            if (host.contains(brand) && !reg.startsWith(brand + ".")) {
                out.add(RivT.t("It uses the name \"" + brand + "\" but is not that company's site.", "يستخدم اسم \"" + brand + "\" لكنه ليس موقع الشركة."));
                break;
            }
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        for (String ext : BAD_FILES) {
            if (path.endsWith(ext)) {
                out.add(RivT.t("It downloads a program (" + ext + ").", "يُنزّل برنامجاً (" + ext + ")."));
                break;
            }
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            out.add(RivT.t("The connection is not encrypted (http).", "الاتصال غير مشفر (http)."));
        }
        return out;
    }

    private static String registrable(String host) {
        String[] p = host.split("\\.");
        if (p.length <= 2) return host;
        String last = p[p.length - 1], second = p[p.length - 2];
        boolean twoPart = last.length() == 2 && (second.equals("co") || second.equals("com") || second.equals("org") || second.equals("net") || second.equals("gov") || second.equals("ac"));
        int n = twoPart ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = p.length - n; i < p.length; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(p[i]);
        }
        return sb.toString();
    }
}
