package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/** Riv Post: profile extras - full "last seen" date and time, and the person's ID. */
public class RivProfile {

    public static String decorateStatus(String status, TLRPC.User user) {
        try {
            if (user == null) return status;
            String out = status;
            if (RivPrefs.b(RivPrefs.EXACT_LAST_SEEN, true) && user.status instanceof TLRPC.TL_userStatusOffline && user.status.expires > 0) {
                // always the full form ("last seen 14 Nov at 23:10"), never the shortened one
                out = LocaleController.formatDateOnline(user.status.expires, null);
            }
            if (RivPrefs.b(RivPrefs.SHOW_USER_ID, true)) {
                out = (out == null || out.isEmpty() ? "" : out + "  ·  ") + "ID " + user.id;
            }
            return out;
        } catch (Throwable t) {
            return status;
        }
    }
}
