package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;

/**
 * Riv Post: the ghost button in the Chats top bar (like the old Riv Post). Tap to turn ghost
 * mode on or off: no read ticks, no "typing...", never shown online. Faded when off.
 */
public class RivGhostButton {

    public static final int MENU_ID = -4747;

    /** Draws the ghost emoji, full strength when ghost mode is on, faded when off. */
    static class GhostDrawable extends Drawable {
        private boolean on;
        String emoji = "\uD83D\uDC7B";

        GhostDrawable(String emoji) {
            this();
            this.emoji = emoji;
        }

        GhostDrawable() {
        }

        void setOn(boolean value) {
            on = value;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int kind = RivLineIcon.kindOf(emoji);
            int color = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_actionBarDefaultIcon);
            RivLineIcon.draw(canvas, kind, b.exactCenterX(), b.exactCenterY(), AndroidUtilities.dp(24), color, on ? 255 : 120, on);
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(26);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(26);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // keep the emoji's own colours; the action bar tints icons otherwise
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Adds the button to the given menu if the Settings switch allows it. */
    public static void add(ActionBarMenu menu) {
        if (menu == null || !RivPrefs.b(RivPrefs.GHOST_BUTTON, true)) return;
        GhostDrawable icon = new GhostDrawable();
        icon.setOn(RivPrefs.ghost());
        ActionBarMenuItem item = menu.addItem(MENU_ID, icon);
        item.setContentDescription(LocaleController.getString(R.string.RivGhostMode));
        item.setOnClickListener(v -> {
            boolean on = !RivPrefs.ghost();
            RivPrefs.setGhost(on);
            icon.setOn(on);
            Toast.makeText(v.getContext(), LocaleController.getString(on ? R.string.RivGhostOn : R.string.RivGhostOff), Toast.LENGTH_SHORT).show();
        });
    }
}
