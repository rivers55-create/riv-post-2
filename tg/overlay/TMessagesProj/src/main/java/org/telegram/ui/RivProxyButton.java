package org.telegram.ui;

import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Riv Post: a shield button in the Chats top bar, next to the ghost. Tap to turn the proxy on or
 * off; full strength when on, faded when off. With no proxy saved yet it opens the proxy screen.
 */
public class RivProxyButton {

    public static final int MENU_ID = -4748;

    public static void add(BaseFragment fragment, ActionBarMenu menu) {
        if (menu == null || !RivPrefs.b(RivPrefs.PROXY_BUTTON, true)) return;
        RivGhostButton.GhostDrawable icon = new RivGhostButton.GhostDrawable("🛡️");
        icon.setOn(RivProxyImport.isProxyOn());
        ActionBarMenuItem item = menu.addItem(MENU_ID, icon);
        item.setContentDescription(LocaleController.getString(R.string.RivProxyButton));
        item.setOnClickListener(v -> {
            Boolean on = RivProxyImport.toggleProxy(fragment);
            if (on == null) return;
            icon.setOn(on);
            Toast.makeText(v.getContext(), LocaleController.getString(on ? R.string.RivProxyOn : R.string.RivProxyOff), Toast.LENGTH_SHORT).show();
        });
    }
}
