package com.liskovsoft.smartyoutubetv2.droid.ui.dialogs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * The dialog sheet, draggable downwards to dismiss.
 *
 * <p>A plain LinearLayout never sees touches that land on its scrolling child, so the drag has to
 * be caught in {@link #onInterceptTouchEvent}. The gesture only starts when the content cannot
 * scroll up any further (or the finger is on the handle/title above the list), so dragging inside
 * a scrolled list still scrolls it.
 *
 * <p>Releasing past the threshold, or flinging downwards, reports a dismiss; the dialog decides
 * whether that means going back one screen or closing.
 */
public class DialogSheetLayout extends LinearLayout {
    /** Fraction of the sheet height that must be dragged before a release dismisses it. */
    private static final float DISMISS_FRACTION = 0.3f;
    /** ...or this downward speed, in pixels per second, regardless of distance. */
    private static final float DISMISS_VELOCITY = 1200f;
    private static final long SETTLE_DURATION_MS = 180;

    public interface Listener {
        /** The sheet was dragged down far enough to dismiss. */
        void onDragDismiss();

        /** True when a downward drag should move the sheet rather than scroll the content. */
        boolean canDragToDismiss(float x, float y);
    }

    private final int mTouchSlop;
    private Listener mListener;
    private VelocityTracker mVelocityTracker;
    private float mDownY;
    private float mDownX;
    private boolean mDragging;

    public DialogSheetLayout(Context context) {
        this(context, null);
    }

    public DialogSheetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** Drop any offset left over from a previous drag (the sheet is reused between screens). */
    public void resetDrag() {
        animate().cancel();
        setTranslationY(0f);
        mDragging = false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mListener == null) {
            return super.onInterceptTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = event.getY();
                mDownX = event.getX();
                mDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - mDownY;
                float dx = event.getX() - mDownX;

                if (dy > mTouchSlop && dy > Math.abs(dx) && mListener.canDragToDismiss(mDownX, mDownY)) {
                    mDragging = true;
                    // Start measuring from here so the sheet doesn't jump by the slop distance
                    mDownY = event.getY();
                    startTracking(event);

                    return true;
                }
                break;
            default:
                break;
        }

        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mListener == null) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Taps on the sheet must not fall through to the scrim behind it
                mDownY = event.getY();
                mDownX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) {
                    float dy = event.getY() - mDownY;
                    if (dy > mTouchSlop && mListener.canDragToDismiss(mDownX, mDownY)) {
                        mDragging = true;
                        mDownY = event.getY();
                        startTracking(event);
                    }
                    return true;
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.addMovement(event);
                }

                // Downwards only: dragging back up just returns the sheet to its resting place
                setTranslationY(Math.max(0f, event.getY() - mDownY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    finishDrag(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void startTracking(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }

        mVelocityTracker.clear();
        mVelocityTracker.addMovement(event);
    }

    private void finishDrag(boolean released) {
        mDragging = false;

        float velocity = 0f;

        if (mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1000);
            velocity = mVelocityTracker.getYVelocity();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }

        boolean dismiss = released
                && (getTranslationY() > getHeight() * DISMISS_FRACTION || velocity > DISMISS_VELOCITY);

        if (dismiss) {
            // Slide the rest of the way out, then hand over
            animate().translationY(getHeight())
                    .setDuration(SETTLE_DURATION_MS)
                    .withEndAction(() -> {
                        setTranslationY(0f);
                        if (mListener != null) {
                            mListener.onDragDismiss();
                        }
                    })
                    .start();
        } else {
            animate().translationY(0f).setDuration(SETTLE_DURATION_MS).start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }
}
