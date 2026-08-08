package com.liskovsoft.smartyoutubetv2.droid.ui.playback;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import com.github.vkay94.dtpv.DoubleTapPlayerAdapter;
import com.github.vkay94.dtpv.DoubleTapPlayerView;
import com.github.vkay94.dtpv.youtube.YouTubeOverlay;
import com.github.vkay94.dtpv.youtube.YouTubeOverlay.PerformListener;
import com.google.android.exoplayer2.Player;

/**
 * YouTube-app-like touch layer of the phone player. Fed from
 * {@code PlaybackActivity.dispatchTouchEvent()} (the events are only observed — dispatching
 * continues, so the buttons of the controls overlay keep working).
 *
 * <ul>
 * <li>single tap — toggles the controls overlay ({@link Listener#onTap()})</li>
 * <li>double tap left/right — ±seek through the {@code doubletapplayerview} module
 *     ({@link DoubleTapPlayerAdapter} + {@link YouTubeOverlay} ripple)</li>
 * <li>vertical drag, right half — volume, left half — brightness (fullscreen only)</li>
 * <li>horizontal drag — scrub through the video (fullscreen only)</li>
 * <li>pinch out/in — fill the screen (crop) / fit the video inside it (fullscreen only)</li>
 * <li>long press — 2x speed while held</li>
 * </ul>
 */
public class PlaybackGestureHandler {
    private static final int DRAG_NONE = 0;
    private static final int DRAG_VOLUME = 1;
    private static final int DRAG_BRIGHTNESS = 2;
    private static final int DRAG_SEEK = 3;
    /** A full-width swipe scrubs this share of the video duration. */
    private static final float SEEK_SWIPE_FRACTION = 0.5f;
    /** How far the fingers must spread/close before the zoom flips. */
    private static final float ZOOM_TRIGGER = 0.15f;

    public interface Listener {
        /** Single (confirmed) tap on the video surface. */
        void onTap();

        /** Vertical drag on the right half. {@code delta} is a fraction of the drag area, + = up. */
        void onVolumeDelta(float delta);

        /** Vertical drag on the left half. {@code delta} is a fraction of the drag area, + = up. */
        void onBrightnessDelta(float delta);

        /** Horizontal drag started: remember the position the scrub is measured from. */
        void onSeekStart();

        /**
         * Horizontal drag in progress. {@code totalDelta} is the whole movement since the drag
         * began, as a fraction of the player width (+ = right/forward). Preview only — the seek
         * is committed in {@link #onDragEnd()}.
         */
        void onSeekDelta(float totalDelta);

        /** Pinch out (true) fills the screen by cropping; pinch in (false) fits the whole frame. */
        void onZoomChanged(boolean fillScreen);

        /** Finger lifted after a volume/brightness/seek drag. */
        void onDragEnd();

        /** Long press started: switch to 2x speed. */
        void onSpeedBoostStart();

        /** Long press released: restore the previous speed. */
        void onSpeedBoostEnd();

        /** Volume/brightness drags are fullscreen-only. */
        boolean isDragEnabled();

        /** False when the point is over the controls (top/bottom bar) — those own their touches. */
        boolean isGestureAllowedAt(float playerX, float playerY);
    }

    private final View mPlayerView;
    private final YouTubeOverlay mYouTubeOverlay;
    private final Listener mListener;
    private final DoubleTapPlayerAdapter mDoubleTapAdapter;
    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleDetector;
    private final int mTouchSlop;
    private final int[] mLocation = new int[2];
    private boolean mIgnoreGesture;
    private boolean mSpeedBoosted;
    private boolean mZooming;
    private boolean mZoomHandled;
    private float mZoomAccumulated = 1f;
    private int mDragMode = DRAG_NONE;

