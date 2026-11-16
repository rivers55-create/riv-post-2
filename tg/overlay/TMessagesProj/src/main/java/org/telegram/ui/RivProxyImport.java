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

    // ------------------------------------------------------------------ fastest / clean up

    /** Riv Post: switch to the proxy with the best (lowest) measured ping. Returns false if none was checked yet. */
    public static boolean connectFastest() {
        SharedConfig.loadProxyList();
        SharedConfig.ProxyInfo best = null;
        for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
            if (!p.available || p.availableCheckTime == 0 || p.ping <= 0) continue;
            if (best == null || p.ping < best.ping) best = p;
        }
        if (best == null) return false;
        android.content.SharedPreferences.Editor editor = org.telegram.messenger.MessagesController.getGlobalMainSettings().edit();
        best.settings.toSharedPreferences(editor);
        editor.putBoolean("proxy_enabled", true);
        editor.commit();
        SharedConfig.currentProxy = best;
        org.telegram.tgnet.ConnectionsManager.setProxySettings(true, best.settings);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.proxySettingsChanged);
        return true;
    }

    /** Proxies whose last check failed (and aren't being checked right now). */
    public static ArrayList<SharedConfig.ProxyInfo> deadProxies() {
        ArrayList<SharedConfig.ProxyInfo> out = new ArrayList<>();
        for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
            if (!p.checking && p.availableCheckTime != 0 && !p.available && p != SharedConfig.currentProxy) out.add(p);
        }
        return out;
    }

    public static void askRemoveDead(BaseFragment fragment, Runnable onDone) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        ArrayList<SharedConfig.ProxyInfo> dead = deadProxies();
        if (dead.isEmpty()) {
            Toast.makeText(context, LocaleController.getString(R.string.RivProxyNoDead), Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(R.string.RivProxyRemoveDead));
        b.setMessage(LocaleController.formatString(R.string.RivProxyRemoveDeadAsk, dead.size()));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
            for (SharedConfig.ProxyInfo p : dead) SharedConfig.deleteProxy(p);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.proxySettingsChanged);
            if (onDone != null) onDone.run();
        });
        fragment.showDialog(b.create());
    }

    // ------------------------------------------------------------------ channel auto-import

    private static final long CHANNEL_EVERY_MS = 6 * 60 * 60 * 1000L;

    /** Riv Post: pick channels/groups that post proxies; their new links are added automatically. */
    public static void pickChannels(BaseFragment fragment, Runnable onDone) {
        ArrayList<Long> ids = new ArrayList<>();
        for (String s : org.telegram.messenger.RivPrefs.list(org.telegram.messenger.RivPrefs.PROXY_CHANNEL)) {
            try {
                ids.add(Long.parseLong(s));
            } catch (Throwable ignored) {
            }
        }
        RivChatSourcePicker picker = new RivChatSourcePicker(LocaleController.getString(R.string.RivProxyChannels),
                LocaleController.getString(R.string.RivProxyChannelsInfo), ids);
        picker.setDelegate(new RivChatSourcePicker.Delegate() {
            @Override
            public void onSourcesChanged(ArrayList<Long> sources) {
                ArrayList<String> out = new ArrayList<>();
                for (Long id : sources) out.add(String.valueOf(id));
                org.telegram.messenger.RivPrefs.setList(org.telegram.messenger.RivPrefs.PROXY_CHANNEL, out);
                org.telegram.messenger.RivPrefs.set(org.telegram.messenger.RivPrefs.PROXY_CHANNEL_LAST, "0");
                importFromChannels(fragment.getCurrentAccount(), true, onDone);
            }

            @Override
            public void onPreview(ArrayList<Long> sources) {
            }
        });
        fragment.presentFragment(picker);
    }

    public static int channelCount() {
        return org.telegram.messenger.RivPrefs.list(org.telegram.messenger.RivPrefs.PROXY_CHANNEL).size();
    }

    /** Reads the last 100 posts of each picked channel and adds every proxy link found. At most every 6 hours unless forced. */
    public static void importFromChannels(int account, boolean force, Runnable onDone) {
        ArrayList<String> ids = org.telegram.messenger.RivPrefs.list(org.telegram.messenger.RivPrefs.PROXY_CHANNEL);
        if (ids.isEmpty()) return;
        long last = 0;
        try {
            last = Long.parseLong(org.telegram.messenger.RivPrefs.s(org.telegram.messenger.RivPrefs.PROXY_CHANNEL_LAST, "0"));
        } catch (Throwable ignored) {
        }
        if (!force && System.currentTimeMillis() - last < CHANNEL_EVERY_MS) return;
        org.telegram.messenger.RivPrefs.set(org.telegram.messenger.RivPrefs.PROXY_CHANNEL_LAST, String.valueOf(System.currentTimeMillis()));
        final int[] pending = {ids.size()};
        final int[] added = {0};
        for (String sid : ids) {
            long dialogId;
            try {
                dialogId = Long.parseLong(sid);
            } catch (Throwable t) {
                pending[0]--;
                continue;
            }
            org.telegram.tgnet.TLRPC.TL_messages_getHistory req = new org.telegram.tgnet.TLRPC.TL_messages_getHistory();
            req.peer = org.telegram.messenger.MessagesController.getInstance(account).getInputPeer(dialogId);
            req.limit = 100;
            org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof org.telegram.tgnet.TLRPC.messages_Messages) {
                    StringBuilder text = new StringBuilder();
                    for (org.telegram.tgnet.TLRPC.Message msg : ((org.telegram.tgnet.TLRPC.messages_Messages) response).messages) {
                        collectLinks(msg, text);
                    }
                    added[0] += addAll(text.toString())[0];
                }
                pending[0]--;
                if (pending[0] <= 0) {
                    if (added[0] > 0) {
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.proxySettingsChanged);
                        Context c = org.telegram.messenger.ApplicationLoader.applicationContext;
                        if (c != null) Toast.makeText(c, LocaleController.formatString(R.string.RivProxyChannelAdded, added[0]), Toast.LENGTH_SHORT).show();
                    }
                    if (onDone != null) onDone.run();
                }
            }), org.telegram.tgnet.ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    private static void collectLinks(org.telegram.tgnet.TLRPC.Message msg, StringBuilder out) {
        if (msg == null) return;
        if (msg.message != null) out.append(msg.message).append('\n');
        if (msg.entities != null) {
            for (org.telegram.tgnet.TLRPC.MessageEntity e : msg.entities) {
                if (e instanceof org.telegram.tgnet.TLRPC.TL_messageEntityTextUrl && e.url != null) out.append(e.url).append('\n');
            }
        }
        if (msg.reply_markup instanceof org.telegram.tgnet.TLRPC.TL_replyInlineMarkup) {
            for (org.telegram.tgnet.tl.TL_keyboard.KeyboardInlineButtonRow row : ((org.telegram.tgnet.TLRPC.TL_replyInlineMarkup) msg.reply_markup).rows) {
                for (org.telegram.tgnet.tl.TL_keyboard.KeyboardInlineButton b : row.buttons) {
                    if (b != null && b.type instanceof org.telegram.tgnet.tl.TL_keyboard.TL_inlineButtonTypeUrl) {
                        String u = ((org.telegram.tgnet.tl.TL_keyboard.TL_inlineButtonTypeUrl) b.type).url;
                        if (u != null) out.append(u).append('\n');
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ speed test / share / names

    /** Riv Post: forget the last check of every proxy so Telegram measures them all again (fastest first). */
    public static void recheckAll(java.util.List<SharedConfig.ProxyInfo> list) {
        for (SharedConfig.ProxyInfo p : list) {
            if (!p.checking) p.availableCheckTime = 0;
        }
    }

    /** Share (or copy) every proxy as links, one per paragraph. */
    public static void shareAll(Context context) {
        SharedConfig.loadProxyList();
        if (SharedConfig.proxyList.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (SharedConfig.ProxyInfo p : SharedConfig.proxyList) {
            String link = p.settings.getLink();
            if (link == null) continue;
            String name = nameOf(p);
            if (sb.length() > 0) sb.append("\n\n");
            if (name != null) sb.append(name).append('\n');
            sb.append(link);
        }
        android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());
        android.content.Intent chooser = android.content.Intent.createChooser(share, LocaleController.getString(R.string.RivProxyShareAll));
        chooser.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }

    private static String nameKey(SharedConfig.ProxyInfo p) {
        return "proxyName_" + p.settings.getAddress() + ":" + p.settings.getPort();
    }

    /** The name you gave this proxy, or null. */
    public static String nameOf(SharedConfig.ProxyInfo p) {
        if (p == null || p.settings == null) return null;
        String n = org.telegram.messenger.RivPrefs.s(nameKey(p), "");
        return n == null || n.trim().isEmpty() ? null : n.trim();
    }

    /** Text for a proxy row: "Name · host:port" when named. */
    public static String label(SharedConfig.ProxyInfo p, String plain) {
        String n = nameOf(p);
        return n == null ? plain : n + "  \u00B7  " + plain;
    }

    public static void askRename(BaseFragment fragment, SharedConfig.ProxyInfo p, Runnable onDone) {
        Context context = fragment.getParentActivity();
        if (context == null || p == null) return;
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setHint(LocaleController.getString(R.string.RivProxyNameHint));
        String old = nameOf(p);
        if (old != null) field.setText(old);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        LinearLayout box = new LinearLayout(context);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        box.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(R.string.RivProxyRename));
        b.setView(box);
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            org.telegram.messenger.RivPrefs.set(nameKey(p), field.getText() == null ? "" : field.getText().toString().trim());
            if (onDone != null) onDone.run();
        });
        fragment.showDialog(b.create());
    }

    // ------------------------------------------------------------------ on/off from the Chats page

    /** Turns the proxy on/off like the switch on the proxy screen. Returns the new state, or null if the proxy screen was opened instead. */
    public static Boolean toggleProxy(BaseFragment fragment) {
        android.content.SharedPreferences prefs = org.telegram.messenger.MessagesController.getGlobalMainSettings();
        SharedConfig.loadProxyList();
        if (SharedConfig.currentProxy == null) {
            if (SharedConfig.proxyList.isEmpty()) {
                fragment.presentFragment(new ProxyListActivity());
                return null;
            }
            SharedConfig.currentProxy = SharedConfig.proxyList.get(0);
            android.content.SharedPreferences.Editor e = prefs.edit();
            SharedConfig.currentProxy.settings.toSharedPreferences(e);
            e.commit();
        }
        boolean on = !prefs.getBoolean("proxy_enabled", false);
        prefs.edit().putBoolean("proxy_enabled", on).commit();
        lastApplied = null;
        applyForNetwork(true);
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.proxySettingsChanged);
        return on;
    }

    public static boolean isProxyOn() {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && SharedConfig.currentProxy != null;
    }

    // ------------------------------------------------------------------ auto-off on Wi-Fi

    private static boolean wifiWatching;

    /** Riv Post: with "Proxy only on mobile data" on, go direct on Wi-Fi and use the proxy on mobile data. */
    public static void startWifiWatch(Context context) {
        if (wifiWatching || context == null || android.os.Build.VERSION.SDK_INT < 24) return;
        wifiWatching = true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> applyForNetwork(false), 500);
                }

                @Override
                public void onCapabilitiesChanged(android.net.Network network, android.net.NetworkCapabilities caps) {
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> applyForNetwork(false), 500);
                }
            });
        } catch (Throwable ignored) {
        }
        applyForNetwork(false);
    }

    private static Boolean lastApplied;

    /** Tells Telegram's connection whether to use the proxy right now (doesn't change your saved setting). */
    public static void applyForNetwork(boolean force) {
        SharedConfig.ProxyInfo cur = SharedConfig.currentProxy;
        if (cur == null) return;
        if (!force && !org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.PROXY_MOBILE_ONLY, false)) return;
        boolean wanted = org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false);
        boolean onWifi = false;
        try {
            onWifi = org.telegram.messenger.ApplicationLoader.isConnectedToWiFi();
        } catch (Throwable ignored) {
        }
        boolean use = wanted && !(onWifi && org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.PROXY_MOBILE_ONLY, false));
        if (lastApplied != null && lastApplied == use) return;
        lastApplied = use;
        org.telegram.tgnet.ConnectionsManager.setProxySettings(use, cur.settings);
    }
}
