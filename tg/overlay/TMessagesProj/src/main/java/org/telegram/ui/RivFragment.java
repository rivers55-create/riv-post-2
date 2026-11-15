package org.telegram.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import java.util.WeakHashMap;

/**
 * Riv Post: shared base for every Riv screen, so none of them draws under the phone's own
 * status bar (top) or navigation bar (bottom).
 *
 * Why this exists: real Telegram only reports the phone's bar sizes to a screen when the screen
 * returns true from isSupportEdgeToEdge() (ActionBarLayout only wires onInsetsInternal in that
 * case), and only puts the SimpleActionBar header on screen when the screen hands over a list
 * through getListViewForSimpleGlass(). Riv screens did neither, so their header was never shown
 * and their content started at the very top of the window, under the clock and battery.
 *
 * Two modes, picked automatically after createView():
 *  - glass mode: the screen gave Telegram a list (getListViewForSimpleGlass), Telegram added the
 *    header itself and pads that list; the screen's own onInsets() handles anything else.
 *  - plain mode: no list was given, so the header is added here, and every content child of the
 *    root FrameLayout is pushed below the header and above the bottom bar / nav bar.
 */
public abstract class RivFragment extends BaseFragment {

    private final WeakHashMap<View, int[]> originalMargins = new WeakHashMap<>();
    private boolean plainMode;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected void onViewCreated(View view) {
        super.onViewCreated(view);
        if (view instanceof FrameLayout && (simpleActionBar == null || simpleActionBar.getParent() == null)) {
            // plain mode: no list was handed to Telegram, so nothing added the header or the room
            // for the phone's bars; do both here (screens without a header get only the bars).
            plainMode = true;
            if (simpleActionBar != null) {
                ((FrameLayout) view).addView(simpleActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
            }
        }
        applyRivInsets();
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyRivInsets());
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        applyRivInsets();
    }

    /** Height taken by the status bar plus the header. */
    protected int rivHeaderHeight() {
        return Math.max(mSystemInsets.top, AndroidUtilities.statusBarHeight) + (useSimpleActionBar() ? ActionBar.getCurrentActionBarHeight() : 0);
    }

    /** Height of the phone's own nav bar, read live. */
    protected int rivNavBarHeight() {
        int inset = mSystemInsets.bottom;
        if (inset <= 0 && fragmentView != null) {
            inset = AndroidUtilities.getViewInset(fragmentView);
        }
        return Math.max(0, inset);
    }

    /** Re-applies top/bottom room for every content child; safe to call any number of times. */
    protected void applyRivInsets() {
        if (!plainMode || !(fragmentView instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) fragmentView;
        boolean hasBar = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof RivBottomBar) {
                hasBar = true;
                break;
            }
        }
        int top = rivHeaderHeight();
        int bottom = rivNavBarHeight() + (hasBar ? AndroidUtilities.dp(RivBottomBar.heightDp()) : 0);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == simpleActionBar || child instanceof RivBottomBar || child instanceof RivMiniPlayerView) continue;
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (!(raw instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            int[] orig = originalMargins.get(child);
            if (orig == null) {
                orig = new int[]{lp.topMargin, lp.bottomMargin};
                originalMargins.put(child, orig);
            }
            boolean fill = lp.height == ViewGroup.LayoutParams.MATCH_PARENT;
            int vg = lp.gravity == -1 ? Gravity.TOP : (lp.gravity & Gravity.VERTICAL_GRAVITY_MASK);
            boolean bottomAligned = vg == Gravity.BOTTOM;
            boolean centered = vg == Gravity.CENTER_VERTICAL;
            int newTop = orig[0] + ((fill || (!bottomAligned && !centered)) ? top : 0);
            int newBottom = orig[1] + ((fill || bottomAligned) ? bottom : 0);
            if (lp.topMargin != newTop || lp.bottomMargin != newBottom) {
                lp.topMargin = newTop;
                lp.bottomMargin = newBottom;
                child.setLayoutParams(lp);
            }
        }
    }
}
