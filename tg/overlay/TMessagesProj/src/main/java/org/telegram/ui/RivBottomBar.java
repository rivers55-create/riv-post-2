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

    public static final int STYLE_CLASSIC = 0;
    public static final int STYLE_FLOATING = 1;
    public static final int STYLE_ICONS = 2;

    public static int style() {
        return org.telegram.messenger.RivPrefs.i(org.telegram.messenger.RivPrefs.LAYOUT_STYLE, STYLE_CLASSIC);
    }

    private static boolean rounded() {
        return org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.BAR_ROUNDED, true);
    }

    /** The bar's own height in dp for the chosen look. */
    private static int barHeightDp() {
        return style() == STYLE_ICONS ? 48 : HEIGHT_DP;
    }

    /** Space the bar takes at the bottom of a screen (bar + floating gap), in dp. */
    public static int heightDp() {
        return barHeightDp() + (style() == STYLE_FLOATING ? 12 : 0);
    }

    /** Bar background: custom "Navigation bar" colour if set, else the theme's. */
    private static int barColor() {
        int c = org.telegram.messenger.RivPrefs.i(org.telegram.messenger.RivTheme.NAV_COLOR, 0);
        return c != 0 ? c : Theme.getColor(Theme.key_windowBackgroundWhite);
    }

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
        setGravity(Gravity.CENTER_VERTICAL);
        if (style() == STYLE_FLOATING) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(barColor());
            bg.setCornerRadius(AndroidUtilities.dp(rounded() ? 28 : 8));
            setBackground(bg);
            setElevation(AndroidUtilities.dp(6));
        } else {
            setBackgroundColor(barColor());
        }

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
            if (style() == STYLE_ICONS) item.label.setVisibility(GONE);
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
                if (rounded()) {
                    pill.setShape(GradientDrawable.OVAL);
                } else {
                    pill.setCornerRadius(AndroidUtilities.dp(6));
                }
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

        // Riv Post: bottomMarginPx is almost always 0 by the time the caller reads it (it comes
        // from AndroidUtilities.navigationBarHeight, which nothing in real Telegram ever actually
        // sets), so the bar and mini-player used to sit flush against the very bottom of the
        // screen, under the phone's own nav bar. AndroidUtilities.getViewInset() reads the real,
        // live inset straight off the window instead, and the layout listener below keeps it
        // correct if the inset changes later (rotation, switching gesture nav on/off).
        final int[] navInset = {Math.max(bottomMarginPx, AndroidUtilities.getViewInset(contentView))};

        RivMiniPlayerView miniPlayer = new RivMiniPlayerView(context);
        miniPlayer.setVisibility(android.view.View.GONE);
        miniPlayer.setOnOpenPlayer(() -> fragment.presentFragment(new RivMusicPlayerActivity()));
        contentView.addView(miniPlayer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM, 0, 0, 0, navInset[0] + AndroidUtilities.dp(heightDp())));
        miniPlayer.refresh();

        RivBottomBar bar = new RivBottomBar(context);
        bar.setActiveTab(activeTab);
        bar.setOnTabSelected(tab -> {
            if (tab == activeTab) return;
            if (tab == TAB_CHATS) {
                goToChats(fragment);
                return;
            }
            // Riv Post: when switching between two tab pages (e.g. Music -> Videos), the new page
            // replaces the current one instead of piling up, so Back always lands on Chats and the
            // bar stays put on every page.
            boolean replace = activeTab != TAB_CHATS;
            BaseFragment target;
            if (tab == TAB_CONTACTS) {
                android.os.Bundle args = new android.os.Bundle();
                args.putBoolean("needPhonebook", true);
                args.putBoolean("rivTab", true);
                target = new ContactsActivity(args);
            } else if (tab == TAB_CALLS) {
                target = new RivCallsActivity();
            } else if (tab == TAB_MUSIC) {
                target = new RivMusicActivity();
            } else if (tab == TAB_VIDEOS) {
                target = new RivVideosActivity();
            } else {
                android.os.Bundle args = new android.os.Bundle();
                args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId());
                args.putBoolean("my_profile", true);
                args.putBoolean("rivTab", true);
                target = new ProfileActivity(args);
            }
            fragment.presentFragment(target, replace);
        });
        final int side = style() == STYLE_FLOATING ? AndroidUtilities.dp(12) : 0;
        final int gap = style() == STYLE_FLOATING ? AndroidUtilities.dp(12) : 0;
        contentView.addView(bar, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, barHeightDp(), Gravity.BOTTOM, side, 0, side, navInset[0] + gap));

        // Riv Post: re-checks the real inset on every layout pass and re-applies both margins
        // when it changes, so this stays correct through rotation or a nav-mode change without
        // needing every screen that calls attach() to wire up its own insets callback.
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int inset = Math.max(bottomMarginPx, AndroidUtilities.getViewInset(contentView));
            if (inset == navInset[0]) return;
            navInset[0] = inset;
            FrameLayout.LayoutParams barLp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            if (barLp.bottomMargin != inset + gap) {
                barLp.bottomMargin = inset + gap;
                bar.setLayoutParams(barLp);
            }
            FrameLayout.LayoutParams playerLp = (FrameLayout.LayoutParams) miniPlayer.getLayoutParams();
            int playerMargin = inset + AndroidUtilities.dp(heightDp());
            if (playerLp.bottomMargin != playerMargin) {
                playerLp.bottomMargin = playerMargin;
                miniPlayer.setLayoutParams(playerLp);
            }
        });

        return new Docked(bar, miniPlayer);
    }

    /**
     * Returns to the root Chats screen regardless of how many Riv screens were pushed on top
     * of it (e.g. Music -> Videos -> tap Chats should land back on Chats, not just go back one
     * step). Finds the deepest DialogsActivity already on the stack and drops everything strictly
     * between it and the current fragment, then closes the current fragment normally so
     * ActionBarLayout's own closeLastFragment() transitions onto whatever is now directly beneath
     * it on the stack -- which, once everything in between has been dropped, is root itself.
     *
     * Important: the fragment calling this (the current screen) is deliberately NOT included in
     * the forced-removal pass. ActionBarLayout.closeLastFragment() reads fragmentsStack.get(size-1)
     * as "the fragment to close" and fragmentsStack.get(size-2) as "the fragment to reveal"; if the
     * current fragment were force-removed first, the stack's top would already be root with nothing
     * below it by the time closeLastFragment() ran, so it would hit the no-previous-fragment branch
     * instead of the normal transition-to-previous-fragment branch and root would never actually be
     * shown. Removing only the fragments strictly above the current one, then letting
     * closeLastFragment() close the current one normally, is what makes root end up revealed.
     * If none is found (shouldn't normally happen, since every one of these screens is reached
     * from a Chats-rooted stack), falls back to a plain finishFragment().
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
        // Everything strictly between root and the current fragment (exclusive of both ends).
        java.util.List<BaseFragment> between = new java.util.ArrayList<>();
        boolean pastRoot = false;
        for (BaseFragment f : stack) {
            if (f == root) {
                pastRoot = true;
                continue;
            }
            if (f == fragment) {
                break;
            }
            if (pastRoot) between.add(f);
        }
        for (int i = between.size() - 1; i >= 0; i--) {
            layout.removeFragmentFromStack(between.get(i), true);
        }
        // Now the stack is [..., root, fragment] with nothing in between, so closing the last
        // fragment (the current one) correctly reveals root.
        layout.closeLastFragment();
    }
}