    public PlaybackGestureHandler(Context context, View playerView, YouTubeOverlay youTubeOverlay, Listener listener) {
        mPlayerView = playerView;
        mYouTubeOverlay = youTubeOverlay;
        mListener = listener;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mDoubleTapAdapter = new DoubleTapPlayerAdapter(playerView);
        mDoubleTapAdapter.onSingleTap(new DoubleTapPlayerAdapter.OnSingleTap() {
            @Override
            public void onSingleTap(@NonNull MotionEvent event) {
                mListener.onTap();
            }
        });
        mDoubleTapAdapter.controller(mYouTubeOverlay);

        mYouTubeOverlay
                .playerView(mDoubleTapAdapter)
                .performListener(new PerformListener() {
                    @Override
                    public void onAnimationStart() {
                        mYouTubeOverlay.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd() {
                        mYouTubeOverlay.setVisibility(View.GONE);
                    }

                    @Override
                    public Boolean shouldForward(@NonNull Player player, @NonNull DoubleTapPlayerView playerView, float posX) {
                        int state = player.getPlaybackState();

                        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                            playerView.cancelInDoubleTapMode();
                            return null;
                        }

                        if (player.getCurrentPosition() > 500 && posX < playerView.getPlayerWidth() * 0.35) {
                            return false; // rewind
                        }

                        if (player.getCurrentPosition() < player.getDuration() && posX > playerView.getPlayerWidth() * 0.65) {
                            return true; // forward
                        }

                        return null; // middle zone: ignore
                    }
                });

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true; // required, otherwise no other callback is delivered
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (mIgnoreGesture || mSpeedBoosted || mDragMode != DRAG_NONE || mDoubleTapAdapter.isInDoubleTapMode()) {
                    return;
                }

                mSpeedBoosted = true;
                mListener.onSpeedBoostStart();
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (mIgnoreGesture || mSpeedBoosted || mZooming || e1 == null || !mListener.isDragEnabled()) {
                    return false;
                }

                float totalY = e1.getY() - e2.getY();
                float totalX = e2.getX() - e1.getX();

                if (mDragMode == DRAG_NONE) {
                    // Whichever axis breaks the slop first owns the gesture until the finger lifts
                    if (Math.abs(totalY) >= mTouchSlop && Math.abs(totalY) >= Math.abs(totalX)) {
                        mDragMode = e1.getX() < mPlayerView.getWidth() / 2f ? DRAG_BRIGHTNESS : DRAG_VOLUME;
                    } else if (Math.abs(totalX) >= mTouchSlop) {
                        mDragMode = DRAG_SEEK;
                        mListener.onSeekStart();
                    } else {
                        return false; // not far enough yet
                    }
                }

                if (mDragMode == DRAG_SEEK) {
                    int width = mPlayerView.getWidth();

                    if (width <= 0) {
                        return false;
                    }

                    mListener.onSeekDelta(totalX / width);

                    return true;
                }

                int height = mPlayerView.getHeight();

                if (height <= 0) {
                    return false;
                }

                // Full swing over ~70% of the player height
                float delta = distanceY / (height * 0.7f);

                if (mDragMode == DRAG_BRIGHTNESS) {
                    mListener.onBrightnessDelta(delta);
                } else {
                    mListener.onVolumeDelta(delta);
                }

                return true;
            }
        });

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (mIgnoreGesture || !mListener.isDragEnabled()) {
                    return false;
                }

                mZooming = true;
                mZoomHandled = false;

                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mZoomHandled) {
                    return false;
                }

                float scale = detector.getScaleFactor() * mZoomAccumulated;
                mZoomAccumulated = scale;

                if (scale > 1f + ZOOM_TRIGGER) {
                    mZoomHandled = true;
                    mListener.onZoomChanged(true);
                } else if (scale < 1f - ZOOM_TRIGGER) {
                    mZoomHandled = true;
                    mListener.onZoomChanged(false);
                }

                return true;
            }
        });
        mGestureDetector.setIsLongpressEnabled(true);
    }

    /**
     * Must be called whenever the engine is (re)created: the overlay seeks on the player directly.
     */
    public void setPlayer(Player player) {
        mYouTubeOverlay.player(player);
    }

    public void setSeekSeconds(int seconds) {
        mYouTubeOverlay.seekSeconds(seconds > 0 ? seconds : 10);
    }

    /**
     * Observes the activity's touch stream. Never consumes: the return value of
     * {@code dispatchTouchEvent} stays with the view hierarchy.
     */
    public void onTouchEvent(MotionEvent event) {
        if (event == null) {
            return;
        }

        MotionEvent local = toPlayerCoords(event);

        try {
            int action = local.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                mDragMode = DRAG_NONE;
                mSpeedBoosted = false;
                mZooming = false;
                mZoomHandled = false;
                mZoomAccumulated = 1f;
                mIgnoreGesture = !isInsidePlayer(local) || !mListener.isGestureAllowedAt(local.getX(), local.getY());
            }

            if (!mIgnoreGesture) {
                mScaleDetector.onTouchEvent(local);

                // A second finger means a pinch, never a tap/seek/volume drag
                if (!mZooming) {
                    mDoubleTapAdapter.onTouchEvent(local);
                    mGestureDetector.onTouchEvent(local);
                }
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (mSpeedBoosted) {
                    mSpeedBoosted = false;
                    mListener.onSpeedBoostEnd();
                }

                if (mDragMode != DRAG_NONE) {
                    mDragMode = DRAG_NONE;
                    mListener.onDragEnd();
                }

                mZooming = false;
                mZoomAccumulated = 1f;
            }
        } finally {
            local.recycle();
        }
    }

    public void release() {
        mYouTubeOverlay
                .player(null)
                .playerView(null)
                .performListener(null);
        mDoubleTapAdapter.controller(null);
        mDoubleTapAdapter.onSingleTap(null);
    }

    private boolean isInsidePlayer(MotionEvent playerLocalEvent) {
        float x = playerLocalEvent.getX();
        float y = playerLocalEvent.getY();

        return x >= 0 && y >= 0 && x <= mPlayerView.getWidth() && y <= mPlayerView.getHeight();
    }

    /**
     * The activity delivers window coordinates while the double-tap zones and the drag areas
     * are relative to the video surface.
     */
    private MotionEvent toPlayerCoords(MotionEvent event) {
        MotionEvent copy = MotionEvent.obtain(event);
        mPlayerView.getLocationInWindow(mLocation);
        copy.offsetLocation(-mLocation[0], -mLocation[1]);

        return copy;
    }
}
