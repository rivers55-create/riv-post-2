package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Riv Post: clean line icons for the Chats top bar (ghost, shield, toolbox), drawn in a 24-unit
 * box so they match Telegram's own thin icons and take the bar's icon colour.
 */
public final class RivLineIcon {

    public static final int GHOST = 0, SHIELD = 1, TOOLS = 2;

    private RivLineIcon() {
    }

    /** Which icon an emoji key stands for (the old buttons were named by emoji). */
    public static int kindOf(String key) {
        if (key != null && key.contains("🛡")) return SHIELD;
        if (key != null && key.contains("🧰")) return TOOLS;
        return GHOST;
    }

    /**
     * Draws the icon centred in a square of the given size.
     *
     * @param check for the shield: draw the tick (proxy connected)
     */
    public static void draw(Canvas canvas, int kind, float cx, float cy, float size, int color, int alpha, boolean check) {
        float s = size / 24f;
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(1.9f * s);
        stroke.setColor(color);
        stroke.setAlpha(alpha);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        fill.setAlpha(alpha);

        canvas.save();
        canvas.translate(cx - size / 2f, cy - size / 2f);
        canvas.scale(s, s);
        // the paints above already hold the scaled stroke width, so undo the canvas scale for it
        stroke.setStrokeWidth(1.9f);
        switch (kind) {
            case SHIELD: {
                Path p = new Path();
                p.moveTo(12, 3);
                p.lineTo(19.5f, 6);
                p.lineTo(19.5f, 11.5f);
                p.cubicTo(19.5f, 16, 16.5f, 19.5f, 12, 21);
                p.cubicTo(7.5f, 19.5f, 4.5f, 16, 4.5f, 11.5f);
                p.lineTo(4.5f, 6);
                p.close();
                canvas.drawPath(p, stroke);
                if (check) {
                    Path c = new Path();
                    c.moveTo(8.6f, 12);
                    c.lineTo(11, 14.4f);
                    c.lineTo(15.4f, 9.8f);
                    canvas.drawPath(c, stroke);
                }
                break;
            }
            case TOOLS: {
                canvas.drawRoundRect(new RectF(3.5f, 8, 20.5f, 20), 2.6f, 2.6f, stroke);
                Path h = new Path();
                h.moveTo(9, 8);
                h.lineTo(9, 6.6f);
                h.cubicTo(9, 5.7f, 9.7f, 5, 10.6f, 5);
                h.lineTo(13.4f, 5);
                h.cubicTo(14.3f, 5, 15, 5.7f, 15, 6.6f);
                h.lineTo(15, 8);
                canvas.drawPath(h, stroke);
                canvas.drawLine(3.5f, 13, 20.5f, 13, stroke);
                canvas.drawRoundRect(new RectF(10.6f, 11.6f, 13.4f, 14.4f), 0.8f, 0.8f, fill);
                break;
            }
            default: {
                Path p = new Path();
                p.moveTo(5, 20);
                p.lineTo(5, 11);
                p.arcTo(new RectF(5, 4, 19, 18), 180, 180);
                p.lineTo(19, 20);
                p.lineTo(16.67f, 18);
                p.lineTo(14.33f, 20);
                p.lineTo(12, 18);
                p.lineTo(9.67f, 20);
                p.lineTo(7.33f, 18);
                p.close();
                canvas.drawPath(p, stroke);
                canvas.drawCircle(9.6f, 11, 1.25f, fill);
                canvas.drawCircle(14.4f, 11, 1.25f, fill);
                break;
            }
        }
        canvas.restore();
    }
}
