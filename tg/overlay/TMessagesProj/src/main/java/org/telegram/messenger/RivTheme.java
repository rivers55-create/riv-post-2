package org.telegram.messenger;

import android.util.SparseIntArray;

import org.telegram.ui.ActionBar.Theme;

/**
 * Riv Post: palette overrides applied whenever a theme's color table is rebuilt.
 * AMOLED turns every dark background pure black. Green classic is a light look
 * with a dark-green bar and light-green outgoing bubbles.
 */
public class RivTheme {

    public static final String AMOLED = "amoled";
    public static final String GREEN = "greenClassic";

    // custom colours (0 = automatic / theme's own)
    public static final String BG_COLOR = "colorBackground";
    public static final String CARD_COLOR = "colorCards";
    public static final String TEXT_COLOR = "colorText";
    public static final String NAV_COLOR = "colorNavBar";
    public static final String MINI_COLOR = "colorMiniPlayer";

    /** Accent choices shown as swatches (same idea as RTP's theme screen). */
    public static final int[] SWATCHES = {
            0xFF7C4DFF, 0xFFAB47BC, 0xFF5E35B1, 0xFFB388FF, 0xFF3D5AFE, 0xFF2196F3, 0xFF00BCD4,
            0xFF26A69A, 0xFF43A047, 0xFFC0CA33, 0xFFFFB300, 0xFFFF7043, 0xFFE53935, 0xFFEC407A,
    };

    private static final int BLACK = 0xFF000000;
    private static final int NEAR_BLACK = 0xFF0D0D0D;
    private static final int DARK_CARD = 0xFF151515;

    private static final int GREEN_BAR = 0xFF075E54;
    private static final int GREEN_ACCENT = 0xFF25D366;
    private static final int GREEN_OUT = 0xFFD9FDD3;
    private static final int GREEN_OUT_SELECTED = 0xFFC5EEC0;
    private static final int BEIGE_WALL = 0xFFEFE7DD;

    /** Called from Theme.refreshThemeColors before the table is published. */
    public static void apply(SparseIntArray colors, boolean isDark) {
        try {
            if (isDark && RivPrefs.b(AMOLED)) {
                put(colors, Theme.key_windowBackgroundWhite, BLACK);
                put(colors, Theme.key_windowBackgroundGray, BLACK);
                put(colors, Theme.key_windowBackgroundGrayShadow, BLACK);
                put(colors, Theme.key_actionBarDefault, BLACK);
                put(colors, Theme.key_actionBarDefaultSubmenuBackground, DARK_CARD);
                put(colors, Theme.key_actionBarActionModeDefault, BLACK);
                put(colors, Theme.key_chats_menuBackground, BLACK);
                put(colors, Theme.key_dialogBackground, BLACK);
                put(colors, Theme.key_dialogBackgroundGray, NEAR_BLACK);
                put(colors, Theme.key_graySection, NEAR_BLACK);
                put(colors, Theme.key_divider, NEAR_BLACK);
                put(colors, Theme.key_chat_messagePanelBackground, BLACK);
                put(colors, Theme.key_chat_emojiPanelBackground, BLACK);
                put(colors, Theme.key_chat_wallpaper, BLACK);
                put(colors, Theme.key_chat_inBubble, DARK_CARD);
                put(colors, Theme.key_chat_inBubbleSelected, 0xFF1F1F1F);
                put(colors, Theme.key_chat_inBubbleShadow, BLACK);
            }
            int accent = RivPrefs.i(RivPrefs.ACCENT, 0);
            if (accent != 0) {
                put(colors, Theme.key_windowBackgroundWhiteBlueText4, accent);
                put(colors, Theme.key_windowBackgroundWhiteValueText, accent);
                put(colors, Theme.key_windowBackgroundWhiteBlueHeader, accent);
                put(colors, Theme.key_chats_actionBackground, accent);
                put(colors, Theme.key_chats_unreadCounter, accent);
                put(colors, Theme.key_switchTrackChecked, accent);
                put(colors, Theme.key_checkboxSquareBackground, accent);
                put(colors, Theme.key_featuredStickers_addButton, accent);
                put(colors, Theme.key_actionBarTabLine, accent);
                put(colors, Theme.key_actionBarTabActiveText, accent);
                put(colors, Theme.key_chats_menuItemCheck, accent);
            }
            int bg = RivPrefs.i(BG_COLOR, 0);
            if (bg != 0) {
                put(colors, Theme.key_windowBackgroundWhite, bg);
                put(colors, Theme.key_windowBackgroundGray, bg);
                put(colors, Theme.key_actionBarDefault, bg);
                put(colors, Theme.key_chats_menuBackground, bg);
            }
            int card = RivPrefs.i(CARD_COLOR, 0);
            if (card != 0) {
                put(colors, Theme.key_actionBarDefaultSubmenuBackground, card);
                put(colors, Theme.key_dialogBackground, card);
                put(colors, Theme.key_chat_inBubble, card);
            }
            int text = RivPrefs.i(TEXT_COLOR, 0);
            if (text != 0) {
                put(colors, Theme.key_windowBackgroundWhiteBlackText, text);
                put(colors, Theme.key_chats_name, text);
                put(colors, Theme.key_actionBarDefaultTitle, text);
                put(colors, Theme.key_dialogTextBlack, text);
            }
            if (!isDark && RivPrefs.b(GREEN)) {
                put(colors, Theme.key_actionBarDefault, GREEN_BAR);
                put(colors, Theme.key_actionBarDefaultIcon, 0xFFFFFFFF);
                put(colors, Theme.key_actionBarDefaultTitle, 0xFFFFFFFF);
                put(colors, Theme.key_actionBarDefaultSubtitle, 0xFFD0E8E4);
                put(colors, Theme.key_actionBarDefaultSelector, 0xFF054C44);
                put(colors, Theme.key_actionBarDefaultSearch, 0xFFFFFFFF);
                put(colors, Theme.key_actionBarDefaultSearchPlaceholder, 0x88FFFFFF);
                put(colors, Theme.key_avatar_backgroundActionBarBlue, GREEN_BAR);
                put(colors, Theme.key_actionBarTabActiveText, 0xFFFFFFFF);
                put(colors, Theme.key_actionBarTabUnactiveText, 0xFFA0D5CE);
                put(colors, Theme.key_actionBarTabLine, 0xFFFFFFFF);
                put(colors, Theme.key_chats_actionBackground, GREEN_ACCENT);
                put(colors, Theme.key_chats_actionPressedBackground, 0xFF1EB757);
                put(colors, Theme.key_chats_unreadCounter, GREEN_ACCENT);
                put(colors, Theme.key_chat_outBubble, GREEN_OUT);
                put(colors, Theme.key_chat_outBubbleSelected, GREEN_OUT_SELECTED);
                put(colors, Theme.key_chat_wallpaper, BEIGE_WALL);
                put(colors, Theme.key_featuredStickers_addButton, GREEN_ACCENT);
                put(colors, Theme.key_switchTrackChecked, GREEN_ACCENT);
                put(colors, Theme.key_checkboxSquareBackground, GREEN_ACCENT);
                put(colors, Theme.key_windowBackgroundWhiteBlueText4, GREEN_BAR);
                put(colors, Theme.key_windowBackgroundWhiteValueText, GREEN_BAR);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void put(SparseIntArray colors, int key, int value) {
        colors.put(key, value);
    }
}
