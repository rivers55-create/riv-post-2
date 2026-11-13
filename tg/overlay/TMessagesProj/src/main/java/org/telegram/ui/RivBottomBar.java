package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Riv Post: a persistent bottom tab bar (Chats / Contacts / Calls / Music / Videos / Profile).
 * Shown at the bottom of the Chats screen, the Music library and the Videos library alike --
 * each of those three fragments attaches its own instance via {@link #attach}, all reading the
 * same per-screen "which tab is this" value and all presenting the same target fragments, so
 * behaviour stays identical without duplicating the docking logic three times. Doesn't touch
 * Telegram's own real tab system (MainTabsActivity/GlassTabView) at all.
 *
 * The active tab's icon sits inside a filled accent-coloured circle (not just a tinted icon),
 * matching the old Riv Post app's look. A RivMiniPlayerView, when a song is playing, docks
 * directly above this bar on every screen that attaches it.
 */
public class RivBottomBar extends LinearLayout {

    public static final int TAB_CHATS = 0;
    public static final int TAB_CONTACTS = 1;
    public static final int TAB_CALLS = 2;
    public static final int TAB_MUSIC = 3;
    public static final int TAB_VIDEOS = 4;
    public static final int TAB_PROFILE = 5;

    public static final int HEIGHT_DP = 56;

    private static final int[] ICON_KINDS = {
            RivIcon.TAB_CHATS, RivIcon.TAB_CONTACTS, RivIcon.TAB_CALLS,
            RivIcon.TAB_MUSIC, RivIcon.TAB_VIDEOS, RivIcon.TAB_PROFILE,
    };

    private final Item[] items = new Item[6];
    private int activeTab = TAB_CHATS;
    private OnTabSelected listener;

    public interface OnTabSelected {
        void onTabSelected(int tab);
    }

    private static class Item extends LinearLayout {
        final FrameLayout iconWrap;
        final RivIcon icon;
        final TextView label;

        Item(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(4));

            iconWrap = new FrameLayout(context);
            addView(iconWrap, LayoutHelper.createLinear(34, 28));

            icon = new RivIcon(context);
            iconWrap.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

            label = new TextView(context);
            label.setTextSize(10.5f);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(0, AndroidUtilities.dp(2), 0, 0);
            addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    public RivBottomBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        String[] labels = {
                LocaleController.getString(R.string.RivTabChats),
                LocaleController.getString(R.string.RivTabContacts),
                LocaleController.getString(R.string.RivTabCalls),
                LocaleController.getString(R.string.RivTabMusicShort),
                LocaleController.getString(R.string.RivTabVideosShort),
                LocaleController.getString(R.string.RivTabProfile),
        };

        for (int i = 0; i < items.length; i++) {
            Item item = new Item(context);
            item.icon.setKind(ICON_KINDS[i]);
            item.label.setText(labels[i]);
            items[i] = item;
            final int tab = i;
            item.setOnClickListener(v -> {
                if (listener != null) listener.onTabSelected(tab);
            });
            addView(item, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        }

        updateHighlight();
    }

    public void setOnTabSelected(OnTabSelected l) {
        listener = l;
    }

    /** Which tab is drawn highlighted; does not by itself trigger navigation. */
    public void setActiveTab(int tab) {
        activeTab = tab;
        updateHighlight();
    }

    private void updateHighlight() {
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
        int inactive = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        for (int i = 0; i < items.length; i++) {
            boolean sel = i == activeTab;
            Item item = items[i];
            if (sel) {
                GradientDrawable pill = new GradientDrawable();
                pill.setShape(GradientDrawable.OVAL);
                pill.setColor(accent);
                item.iconWrap.setBackground(pill);
                item.icon.setColor(0xFFFFFFFF);
                item.label.setTextColor(accent);
            } else {
                item.iconWrap.setBackground(null);
                item.icon.setColor(inactive);
                item.label.setTextColor(inactive);
            }
        }
    }

    /** What {@link #attach} created, so the caller can keep its mini-player current. */
    public static class Docked {
        public final RivBottomBar bar;
        public final RivMiniPlayerView miniPlayer;

        Docked(RivBottomBar bar, RivMiniPlayerView miniPlayer) {
            this.bar = bar;
            this.miniPlayer = miniPlayer;
        }
    }

    /**
     * Docks a bar + mini-player pair at the bottom of {@code contentView}, above {@code bottomMarginPx}
     * of reserved space (e.g. the real Telegram tab bar height, or just the nav bar). Used identically
     * by DialogsActivity (via a hook), RivMusicActivity and RivVideosActivity so all three look and
     * behave the same. The caller should call {@code result.miniPlayer.refresh()} from its own
     * onResume and playback-change notifications, same as RivMusicActivity already did for its own
     * mini-player before this helper existed.
     */
    public static Docked attach(BaseFragment fragment, FrameLayout contentView, int activeTab, int bottomMarginPx) {
        Context context = contentView.getContext();

        RivMiniPlayerView miniPlayer = new RivMiniPlayerView(context);
        miniPlayer.setVisibility(android.view.View.GONE);
        miniPlayer.setOnOpenPlayer(() -> fragment.presentFragment(new RivMusicPlayerActivity()));
        contentView.addView(miniPlayer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM, 0, 0, 0, bottomMarginPx + AndroidUtilities.dp(HEIGHT_DP)));
        miniPlayer.refresh();

        RivBottomBar bar = new RivBottomBar(context);
        bar.setActiveTab(activeTab);
        bar.setOnTabSelected(tab -> {
            if (tab == activeTab) return;
            if (tab == TAB_CHATS) {
                goToChats(fragment);
            } else if (tab == TAB_CONTACTS) {
                android.os.Bundle args = new android.os.Bundle();
                args.putBoolean("needPhonebook", true);
                fragment.presentFragment(new ContactsActivity(args));
            } else if (tab == TAB_CALLS) {
                fragment.presentFragment(new CallLogActivity());
            } else if (tab == TAB_MUSIC) {
                fragment.presentFragment(new RivMusicActivity());
            } else if (tab == TAB_VIDEOS) {
                fragment.presentFragment(new RivVideosActivity());
            } else if (tab == TAB_PROFILE) {
                android.os.Bundle args = new android.os.Bundle();
                args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId());
                args.putBoolean("my_profile", true);
                fragment.presentFragment(new ProfileActivity(args));
            }
        });
        contentView.addView(bar, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, bottomMarginPx));

        return new Docked(bar, miniPlayer);
    }

    /**
     * Returns to the root Chats screen regardless of how many Riv screens were pushed on top
     * of it (e.g. Music -> Videos -> tap Chats should land back on Chats, not just go back one
     * step). Finds the deepest DialogsActivity already on the stack and drops everything above
     * it; if none is found (shouldn't normally happen, since every one of these screens is
     * reached from a Chats-rooted stack), falls back to a plain finishFragment().
     */
    private static void goToChats(BaseFragment fragment) {
        org.telegram.ui.ActionBar.INavigationLayout layout = fragment.getParentLayout();
        if (layout == null) {
            fragment.finishFragment();
            return;
        }
        java.util.List<BaseFragment> stack = layout.getFragmentStack();
        BaseFragment root = null;
        for (BaseFragment f : stack) {
            if (f instanceof DialogsActivity) {
                root = f;
                break;
            }
        }
        if (root == null) {
            fragment.finishFragment();
            return;
        }
        java.util.List<BaseFragment> above = new java.util.ArrayList<>();
        boolean pastRoot = false;
        for (BaseFragment f : stack) {
            if (f == root) {
                pastRoot = true;
                continue;
            }
            if (pastRoot) above.add(f);
        }
        for (int i = above.size() - 1; i >= 0; i--) {
            layout.removeFragmentFromStack(above.get(i), true);
        }
        layout.closeLastFragment();
    }
}
