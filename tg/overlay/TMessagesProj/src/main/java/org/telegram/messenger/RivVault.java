package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Riv Post Vault: chats hidden behind a PIN.
 * Hidden chats disappear from every chat list (and pickers) until revealed.
 * Hiding a chat also mutes it, so nothing shows in notifications.
 */
public class RivVault {

    private static final String IDS = "vaultChats";
    private static final String PIN = "vaultPinHash";

    /** Revealed until the app restarts or the switch is turned off. */
    public static volatile boolean revealed = false;

    private static HashSet<Long> cache;

    public static HashSet<Long> ids() {
        if (cache == null) {
            HashSet<Long> set = new HashSet<>();
            String s = RivPrefs.s(IDS, "");
            if (s != null && !s.isEmpty()) {
                for (String part : s.split(",")) {
                    try {
                        set.add(Long.parseLong(part));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            cache = set;
        }
        return cache;
    }

    private static void store() {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        RivPrefs.set(IDS, sb.toString());
    }

    public static boolean isHidden(long dialogId) {
        return !revealed && !ids().isEmpty() && ids().contains(dialogId);
    }

    public static void hide(int account, long dialogId) {
        ids().add(dialogId);
        store();
        try {
            NotificationsController.getInstance(account).muteDialog(dialogId, 0, true);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void unhide(int account, long dialogId) {
        ids().remove(dialogId);
        store();
        try {
            NotificationsController.getInstance(account).muteDialog(dialogId, 0, false);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static boolean hasPin() {
        return !RivPrefs.s(PIN, "").isEmpty();
    }

    public static void setPin(String pin) {
        RivPrefs.set(PIN, hash(pin));
    }

    public static boolean checkPin(String pin) {
        return hash(pin).equals(RivPrefs.s(PIN, ""));
    }

    private static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("riv-vault:" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    /** Filters hidden chats out of a dialog list. Called from the chat list. */
    public static ArrayList<TLRPC.Dialog> filterDialogs(ArrayList<TLRPC.Dialog> list) {
        try {
            if (list == null) {
                return list;
            }
            boolean hideVault = !revealed && !ids().isEmpty();
            // Riv Post: "Hide ads" also hides the sponsored "Proxy sponsor" chat Telegram pins on
            // top, but only while you're not actually a member of it.
            boolean hideAds = RivPrefs.b(RivPrefs.HIDE_ADS, true);
            MessagesController mc = hideAds ? MessagesController.getInstance(UserConfig.selectedAccount) : null;
            if (!hideVault && !hideAds) {
                return list;
            }
            boolean any = false;
            for (int i = 0; i < list.size(); i++) {
                TLRPC.Dialog d = list.get(i);
                if (d != null && ((hideVault && ids().contains(d.id)) || (mc != null && mc.isPromoDialog(d.id, true)))) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return list;
            }
            ArrayList<TLRPC.Dialog> out = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                TLRPC.Dialog d = list.get(i);
                if (d == null || !((hideVault && ids().contains(d.id)) || (mc != null && mc.isPromoDialog(d.id, true)))) {
                    out.add(d);
                }
            }
            return out;
        } catch (Throwable e) {
            FileLog.e(e);
            return list;
        }
    }
}
