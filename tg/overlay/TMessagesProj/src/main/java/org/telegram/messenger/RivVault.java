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
        if (decoyActive()) return !RivPrefs.inList(DECOY_IDS, dialogId);
        return !revealed && !ids().isEmpty() && ids().contains(dialogId);
    }

    // ---- decoy view: a second PIN that shows only the chats you picked, nothing else
    private static final String DECOY_PIN = "decoyPinHash";
    public static final String DECOY_IDS = "decoyChats";
    private static final String DECOY_ON = "decoyOn";

    public static boolean decoyActive() {
        return RivPrefs.b(DECOY_ON, false);
    }

    public static boolean hasDecoyPin() {
        return !RivPrefs.s(DECOY_PIN, "").isEmpty();
    }

    /** False when the new PIN equals the real Vault PIN. */
    public static boolean setDecoyPin(String pin) {
        if (hasPin()) {
            // typing the decoy PIN must never pass through the real PIN on the way
            for (int n = 1; n <= pin.length(); n++) {
                if (checkPin(pin.substring(0, n))) return false;
            }
        }
        RivPrefs.set(DECOY_PIN, hash(pin));
        return true;
    }

    public static void clearDecoy() {
        RivPrefs.set(DECOY_PIN, "");
        RivPrefs.set(DECOY_ON, false);
    }

    /**
     * Typing "#" and a PIN into the chat-list search box: your Vault PIN shows or hides the Vault
     * chats (and leaves the decoy view); your decoy PIN switches to the decoy view.
     * Returns true when the text was one of the PINs.
     */
    public static boolean handleSearchCode(String text) {
        try {
            if (text == null || text.length() < 3 || text.charAt(0) != '#') return false;
            String pin = text.substring(1).trim();
            if (hasPin() && checkPin(pin)) {
                if (decoyActive()) {
                    RivPrefs.set(DECOY_ON, false);
                    revealed = true;
                } else {
                    revealed = !revealed;
                }
                return true;
            }
            if (hasDecoyPin() && hash(pin).equals(RivPrefs.s(DECOY_PIN, ""))) {
                RivPrefs.set(DECOY_ON, true);
                revealed = false;
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
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
            if (decoyActive()) {
                ArrayList<TLRPC.Dialog> only = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    TLRPC.Dialog d = list.get(i);
                    if (d != null && !(d instanceof TLRPC.TL_dialogFolder) && RivPrefs.inList(DECOY_IDS, d.id)) only.add(d);
                }
                return only;
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
