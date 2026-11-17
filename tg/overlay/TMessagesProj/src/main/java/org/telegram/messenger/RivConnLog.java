package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;

/** Riv Post: a log of when Telegram's connection changed (connecting, connected, waiting for network...), and whether a proxy was on. */
public final class RivConnLog implements NotificationCenter.NotificationCenterDelegate {

    private static final String KEY = "rivConnLog";
    private static final int MAX = 300;
    private static RivConnLog instance;
    private int lastState = -1;

    private RivConnLog() {
    }

    public static synchronized void start() {
        if (instance != null) return;
        instance = new RivConnLog();
        NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.didUpdateConnectionState);
    }

    public static String describe(int state) {
        switch (state) {
            case ConnectionsManager.ConnectionStateConnected:
                return RivT.t("Connected", "متصل");
            case ConnectionsManager.ConnectionStateConnecting:
                return RivT.t("Connecting…", "جارٍ الاتصال…");
            case ConnectionsManager.ConnectionStateConnectingToProxy:
                return RivT.t("Connecting to proxy…", "جارٍ الاتصال بالوكيل…");
            case ConnectionsManager.ConnectionStateWaitingForNetwork:
                return RivT.t("Waiting for network", "بانتظار الشبكة");
            case ConnectionsManager.ConnectionStateUpdating:
                return RivT.t("Updating", "جارٍ التحديث");
            default:
                return String.valueOf(state);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didUpdateConnectionState) return;
        try {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state == lastState) return;
            lastState = state;
            ArrayList<JSONObject> list = RivStore.load(KEY);
            JSONObject o = new JSONObject();
            o.put("time", System.currentTimeMillis());
            o.put("state", state);
            o.put("proxy", SharedConfig.isProxyEnabled());
            o.put("net", ApplicationLoader.isConnectedToWiFi() ? "wifi" : (ApplicationLoader.isNetworkOnline() ? "mobile" : "none"));
            list.add(0, o);
            while (list.size() > MAX) list.remove(list.size() - 1);
            RivStore.save(KEY, list);
        } catch (Throwable ignored) {
        }
    }

    public static ArrayList<JSONObject> all() {
        return RivStore.load(KEY);
    }

    public static void clear() {
        RivPrefs.set(KEY, "[]");
    }
}
