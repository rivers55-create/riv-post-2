package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Riv Post: "Quick looks", like RTP's: pick how the bottom bar looks (Classic full-width,
 * Floating pill, or Icons only) and the design style (Flat or Rounded), with small previews.
 * Tapping an option applies it straight away.
 */
public class RivLooksActivity extends RivFragment {

    private LinearLayout layout;

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivQuickLooks));
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
        int style = RivBottomBar.style();
        boolean rounded = RivPrefs.b(RivPrefs.BAR_ROUNDED, true);

        layout.addView(header(context, LocaleController.getString(R.string.RivLayout)));
        LinearLayout row = cardsRow(context);
        row.addView(card(context, LocaleController.getString(R.string.RivLayoutClassic), new Preview(context, RivBottomBar.STYLE_CLASSIC, rounded), style == RivBottomBar.STYLE_CLASSIC, v -> setStyle(context, RivBottomBar.STYLE_CLASSIC)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
        row.addView(card(context, LocaleController.getString(R.string.RivLayoutFloating), new Preview(context, RivBottomBar.STYLE_FLOATING, rounded), style == RivBottomBar.STYLE_FLOATING, v -> setStyle(context, RivBottomBar.STYLE_FLOATING)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 4, 0));
        row.addView(card(context, LocaleController.getString(R.string.RivLayoutIcons), new Preview(context, RivBottomBar.STYLE_ICONS, rounded), style == RivBottomBar.STYLE_ICONS, v -> setStyle(context, RivBottomBar.STYLE_ICONS)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));
        layout.addView(row);

        layout.addView(header(context, LocaleController.getString(R.string.RivDesignStyle)));
        LinearLayout row2 = cardsRow(context);
        row2.addView(card(context, LocaleController.getString(R.string.RivStyleFlat), new Preview(context, style, false), !rounded, v -> setRounded(context, false)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
        row2.addView(card(context, LocaleController.getString(R.string.RivStyleRounded), new Preview(context, style, true), rounded, v -> setRounded(context, true)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));
        row2.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f, 8, 0, 0, 0));
        layout.addView(row2);

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivQuickLooksFooter));
        layout.addView(info);
    }

    private void setStyle(Context context, int style) {
        RivPrefs.set(RivPrefs.LAYOUT_STYLE, style);
        rebuild(context);
    }

    private void setRounded(Context context, boolean rounded) {
        RivPrefs.set(RivPrefs.BAR_ROUNDED, rounded);
        rebuild(context);
    }

    private LinearLayout cardsRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        return row;
    }

    private View card(Context context, String label, View preview, boolean selected, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bg.setStroke(AndroidUtilities.dp(selected ? 2 : 1), selected ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : 0x22888888);
        col.setBackground(bg);
        col.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 130));
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(selected ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        col.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        col.setOnClickListener(l);
        return col;
    }

    private HeaderCell header(Context context, String text) {
        HeaderCell cell = new HeaderCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setText(text);
        return cell;
    }

    /** A tiny phone sketch: a few list rows and the bottom bar in the given look. */
    private static class Preview extends View {
        private final int style;
        private final boolean rounded;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Preview(Context context, int style, boolean rounded) {
            super(context);
            this.style = style;
            this.rounded = rounded;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float pw = Math.min(w * 0.8f, h * 0.55f);
            float left = (w - pw) / 2f, top = AndroidUtilities.dp(2), bottom = h - AndroidUtilities.dp(2);
            // phone
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            r.set(left, top, left + pw, bottom);
            canvas.drawRoundRect(r, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1));
            paint.setColor(0x55888888);
            canvas.drawRoundRect(r, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
            // rows
            paint.setStyle(Paint.Style.FILL);
            float y = top + AndroidUtilities.dp(10);
            float rowH = AndroidUtilities.dp(9);
            float radius = rounded ? rowH / 2f : AndroidUtilities.dp(1.5f);
            for (int i = 0; i < 5; i++) {
                paint.setColor(0x33888888);
                canvas.drawCircle(left + AndroidUtilities.dp(10), y + rowH / 2f, rowH / 2f, paint);
                r.set(left + AndroidUtilities.dp(18), y + rowH * 0.2f, left + pw - AndroidUtilities.dp(8), y + rowH * 0.8f);
                canvas.drawRoundRect(r, radius, radius, paint);
                y += rowH + AndroidUtilities.dp(6);
            }
            // bottom bar
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
            float barH = AndroidUtilities.dp(style == RivBottomBar.STYLE_ICONS ? 10 : 14);
            if (style == RivBottomBar.STYLE_FLOATING) {
                r.set(left + AndroidUtilities.dp(6), bottom - barH - AndroidUtilities.dp(6), left + pw - AndroidUtilities.dp(6), bottom - AndroidUtilities.dp(6));
                paint.setColor(0x55888888);
                float br = rounded ? barH / 2f : AndroidUtilities.dp(2);
                canvas.drawRoundRect(r, br, br, paint);
            } else {
                r.set(left + AndroidUtilities.dp(1), bottom - barH, left + pw - AndroidUtilities.dp(1), bottom - AndroidUtilities.dp(1));
                paint.setColor(0x55888888);
                canvas.drawRect(r, paint);
            }
            float cy = r.centerY();
            for (int i = 0; i < 4; i++) {
                float cx = r.left + (r.width() / 4f) * (i + 0.5f);
                paint.setColor(i == 0 ? accent : 0x99888888);
                float dot = AndroidUtilities.dp(2.5f);
                if (rounded) {
                    canvas.drawCircle(cx, cy, dot, paint);
                } else {
                    canvas.drawRect(cx - dot, cy - dot, cx + dot, cy + dot, paint);
                }
            }
        }
    }
}
