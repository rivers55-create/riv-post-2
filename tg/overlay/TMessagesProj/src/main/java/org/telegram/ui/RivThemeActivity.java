package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivTheme;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

/**
 * Riv Post: theme colours, like RTP's theme screen: accent swatches, your own colour, "colour by
 * current track" for the music player, and custom colours for background, cards and menus, text,
 * the bottom bar and the mini player. Every colour can go back to "Automatic".
 */
public class RivThemeActivity extends RivFragment {

    private LinearLayout layout;

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivThemeColors));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;
        ScrollView scroll = new ScrollView(context);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        frame.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        rebuild(context);
        return fragmentView;
    }

    private void rebuild(Context context) {
        layout.removeAllViews();
        int accent = RivPrefs.i(RivPrefs.ACCENT, 0);

        layout.addView(header(context, LocaleController.getString(R.string.RivAccentColor)));
        HorizontalScrollView hs = new HorizontalScrollView(context);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        hs.addView(row);
        row.addView(swatch(context, 0, accent == 0, v -> setAccent(context, 0)), LayoutHelper.createLinear(42, 42, 0, 0, 10, 0));
        for (int c : RivTheme.SWATCHES) {
            row.addView(swatch(context, c, accent == c, v -> setAccent(context, c)), LayoutHelper.createLinear(42, 42, 0, 0, 10, 0));
        }
        layout.addView(hs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        layout.addView(colorRow(context, LocaleController.getString(R.string.RivCustomColor), LocaleController.getString(R.string.RivCustomColorInfo), accent, v -> pickColor(context, LocaleController.getString(R.string.RivCustomColor), accent, c -> setAccent(context, c))));

        TextCheckCell byTrack = new TextCheckCell(context);
        byTrack.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        byTrack.setTextAndValueAndCheck(LocaleController.getString(R.string.RivColorByTrack), LocaleController.getString(R.string.RivColorByTrackInfo), RivPrefs.b(RivPrefs.ACCENT_FROM_COVER, true), true, false);
        byTrack.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(RivPrefs.ACCENT_FROM_COVER, true);
            RivPrefs.set(RivPrefs.ACCENT_FROM_COVER, now);
            byTrack.setChecked(now);
        });
        layout.addView(byTrack);

        layout.addView(header(context, LocaleController.getString(R.string.RivCustomColors)));
        addCustom(context, R.string.RivColorBackground, RivTheme.BG_COLOR, true);
        addCustom(context, R.string.RivColorCards, RivTheme.CARD_COLOR, true);
        addCustom(context, R.string.RivColorText, RivTheme.TEXT_COLOR, true);
        addCustom(context, R.string.RivColorNavBar, RivTheme.NAV_COLOR, false);
        addCustom(context, R.string.RivColorMiniPlayer, RivTheme.MINI_COLOR, false);

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivThemeColorsFooter));
        layout.addView(info);
    }

    private void addCustom(Context context, int titleRes, String key, boolean themeWide) {
        int value = RivPrefs.i(key, 0);
        String title = LocaleController.getString(titleRes);
        layout.addView(colorRow(context, title, null, value, v -> pickColor(context, title, value, c -> {
            RivPrefs.set(key, c);
            if (themeWide) Theme.refreshThemeColors();
            rebuild(context);
        })));
    }

    private void setAccent(Context context, int c) {
        RivPrefs.set(RivPrefs.ACCENT, c);
        Theme.refreshThemeColors();
        rebuild(context);
    }

    private View swatch(Context context, int color, boolean selected, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(context);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (color == 0) {
            d.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            d.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            TextView a = new TextView(context);
            a.setText("A");
            a.setTypeface(AndroidUtilities.bold());
            a.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            a.setGravity(Gravity.CENTER);
            f.addView(a, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            d.setColor(color);
        }
        if (selected) d.setStroke(AndroidUtilities.dp(3), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        f.setBackground(d);
        if (selected && color != 0) {
            RivIcon check = new RivIcon(context);
            check.setKind(RivIcon.CHECK);
            check.setColor(0xFFFFFFFF);
            f.addView(check, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        }
        if (l != null) f.setOnClickListener(l);
        return f;
    }

    /** A row with a title, an optional line under it, and a round colour dot ("A" = automatic). */
    private View colorRow(Context context, String title, String sub, int color, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        col.addView(t);
        TextView s = new TextView(context);
        s.setText(sub != null ? sub : (color == 0 ? LocaleController.getString(R.string.RivAutomatic) : hex(color)));
        s.setTextSize(13);
        s.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        col.addView(s);
        row.addView(swatch(context, color, false, null), LayoutHelper.createLinear(34, 34, 12, 0, 0, 0));
        row.setOnClickListener(l);
        return row;
    }

    private static String hex(int c) {
        return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
    }

    private interface ColorCallback {
        void onColor(int color);
    }

    /** Swatch grid + hex box + "Automatic". */
    private void pickColor(Context context, String title, int current, ColorCallback cb) {
        if (getParentActivity() == null) return;
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(6), AndroidUtilities.dp(20), 0);
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(7);
        col.addView(grid, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        EditText hexBox = new EditText(context);
        hexBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hexBox.setHint("#RRGGBB");
        hexBox.setText(current != 0 ? hex(current) : "");
        hexBox.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        int[] extra = {0xFF000000, 0xFF121212, 0xFF1E1E1E, 0xFF2A2236, 0xFF3A1F1F, 0xFFFFFFFF, 0xFFF5F5F5};
        int[] all = new int[RivTheme.SWATCHES.length + extra.length];
        System.arraycopy(RivTheme.SWATCHES, 0, all, 0, RivTheme.SWATCHES.length);
        System.arraycopy(extra, 0, all, RivTheme.SWATCHES.length, extra.length);
        for (int c : all) {
            View sw = swatch(context, c, c == current, v -> hexBox.setText(hex(c)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = AndroidUtilities.dp(34);
            lp.height = AndroidUtilities.dp(34);
            lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            grid.addView(sw, lp);
        }
        col.addView(hexBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setView(col);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String txt = hexBox.getText() == null ? "" : hexBox.getText().toString().trim();
            if (txt.isEmpty()) {
                cb.onColor(0);
                return;
            }
            try {
                if (!txt.startsWith("#")) txt = "#" + txt;
                cb.onColor(Color.parseColor(txt) | 0xFF000000);
            } catch (Exception e) {
                android.widget.Toast.makeText(context, LocaleController.getString(R.string.RivBadColor), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        b.setNeutralButton(LocaleController.getString(R.string.RivAutomatic), (d, w) -> cb.onColor(0));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private HeaderCell header(Context context, String text) {
        HeaderCell cell = new HeaderCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setText(text);
        return cell;
    }
}
