package org.telegram.ui;

import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;

import java.util.ArrayList;

/** Riv Post: choose which tabs the bottom bar shows and in what order. Chats and Profile always stay. Applies the next time a screen with the bar opens. */
public class RivBarEditorActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Bottom bar tabs", "تبويبات الشريط السفلي");
    }

    private static String nameOf(int tab) {
        switch (tab) {
            case RivBottomBar.TAB_CHATS:
                return RivT.t("Chats", "المحادثات");
            case RivBottomBar.TAB_CONTACTS:
                return RivT.t("Contacts", "جهات الاتصال");
            case RivBottomBar.TAB_CALLS:
                return RivT.t("Calls", "المكالمات");
            case RivBottomBar.TAB_MUSIC:
                return RivT.t("Music", "الموسيقى");
            case RivBottomBar.TAB_VIDEOS:
                return RivT.t("Videos", "الفيديوهات");
            default:
                return RivT.t("Profile", "الملف الشخصي");
        }
    }

    @Override
    protected void build() {
        final int[] order = RivBottomBar.tabOrder();
        for (int i = 0; i < order.length; i++) {
            final int tab = order[i];
            final int pos = i;
            boolean fixed = tab == RivBottomBar.TAB_CHATS || tab == RivBottomBar.TAB_PROFILE;
            boolean hidden = RivBottomBar.isHidden(tab);
            addCell((hidden ? "○  " : "●  ") + nameOf(tab), hidden ? RivT.t("hidden", "مخفي") : "", i != order.length - 1, () -> {
                ArrayList<CharSequence> names = new ArrayList<>();
                ArrayList<Runnable> acts = new ArrayList<>();
                if (!fixed) {
                    names.add(hidden ? RivT.t("Show", "إظهار") : RivT.t("Hide", "إخفاء"));
                    acts.add(() -> {
                        setHidden(tab, !hidden);
                        rebuild();
                    });
                }
                if (pos > 0) {
                    names.add(RivT.t("Move earlier", "تحريك للأمام"));
                    acts.add(() -> {
                        move(order, pos, pos - 1);
                        rebuild();
                    });
                }
                if (pos < order.length - 1) {
                    names.add(RivT.t("Move later", "تحريك للخلف"));
                    acts.add(() -> {
                        move(order, pos, pos + 1);
                        rebuild();
                    });
                }
                choose(nameOf(tab), names.toArray(new CharSequence[0]), w -> acts.get(w).run());
            }, null);
        }
        addAction(RivT.t("Reset to default", "إعادة للافتراضي"), false, () -> {
            RivPrefs.set(RivPrefs.BAR_ORDER, "");
            RivPrefs.set(RivPrefs.BAR_HIDDEN, "");
            rebuild();
        });
        addInfo(RivT.t("Tap a tab to hide it or move it. Chats and Profile always stay. The bar updates the next time you open a screen that has it.",
                "اضغط على تبويب لإخفائه أو تحريكه. المحادثات والملف الشخصي يبقيان دائماً. يتحدث الشريط عند فتح شاشة جديدة تحتويه."));
    }

    private static void move(int[] order, int from, int to) {
        int t = order[from];
        order[from] = order[to];
        order[to] = t;
        StringBuilder sb = new StringBuilder();
        for (int x : order) {
            if (sb.length() > 0) sb.append(',');
            sb.append(x);
        }
        RivPrefs.set(RivPrefs.BAR_ORDER, sb.toString());
    }

    private static void setHidden(int tab, boolean hide) {
        ArrayList<String> list = new ArrayList<>();
        String s = RivPrefs.s(RivPrefs.BAR_HIDDEN, "");
        if (s != null && !s.isEmpty()) for (String p : s.split(",")) if (!p.trim().isEmpty()) list.add(p.trim());
        list.remove(String.valueOf(tab));
        if (hide) list.add(String.valueOf(tab));
        RivPrefs.set(RivPrefs.BAR_HIDDEN, android.text.TextUtils.join(",", list));
    }
}
