package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.droid.R;

/**
 * The start intro, as Saneafy's: the gothic S in the middle of the screen over a soft red light
 * that drifts, shown while the first section loads instead of the spinner on an empty page.
 * <p>
 * The S fades in and grows from 85% (150ms in, 650ms, fast-out-slow-in), the light moves round
 * once every 16s, both as Saneafy's welcome screen. {@link #dismiss()} lets the entrance finish,
 * then fades the whole view out and removes it.
 */
public class IntroView extends View {
    /** Saneafy's launch S: 78dp wide (measured on the S24+), so the 108-unit intro_s drawn 160dp */
    private static final int S_BOX_DP = 160;
    private static final long ENTER_DELAY_MS = 150;
    private static final long ENTER_MS = 650;
    private static final long EXIT_MS = 300;
    private static final long GLOW_TURN_MS = 16_000;
    private static final float GLOW_ALPHA = 0.30f;
    /** Nothing loaded by then (no network...): the page underneath says why */
    private static final long GIVE_UP_MS = 20_000;

    private final Drawable mS;
    private final int mBackground;
    private final int mGlow;
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] mOnScreen = new int[2];
    private final long mShownAt = SystemClock.uptimeMillis();
    private final ValueAnimator mEnter;
    private final ValueAnimator mDrift;
    private float mEnterValue;
    private float mPhase;
    private boolean mDismissed;
    private final Runnable mGiveUp = this::dismiss;

    /** Puts the intro over the activity's content */
    public static IntroView show(Activity activity) {
        IntroView intro = new IntroView(activity);
        ViewGroup content = activity.findViewById(android.R.id.content);
        content.addView(intro, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        intro.postDelayed(intro.mGiveUp, GIVE_UP_MS);
        return intro;
    }

    private IntroView(Context context) {
        super(context);
        // Covers the page: a tap must not reach the tabs under it
        setClickable(true);
        mS = ContextCompat.getDrawable(context, R.drawable.intro_s);
        mBackground = ContextCompat.getColor(context, R.color.shared_surface);
        mGlow = ContextCompat.getColor(context, R.color.intro_glow);

        mEnter = ValueAnimator.ofFloat(0f, 1f);
        mEnter.setStartDelay(ENTER_DELAY_MS);
        mEnter.setDuration(ENTER_MS);
        mEnter.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        mEnter.addUpdateListener(a -> { mEnterValue = (float) a.getAnimatedValue(); invalidate(); });

        mDrift = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        mDrift.setDuration(GLOW_TURN_MS);
        mDrift.setInterpolator(new LinearInterpolator());
        mDrift.setRepeatCount(ValueAnimator.INFINITE);
        mDrift.addUpdateListener(a -> { mPhase = (float) a.getAnimatedValue(); invalidate(); });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mEnter.start();
        // "Remove animations": the light stays where it starts
        if (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) {
            mDrift.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mEnter.cancel();
        mDrift.cancel();
        removeCallbacks(mGiveUp);
    }

    /** The first content is there: finish the entrance, then fade out. Safe from any thread, more than once */
    public void dismiss() {
        post(() -> {
            if (mDismissed) {
                return;
            }
            mDismissed = true;
            long entranceLeft = Math.max(0, mShownAt + ENTER_DELAY_MS + ENTER_MS - SystemClock.uptimeMillis());
            animate().alpha(0f).setStartDelay(entranceLeft).setDuration(EXIT_MS).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(IntroView.this);
                    }
                }
            });
        });
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(mBackground);

        // The light: Saneafy's Glow, a radial fade from the brand colour, drifting round
        float cx = width * (0.5f + 0.12f * (float) Math.sin(mPhase));
        float cy = height * (0.30f + 0.05f * (float) Math.cos(mPhase));
        float radius = width * 1.05f;
        if (radius > 0) {
            int inner = Color.argb(Math.round(Color.alpha(mGlow) * GLOW_ALPHA), Color.red(mGlow), Color.green(mGlow), Color.blue(mGlow));
            mGlowPaint.setShader(new RadialGradient(cx, cy, radius, inner, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, mGlowPaint);
        }

        // The S, in the middle of the screen (not of this view, which starts under the status bar)
        if (mS != null && mEnterValue > 0f) {
            getLocationOnScreen(mOnScreen);
            float screenHeight = getRootView().getHeight();
            float centerY = Math.max(0, Math.min(height, screenHeight / 2f - mOnScreen[1]));
            float centerX = width / 2f;
            int half = Math.round(S_BOX_DP * getResources().getDisplayMetrics().density / 2f);
            float scale = 0.85f + 0.15f * mEnterValue;
            canvas.save();
            canvas.scale(scale, scale, centerX, centerY);
            mS.setBounds(Math.round(centerX) - half, Math.round(centerY) - half, Math.round(centerX) + half, Math.round(centerY) + half);
            mS.setAlpha(Math.round(255 * mEnterValue));
            mS.draw(canvas);
            canvas.restore();
        }
    }
}
