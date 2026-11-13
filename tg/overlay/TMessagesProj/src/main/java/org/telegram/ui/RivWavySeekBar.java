package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;

/**
 * Riv Post: the "Riv Tunes Player" signature seek bar. A straight line for the un-played
 * remainder and a sine wave for the played portion, with a small rounded thumb tick at the
 * progress point. Exact math ported from the old app's Compose WavySeekBar:
 * y = cy + amplitude * sin(x / waveLength * 2*PI - phase).
 */
public class RivWavySeekBar extends View {

    public interface Listener {
        void onSeek(float fraction);

        void onSeekEnd();
    }

    private float progress; // 0..1
    private boolean playing;
    private int waveColor = 0xFFFFFFFF;
    private int trackColor = 0x33FFFFFF;
    private float phase;
    private float amplitude; // animates 0..1 depending on `playing`
    private Listener listener;
    private boolean dragging;
    private ValueAnimator phaseAnimator;
    private ValueAnimator ampAnimator;

    public RivWavySeekBar(Context context) {
        super(context);
        startPhaseAnimator();
    }

    private void startPhaseAnimator() {
        phaseAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        phaseAnimator.setDuration(1800);
        phaseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        phaseAnimator.setInterpolator(new LinearInterpolator());
        phaseAnimator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        phaseAnimator.start();
    }

    public void setColors(int wave, int track) {
        waveColor = wave;
        trackColor = track;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setProgress(float p) {
        if (dragging) return;
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public void setPlaying(boolean p) {
        if (playing == p) return;
        playing = p;
        if (ampAnimator != null) ampAnimator.cancel();
        ampAnimator = ValueAnimator.ofFloat(amplitude, playing ? 1f : 0f);
        ampAnimator.setDuration(400);
        ampAnimator.addUpdateListener(a -> {
            amplitude = (float) a.getAnimatedValue();
            invalidate();
        });
        ampAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (phaseAnimator != null) phaseAnimator.cancel();
        if (ampAnimator != null) ampAnimator.cancel();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float frac = Math.max(0f, Math.min(1f, event.getX() / Math.max(1, getWidth())));
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                progress = frac;
                invalidate();
                if (listener != null) listener.onSeek(progress);
                return true;
            case MotionEvent.ACTION_MOVE:
                progress = frac;
                invalidate();
                if (listener != null) listener.onSeek(progress);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (listener != null) listener.onSeekEnd();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        float cy = getHeight() / 2f;
        float stroke = AndroidUtilities.dpf2(2.2f);
        float amp = AndroidUtilities.dpf2(3.5f) * amplitude;
        float waveLen = AndroidUtilities.dpf2(26f);
        float px = progress * w;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(stroke);

        // un-played remainder: straight line
        paint.setColor(trackColor);
        canvas.drawLine(px, cy, w, cy, paint);

        // played portion: sine wave
        paint.setColor(waveColor);
        Path path = new Path();
        float x = 0f;
        path.moveTo(0f, cy + amp * (float) Math.sin(phase));
        while (x < px) {
            x = Math.min(x + 2f, px);
            float y = cy + amp * (float) Math.sin(x / waveLen * 2f * (float) Math.PI - phase);
            path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);

        // thumb tick
        paint.setStyle(Paint.Style.FILL);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(waveColor);
        float tw = AndroidUtilities.dpf2(5f);
        float th = AndroidUtilities.dpf2(20f);
        float r = AndroidUtilities.dpf2(3f);
        android.graphics.RectF rect = new android.graphics.RectF(px - tw / 2, cy - th / 2, px + tw / 2, cy + th / 2);
        canvas.drawRoundRect(rect, r, r, fill);
    }
}
