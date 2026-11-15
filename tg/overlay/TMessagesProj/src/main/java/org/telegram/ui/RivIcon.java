package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Riv Post: a few simple glyphs drawn in code (heart, speaker, shuffle, repeat, chevron,
 * close, skip) so the new library/player screens don't depend on guessing the exact names
 * of icon drawables that may or may not exist in this build of Telegram. Self-contained,
 * no resources needed.
 */
public class RivIcon extends View {

    public static final int HEART = 0;
    public static final int HEART_OUTLINE = 1;
    public static final int SPEAKER = 2;
    public static final int CHEVRON_DOWN = 3;
    public static final int CLOSE = 4;
    public static final int SHUFFLE = 5;
    public static final int REPEAT = 6;
    public static final int REPEAT_ONE = 7;
    public static final int SKIP_NEXT = 8;
    public static final int SKIP_PREV = 9;
    public static final int MOON = 10;
    public static final int SPEED = 11;
    public static final int EQ = 12;
    public static final int LYRICS = 13;
    public static final int QUEUE = 14;
    public static final int INFO = 15;
    public static final int PLAY = 16;
    public static final int PAUSE = 17;
    public static final int TAB_CHATS = 18;
    public static final int TAB_CONTACTS = 19;
    public static final int TAB_CALLS = 20;
    public static final int TAB_MUSIC = 21;
    public static final int TAB_VIDEOS = 22;
    public static final int TAB_PROFILE = 23;
    public static final int CHECK = 24;
    public static final int MORE = 25;
    public static final int REWIND = 26;
    public static final int FORWARD = 27;
    public static final int PHONE = 28;
    public static final int NEXT_FILLED = 29;
    public static final int PREV_FILLED = 30;
    public static final int BACK = 31;
    public static final int LOCK = 32;
    public static final int ROTATE = 33;
    public static final int ASPECT = 34;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int kind;
    private int color;

