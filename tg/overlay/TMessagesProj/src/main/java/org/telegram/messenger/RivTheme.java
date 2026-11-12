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
