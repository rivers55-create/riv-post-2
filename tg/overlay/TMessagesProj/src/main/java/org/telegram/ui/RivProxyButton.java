package org.telegram.ui;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

/**
 * Riv Post: a shield button in the Chats top bar, next to the ghost. Tap it for a small menu, like
 * the three-dot one: connect to a proxy you pick, switch to the fastest one, or turn the proxy off
 * (that row only shows while it is on). The shield has a tick when a proxy is connected.
 */
public class RivProxyButton {

    public static final int MENU_ID = -4748;

    public static void add(BaseFragment fragment, ActionBarMenu menu) {
        if (menu == null || !RivPrefs.b(RivPrefs.PROXY_BUTTON, true)) return;
        final RivGhostButton.GhostDrawable icon = new RivGhostButton.GhostDrawable("🛡️");
        icon.setOn(RivProxyImport.isProxyOn());
        final ActionBarMenuItem item = menu.addItem(MENU_ID, icon);
        item.setContentDescription(LocaleController.getString(R.string.RivProxyButton));

        final ActionBarMenuSubItem connect = item.addSubItem(1, R.drawable.msg_secret, RivT.t("Connect to a proxy", "الاتصال ببروكسي"));
        final ActionBarMenuSubItem fastest = item.addSubItem(2, R.drawable.msg_speed, RivT.t("Switch to fastest proxy", "التبديل لأسرع بروكسي"));
        final ActionBarMenuSubItem off = item.addSubItem(3, R.drawable.msg_disable, RivT.t("Turn proxy off", "إيقاف البروكسي"));

        connect.setOnClickListener(v -> {
            item.toggleSubMenu();
            pickProxy(fragment, icon);
        });
        fastest.setOnClickListener(v -> {
            item.toggleSubMenu();
            switchToFastest(fragment, icon);
        });
        off.setOnClickListener(v -> {
            item.toggleSubMenu();
            Boolean on = RivProxyImport.toggleProxy(fragment);
            if (on == null) return;
            icon.setOn(on);
            Toast.makeText(v.getContext(), LocaleController.getString(on ? R.string.RivProxyOn : R.string.RivProxyOff), Toast.LENGTH_SHORT).show();
        });
        item.setSubMenuDelegate(new ActionBarMenuItem.ActionBarSubMenuItemDelegate() {
            @Override
            public void onShowSubMenu() {
                SharedConfig.loadProxyList();
                boolean on = RivProxyImport.isProxyOn();
                icon.setOn(on);
                off.setVisibility(on ? View.VISIBLE : View.GONE);
                SharedConfig.ProxyInfo cur = SharedConfig.currentProxy;
                if (on && cur != null) {
                    connect.setSubtext(RivT.t("Connected: ", "متصل: ") + RivProxyImport.label(cur, cur.settings.getAddress() + ":" + cur.settings.getPort()));
                } else {
                    connect.setSubtext(SharedConfig.proxyList.isEmpty() ? RivT.t("No proxies saved", "لا توجد بروكسيات محفوظة") : RivT.t("Off", "متوقف"));
                }
            }

            @Override
            public void onHideSubMenu() {
            }
        });
    }

    private static void switchToFastest(BaseFragment fragment, RivGhostButton.GhostDrawable icon) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        SharedConfig.loadProxyList();
        if (SharedConfig.proxyList.isEmpty()) {
            fragment.presentFragment(new ProxyListActivity());
            return;
        }
        Toast.makeText(activity, RivT.t("Testing proxies…", "جارٍ فحص البروكسيات…"), Toast.LENGTH_SHORT).show();
        RivProxyImport.connectFastestNow(fragment.getCurrentAccount(), best -> {
            icon.setOn(RivProxyImport.isProxyOn());
            Toast.makeText(activity, best == null ? RivT.t("No proxy answered.", "لم يستجب أي بروكسي.")
                    : RivT.t("Connected: ", "تم الاتصال: ") + RivProxyImport.label(best, best.settings.getAddress() + ":" + best.settings.getPort()) + " (" + best.ping + " ms)", Toast.LENGTH_LONG).show();
        });
    }

    private static void pickProxy(BaseFragment fragment, RivGhostButton.GhostDrawable icon) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        SharedConfig.loadProxyList();
        final ArrayList<SharedConfig.ProxyInfo> list = new ArrayList<>(SharedConfig.proxyList);
        if (list.isEmpty()) {
            fragment.presentFragment(new ProxyListActivity());
            return;
        }
        CharSequence[] names = new CharSequence[list.size()];
        for (int i = 0; i < names.length; i++) {
            SharedConfig.ProxyInfo p = list.get(i);
            String plain = p.settings.getAddress() + ":" + p.settings.getPort();
            String ping = p.availableCheckTime != 0 ? (p.available && p.ping > 0 ? "  ·  " + p.ping + " ms" : "  ·  " + RivT.t("no answer", "لا استجابة")) : "";
            names[i] = (p == SharedConfig.currentProxy && RivProxyImport.isProxyOn() ? "✓  " : "") + RivProxyImport.label(p, plain) + ping;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(RivT.t("Connect to a proxy", "الاتصال ببروكسي"));
        b.setItems(names, (d, which) -> {
            RivProxyImport.connectTo(list.get(which));
            icon.setOn(true);
            Toast.makeText(activity, LocaleController.getString(R.string.RivProxyOn), Toast.LENGTH_SHORT).show();
        });
        fragment.showDialog(b.create());
    }
}