    public RivIcon(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setKind(int k) {
        kind = k;
        invalidate();
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float s = Math.min(w, h) * 0.5f;
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        switch (kind) {
            case BACK: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2.2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawLine(cx - s * 0.65f, cy, cx + s * 0.65f, cy, paint);
                Path p = new Path();
                p.moveTo(cx - s * 0.1f, cy - s * 0.55f);
                p.lineTo(cx - s * 0.65f, cy);
                p.lineTo(cx - s * 0.1f, cy + s * 0.55f);
                canvas.drawPath(p, paint);
                break;
            }
            case LOCK: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2f));
                canvas.drawArc(cx - s * 0.38f, cy - s * 0.8f, cx + s * 0.38f, cy - s * 0.05f, 180, 180, false, paint);
                canvas.drawLine(cx - s * 0.38f, cy - s * 0.42f, cx - s * 0.38f, cy - s * 0.1f, paint);
                canvas.drawLine(cx + s * 0.38f, cy - s * 0.42f, cx + s * 0.38f, cy - s * 0.1f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new android.graphics.RectF(cx - s * 0.6f, cy - s * 0.1f, cx + s * 0.6f, cy + s * 0.75f), s * 0.12f, s * 0.12f, paint);
                break;
            }
            case ROTATE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
                canvas.save();
                canvas.rotate(-45, cx, cy);
                canvas.drawRoundRect(new android.graphics.RectF(cx - s * 0.32f, cy - s * 0.6f, cx + s * 0.32f, cy + s * 0.6f), s * 0.1f, s * 0.1f, paint);
                canvas.restore();
                canvas.drawArc(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f, 200, 50, false, paint);
                canvas.drawArc(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f, 20, 50, false, paint);
                break;
            }
            case ASPECT: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                float a = s * 0.75f, b = s * 0.55f, k = s * 0.3f;
                canvas.drawLine(cx - a, cy - b, cx - a + k, cy - b, paint);
                canvas.drawLine(cx - a, cy - b, cx - a, cy - b + k, paint);
                canvas.drawLine(cx + a, cy - b, cx + a - k, cy - b, paint);
                canvas.drawLine(cx + a, cy - b, cx + a, cy - b + k, paint);
                canvas.drawLine(cx - a, cy + b, cx - a + k, cy + b, paint);
                canvas.drawLine(cx - a, cy + b, cx - a, cy + b - k, paint);
                canvas.drawLine(cx + a, cy + b, cx + a - k, cy + b, paint);
                canvas.drawLine(cx + a, cy + b, cx + a, cy + b - k, paint);
                break;
            }
            case MORE: {
                paint.setStyle(Paint.Style.FILL);
                float r = AndroidUtilities.dp(2f);
                canvas.drawCircle(cx, cy - s * 0.5f, r, paint);
                canvas.drawCircle(cx, cy, r, paint);
                canvas.drawCircle(cx, cy + s * 0.5f, r, paint);
                break;
            }
            case REWIND:
            case FORWARD: {
                // circular arrow with a small "10" inside
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                float r = s * 0.72f;
                boolean back = kind == REWIND;
                canvas.drawArc(cx - r, cy - r, cx + r, cy + r, back ? -60 : -120, back ? 300 : -300, false, paint);
                paint.setStyle(Paint.Style.FILL);
                float ax = back ? cx + r * 0.5f : cx - r * 0.5f;
                float ay = cy - r * 0.87f;
                Path head = new Path();
                head.moveTo(ax + (back ? -s * 0.28f : s * 0.28f), ay);
                head.lineTo(ax + (back ? s * 0.1f : -s * 0.1f), ay - s * 0.25f);
                head.lineTo(ax + (back ? s * 0.1f : -s * 0.1f), ay + s * 0.25f);
                head.close();
                canvas.drawPath(head, paint);
                paint.setTextSize(s * 0.62f);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(true);
                canvas.drawText("10", cx, cy + s * 0.22f, paint);
                paint.setFakeBoldText(false);
                break;
            }
            case PHONE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
                android.graphics.RectF rr = new android.graphics.RectF(cx - s * 0.42f, cy - s * 0.75f, cx + s * 0.42f, cy + s * 0.75f);
                canvas.drawRoundRect(rr, s * 0.15f, s * 0.15f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy + s * 0.55f, AndroidUtilities.dp(1.2f), paint);
                break;
            }
            case NEXT_FILLED:
            case PREV_FILLED: {
                paint.setStyle(Paint.Style.FILL);
                boolean next = kind == NEXT_FILLED;
                Path p = new Path();
                if (next) {
                    p.moveTo(cx - s * 0.55f, cy - s * 0.6f);
                    p.lineTo(cx + s * 0.3f, cy);
                    p.lineTo(cx - s * 0.55f, cy + s * 0.6f);
                    p.close();
                    canvas.drawPath(p, paint);
                    canvas.drawRoundRect(new android.graphics.RectF(cx + s * 0.38f, cy - s * 0.6f, cx + s * 0.58f, cy + s * 0.6f), s * 0.08f, s * 0.08f, paint);
                } else {
                    p.moveTo(cx + s * 0.55f, cy - s * 0.6f);
                    p.lineTo(cx - s * 0.3f, cy);
                    p.lineTo(cx + s * 0.55f, cy + s * 0.6f);
                    p.close();
                    canvas.drawPath(p, paint);
                    canvas.drawRoundRect(new android.graphics.RectF(cx - s * 0.58f, cy - s * 0.6f, cx - s * 0.38f, cy + s * 0.6f), s * 0.08f, s * 0.08f, paint);
                }
                break;
            }
            case HEART:
            case HEART_OUTLINE: {
                paint.setStyle(kind == HEART ? Paint.Style.FILL : Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                Path p = new Path();
                float r = s * 0.42f;
                p.moveTo(cx, cy + s * 0.55f);
                p.cubicTo(cx - s * 1.1f, cy, cx - s * 0.5f, cy - s * 0.95f, cx, cy - s * 0.25f);
                p.cubicTo(cx + s * 0.5f, cy - s * 0.95f, cx + s * 1.1f, cy, cx, cy + s * 0.55f);
                p.close();
                canvas.drawPath(p, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case SPEAKER: {
                Path p = new Path();
                p.moveTo(cx - s * 0.9f, cy - s * 0.3f);
                p.lineTo(cx - s * 0.3f, cy - s * 0.3f);
                p.lineTo(cx + s * 0.3f, cy - s * 0.9f);
                p.lineTo(cx + s * 0.3f, cy + s * 0.9f);
                p.lineTo(cx - s * 0.3f, cy + s * 0.3f);
                p.lineTo(cx - s * 0.9f, cy + s * 0.3f);
                p.close();
                canvas.drawPath(p, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                canvas.drawArc(cx + s * 0.35f, cy - s * 0.6f, cx + s * 1.1f, cy + s * 0.6f, -55, 110, false, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case CHEVRON_DOWN:
            case SKIP_NEXT:
            case SKIP_PREV: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2.2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                Path p = new Path();
                if (kind == CHEVRON_DOWN) {
                    p.moveTo(cx - s * 0.6f, cy - s * 0.25f);
                    p.lineTo(cx, cy + s * 0.35f);
                    p.lineTo(cx + s * 0.6f, cy - s * 0.25f);
                } else if (kind == SKIP_NEXT) {
                    p.moveTo(cx - s * 0.4f, cy - s * 0.6f);
                    p.lineTo(cx + s * 0.3f, cy);
                    p.lineTo(cx - s * 0.4f, cy + s * 0.6f);
                    canvas.drawPath(p, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(cx + s * 0.45f, cy - s * 0.6f, cx + s * 0.6f, cy + s * 0.6f, paint);
                    return;
                } else {
                    p.moveTo(cx + s * 0.4f, cy - s * 0.6f);
                    p.lineTo(cx - s * 0.3f, cy);
                    p.lineTo(cx + s * 0.4f, cy + s * 0.6f);
                    canvas.drawPath(p, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(cx - s * 0.6f, cy - s * 0.6f, cx - s * 0.45f, cy + s * 0.6f, paint);
                    return;
                }
                canvas.drawPath(p, paint);
                break;
            }
            case CLOSE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2.2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.5f, cy + s * 0.5f, paint);
                canvas.drawLine(cx + s * 0.5f, cy - s * 0.5f, cx - s * 0.5f, cy + s * 0.5f, paint);
                break;
            }
            case SHUFFLE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                Path p1 = new Path();
                p1.moveTo(cx - s * 0.9f, cy - s * 0.5f);
                p1.lineTo(cx - s * 0.2f, cy - s * 0.5f);
                p1.cubicTo(cx + s * 0.3f, cy - s * 0.5f, cx + s * 0.3f, cy + s * 0.5f, cx + s * 0.9f, cy + s * 0.5f);
                canvas.drawPath(p1, paint);
                Path p2 = new Path();
                p2.moveTo(cx - s * 0.9f, cy + s * 0.5f);
                p2.lineTo(cx - s * 0.2f, cy + s * 0.5f);
                p2.cubicTo(cx + s * 0.3f, cy + s * 0.5f, cx + s * 0.3f, cy - s * 0.5f, cx + s * 0.9f, cy - s * 0.5f);
                canvas.drawPath(p2, paint);
                paint.setStyle(Paint.Style.FILL);
                drawArrowHead(canvas, cx + s * 0.9f, cy + s * 0.5f, s * 0.22f, true);
                drawArrowHead(canvas, cx + s * 0.9f, cy - s * 0.5f, s * 0.22f, false);
                break;
            }
            case REPEAT:
            case REPEAT_ONE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
                canvas.drawArc(cx - s * 0.75f, cy - s * 0.75f, cx + s * 0.75f, cy + s * 0.75f, -200, 290, false, paint);
                paint.setStyle(Paint.Style.FILL);
                drawArrowHead(canvas, cx + s * 0.6f, cy - s * 0.55f, s * 0.22f, false);
                if (kind == REPEAT_ONE) {
                    paint.setTextSize(s * 0.8f);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("1", cx, cy + s * 0.3f, paint);
                }
                break;
            }
            case MOON: {
                canvas.save();
                Path circle = new Path();
                circle.addCircle(cx, cy, s * 0.7f, Path.Direction.CW);
                Path hole = new Path();
                hole.addCircle(cx + s * 0.38f, cy - s * 0.28f, s * 0.62f, Path.Direction.CW);
                circle.op(hole, Path.Op.DIFFERENCE);
                canvas.drawPath(circle, paint);
                canvas.restore();
                break;
            }
            case SPEED: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
                canvas.drawArc(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, 180, 180, false, paint);
                canvas.drawLine(cx, cy, cx + s * 0.45f, cy - s * 0.35f, paint);
                break;
            }
            case EQ: {
                paint.setStyle(Paint.Style.FILL);
                float barW = s * 0.35f;
                canvas.drawRect(cx - s * 0.8f, cy - s * 0.2f, cx - s * 0.8f + barW, cy + s * 0.8f, paint);
                canvas.drawRect(cx - barW / 2, cy - s * 0.8f, cx + barW / 2, cy + s * 0.8f, paint);
                canvas.drawRect(cx + s * 0.45f, cy + s * 0.1f, cx + s * 0.45f + barW, cy + s * 0.8f, paint);
                break;
            }
            case LYRICS: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                canvas.drawLine(cx - s * 0.7f, cy - s * 0.4f, cx + s * 0.7f, cy - s * 0.4f, paint);
                canvas.drawLine(cx - s * 0.7f, cy, cx + s * 0.3f, cy, paint);
                canvas.drawLine(cx - s * 0.7f, cy + s * 0.4f, cx + s * 0.5f, cy + s * 0.4f, paint);
                break;
            }
            case QUEUE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                canvas.drawLine(cx - s * 0.7f, cy - s * 0.5f, cx + s * 0.4f, cy - s * 0.5f, paint);
                canvas.drawLine(cx - s * 0.7f, cy, cx + s * 0.4f, cy, paint);
                canvas.drawLine(cx - s * 0.7f, cy + s * 0.5f, cx + s * 0.4f, cy + s * 0.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx + s * 0.65f, cy, s * 0.12f, paint);
                break;
            }
            case PLAY: {
                Path p = new Path();
                p.moveTo(cx - s * 0.5f, cy - s * 0.75f);
                p.lineTo(cx + s * 0.75f, cy);
                p.lineTo(cx - s * 0.5f, cy + s * 0.75f);
                p.close();
                canvas.drawPath(p, paint);
                break;
            }
            case PAUSE: {
                float barW = s * 0.42f;
                canvas.drawRect(cx - s * 0.65f, cy - s * 0.75f, cx - s * 0.65f + barW, cy + s * 0.75f, paint);
                canvas.drawRect(cx + s * 0.65f - barW, cy - s * 0.75f, cx + s * 0.65f, cy + s * 0.75f, paint);
                break;
            }
            case INFO: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                canvas.drawCircle(cx, cy, s * 0.75f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - s * 0.32f, AndroidUtilities.dp(1.3f), paint);
                canvas.drawRect(cx - AndroidUtilities.dp(1.2f), cy - s * 0.05f, cx + AndroidUtilities.dp(1.2f), cy + s * 0.45f, paint);
                break;
            }
            case TAB_CHATS: {
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                float r = s * 0.22f;
                android.graphics.RectF rect = new android.graphics.RectF(cx - s * 0.85f, cy - s * 0.65f, cx + s * 0.85f, cy + s * 0.35f);
                p.addRoundRect(rect, r, r, Path.Direction.CW);
                p.moveTo(cx - s * 0.45f, cy + s * 0.35f);
                p.lineTo(cx - s * 0.15f, cy + s * 0.35f);
                p.lineTo(cx - s * 0.35f, cy + s * 0.78f);
                p.close();
                canvas.drawPath(p, paint);
                break;
            }
            case TAB_CONTACTS: {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - s * 0.45f, s * 0.38f, paint);
                Path p = new Path();
                p.moveTo(cx - s * 0.75f, cy + s * 0.75f);
                p.cubicTo(cx - s * 0.75f, cy + s * 0.1f, cx - s * 0.5f, cy + s * 0.05f, cx, cy + s * 0.05f);
                p.cubicTo(cx + s * 0.5f, cy + s * 0.05f, cx + s * 0.75f, cy + s * 0.1f, cx + s * 0.75f, cy + s * 0.75f);
                p.close();
                canvas.drawPath(p, paint);
                break;
            }
            case TAB_CALLS: {
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(cx - s * 0.65f, cy - s * 0.8f);
                p.cubicTo(cx - s * 0.95f, cy - s * 0.5f, cx - s * 0.95f, cy - s * 0.1f, cx - s * 0.55f, cy + s * 0.3f);
                p.cubicTo(cx - s * 0.15f, cy + s * 0.7f, cx + s * 0.25f, cy + s * 0.7f, cx + s * 0.55f, cy + s * 0.4f);
                p.cubicTo(cx + s * 0.65f, cy + s * 0.3f, cx + s * 0.6f, cy + s * 0.15f, cx + s * 0.45f, cy + s * 0.05f);
                p.cubicTo(cx + s * 0.35f, cy - s * 0.02f, cx + s * 0.25f, cy, cx + s * 0.2f, cy + s * 0.1f);
                p.cubicTo(cx + s * 0.05f, cy + s * 0.05f, cx - s * 0.1f, cy - s * 0.1f, cx - s * 0.15f, cy - s * 0.25f);
                p.cubicTo(cx - s * 0.05f, cy - s * 0.3f, cx - s * 0.02f, cy - s * 0.4f, cx - s * 0.1f, cy - s * 0.5f);
                p.cubicTo(cx - s * 0.2f, cy - s * 0.65f, cx - s * 0.35f, cy - s * 0.85f, cx - s * 0.45f, cy - s * 0.85f);
                p.close();
                canvas.drawPath(p, paint);
                break;
            }
            case TAB_MUSIC: {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - s * 0.45f, cy + s * 0.55f, s * 0.26f, paint);
                canvas.drawCircle(cx + s * 0.45f, cy + s * 0.35f, s * 0.26f, paint);
                canvas.drawRect(cx - s * 0.28f, cy - s * 0.85f, cx - s * 0.15f, cy + s * 0.55f, paint);
                canvas.drawRect(cx + s * 0.6f, cy - s * 0.65f, cx + s * 0.73f, cy + s * 0.35f, paint);
                Path beam = new Path();
                beam.moveTo(cx - s * 0.28f, cy - s * 0.85f);
                beam.lineTo(cx + s * 0.73f, cy - s * 0.65f);
                beam.lineTo(cx + s * 0.73f, cy - s * 0.4f);
                beam.lineTo(cx - s * 0.28f, cy - s * 0.6f);
                beam.close();
                canvas.drawPath(beam, paint);
                break;
            }
            case TAB_VIDEOS: {
                paint.setStyle(Paint.Style.FILL);
                float top = cy - s * 0.55f, bottom = cy + s * 0.2f;
                canvas.drawRect(cx - s * 0.85f, top, cx + s * 0.85f, bottom, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                float notch = (bottom - top) * 0.35f;
                for (int i = 0; i < 4; i++) {
                    float x = cx - s * 0.85f + (s * 1.7f) * (i + 0.5f) / 4f;
                    canvas.drawLine(x - notch * 0.4f, top, x + notch * 0.4f, top + notch, paint);
                }
                break;
            }
            case CHECK: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                Path p = new Path();
                p.moveTo(cx - s * 0.6f, cy);
                p.lineTo(cx - s * 0.15f, cy + s * 0.45f);
                p.lineTo(cx + s * 0.65f, cy - s * 0.5f);
                canvas.drawPath(p, paint);
                break;
            }
            case TAB_PROFILE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
                canvas.drawCircle(cx, cy, s * 0.85f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - s * 0.28f, s * 0.26f, paint);
                Path p = new Path();
                p.moveTo(cx - s * 0.48f, cy + s * 0.58f);
                p.cubicTo(cx - s * 0.48f, cy + s * 0.12f, cx - s * 0.28f, cy + s * 0.08f, cx, cy + s * 0.08f);
                p.cubicTo(cx + s * 0.28f, cy + s * 0.08f, cx + s * 0.48f, cy + s * 0.12f, cx + s * 0.48f, cy + s * 0.58f);
                p.close();
                canvas.drawPath(p, paint);
                break;
            }
        }
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float size, boolean up) {
        Path p = new Path();
        if (up) {
            p.moveTo(x - size, y + size * 0.6f);
            p.lineTo(x + size, y + size * 0.6f);
            p.lineTo(x, y - size * 0.6f);
        } else {
            p.moveTo(x - size, y - size * 0.6f);
            p.lineTo(x + size, y - size * 0.6f);
            p.lineTo(x, y + size * 0.6f);
        }
        p.close();
        canvas.drawPath(p, paint);
    }
}
