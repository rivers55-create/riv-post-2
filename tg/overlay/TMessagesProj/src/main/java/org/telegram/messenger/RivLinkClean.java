package org.telegram.messenger;

import android.net.Uri;

import java.util.Locale;

/** Riv Post: takes tracking bits (utm_*, fbclid, gclid, si...) off a link. */
public final class RivLinkClean {

    private RivLinkClean() {
    }

    private static boolean isTracker(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.startsWith("utm_") || n.equals("fbclid") || n.equals("gclid") || n.equals("dclid") || n.equals("msclkid")
                || n.equals("igshid") || n.equals("igsh") || n.equals("mc_cid") || n.equals("mc_eid") || n.equals("ref_src")
                || n.equals("_hsenc") || n.equals("_hsmi") || n.equals("yclid") || n.equals("twclid")
                || n.equals("si") || n.equals("spm") || n.equals("cmpid") || n.equals("ocid");
    }

    /** Cleans one link; text that is not a link comes back unchanged. */
    public static String clean(String link) {
        if (link == null) return "";
        String s = link.trim();
        try {
            Uri uri = Uri.parse(s);
            if (uri.getScheme() == null || uri.getHost() == null) return s;
            Uri.Builder b = uri.buildUpon().clearQuery();
            for (String name : uri.getQueryParameterNames()) {
                if (isTracker(name)) continue;
                for (String v : uri.getQueryParameters(name)) b.appendQueryParameter(name, v);
            }
            return b.build().toString();
        } catch (Throwable t) {
            return s;
        }
    }

    /** Cleans every link found in a block of text. */
    public static String cleanAll(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s]+").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(clean(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }
}
