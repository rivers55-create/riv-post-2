package org.telegram.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;

/**
 * Riv Post: one-hand mode. Shrinks the whole app toward the bottom corner of the screen so the top
 * is within reach of your thumb. Tap the empty area to go back to full size.
 */
public final class RivOneHand {

    public static final String RIGHT = "oneHandRight";
    private static final float SCALE = 0.72f;
    private static View dim;

    private RivOneHand() {
    }

    public static boolean isOn() {
        return dim != null && dim.getParent() != null;
    }

    public static void toggle(Activity activity) {
        if (activity == null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout) || content.getChildCount() == 0) return;
        if (isOn()) {
            off((FrameLayout) content);
            return;
        }
        View root = content.getChildAt(0);
        int w = content.getWidth(), h = content.getHeight();
        boolean right = RivPrefs.b(RIGHT, true);
        root.setPivotX(right ? w : 0);
        root.setPivotY(h);
        root.animate().scaleX(SCALE).scaleY(SCALE).setDuration(220).start();
        FrameLayout d = new FrameLayout(activity);
        d.setBackgroundColor(0xFF0E0E0E);
        TextView hint = new TextView(activity);
        hint.setText(RivT.t("Tap here to go back to full size", "اضغط هنا للعودة إلى الحجم الكامل"));
        hint.setTextColor(Color.argb(160, 255, 255, 255));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        d.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((FrameLayout.LayoutParams) hint.getLayoutParams()).topMargin = AndroidUtilities.dp(120);
        d.setOnClickListener(v -> off((FrameLayout) content));
        ((FrameLayout) content).addView(d, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim = d;
    }

    private static void off(FrameLayout content) {
        try {
            if (dim != null) content.removeView(dim);
            dim = null;
            for (int i = 0; i < content.getChildCount(); i++) {
                content.getChildAt(i).animate().scaleX(1f).scaleY(1f).setDuration(200).start();
            }
        } catch (Throwable ignored) {
        }
    }
}
