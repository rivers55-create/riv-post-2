package org.telegram.messenger;

import android.os.Bundle;

import org.telegram.ui.ActionBar.Theme;

/** Riv Post: message text size per chat. The saved global size comes back when you leave the chat. */
public final class RivFontSize {

    private RivFontSize() {
    }

    private static int base() {
        int def = AndroidUtilities.isTablet() && !AndroidUtilities.isFold() ? 18 : 16;
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", 0).getInt("fons_size", def);
    }

    public static long idOf(Bundle args) {
        if (args == null) return 0;
        long user = args.getLong("user_id", 0);
        if (user != 0) return user;
        long chat = args.getLong("chat_id", 0);
        return chat != 0 ? -chat : 0;
    }

    public static void applyFor(long dialogId) {
        try {
            int custom = dialogId == 0 ? 0 : RivChatFlags.fontSize(dialogId);
            int target = custom > 0 ? custom : base();
            if (target != SharedConfig.fontSize) {
                SharedConfig.fontSize = target;
                Theme.createCommonMessageResources();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void restore() {
        applyFor(0);
    }
}
