package org.telegram.messenger;

import java.util.Locale;

/** Riv Post: one-line English/Arabic text for the Riv Tools screens (Arabic when the app language is Arabic). */
public final class RivT {

    private RivT() {
    }

    public static boolean isArabic() {
        try {
            return "ar".equals(LocaleController.getInstance().getCurrentLocale().getLanguage());
        } catch (Throwable t) {
            return "ar".equals(Locale.getDefault().getLanguage());
        }
    }

    public static String t(String en, String ar) {
        return isArabic() && ar != null ? ar : en;
    }
}
