package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.droid.R;

/**
 * Loading spinner after loading.dev's "Atom": a thin circle with three rings tumbling inside it.
 * <p>
 * The web version tilts each ring flat (rotateX 90deg), spins it around the view axis and leans
 * its orbit by 0, 60 and 120 degrees, all a third of a turn apart. Seen straight on, a ring turned
 * by angle a is an ellipse as tall as the circle and |cos a| as wide, leaned by its orbit angle,
 * which is what this draws, without any 3D.
 */
public class AtomSpinnerView extends View {
    /** One full turn of each ring, as the web default (1000ms, linear) */
    private static final long TURN_MS = 1000;
    private static final int RINGS = 3;
    /** Stroke widths relative to the spinner size, as in the web CSS */
    private static final float SHELL_STROKE = 0.055f;
    private static final float RING_STROKE = 0.045f;

    private final Paint mShellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();

    public AtomSpinnerView(Context context) {
        this(context, null);
    }

    public AtomSpinnerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mShellPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStyle(Paint.Style.STROKE);
        setColor(ContextCompat.getColor(context, R.color.playback_loading_spinner));
    }

    public void setColor(int color) {
        mShellPaint.setColor(color);
        mRingPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(width, height);

        if (size <= 0) {
            return;
        }

        float cx = getPaddingLeft() + width / 2;
        float cy = getPaddingTop() + height / 2;

        // Borders sit inside the box on the web, so the stroke centre is half a stroke in
        float shellStroke = size * SHELL_STROKE;
        float shellRadius = size / 2 - shellStroke / 2;
        mShellPaint.setStrokeWidth(shellStroke);
        canvas.drawCircle(cx, cy, shellRadius, mShellPaint);

        float ringStroke = size * RING_STROKE;
        float ringRadius = size / 2 - ringStroke / 2;
        mRingPaint.setStrokeWidth(ringStroke);

        double turn = (SystemClock.uptimeMillis() % TURN_MS) / (double) TURN_MS * 2 * Math.PI;

        for (int i = 0; i < RINGS; i++) {
            // Ring i starts i thirds of a turn back (the web staggers them with negative delays)
            double angle = turn - i * 2 * Math.PI / RINGS;
            float halfWidth = (float) Math.abs(Math.cos(angle)) * ringRadius;

            canvas.save();
            canvas.rotate(i * 180f / RINGS, cx, cy);
            mOval.set(cx - halfWidth, cy - ringRadius, cx + halfWidth, cy + ringRadius);
            canvas.drawOval(mOval, mRingPaint);
            canvas.restore();
        }

        // Keep animating only while on screen: a GONE or detached view gets no more draws
        if (isShown()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == VISIBLE) {
            invalidate();
        }
    }
}
