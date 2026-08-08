package com.liskovsoft.smartyoutubetv2.droid.ui.playback;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.SeekBar;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Transport seek bar that paints SponsorBlock segments over the track.
 * <br/>
 * Segments arrive through {@link com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI#setSeekBarSegments(List)}
 * (fed by common's {@code SponsorBlockController}) as {@link SeekBarSegment} items holding
 * {@code startProgress}/{@code endProgress} ratios in the 0..1 range plus an ARGB color.
 * <br/>
 * Plain framework {@link SeekBar} on purpose: the module has no AppCompat delegate
 * (activities extend {@code MotherActivity} -> {@code FragmentActivity}), so
 * {@code AppCompatSeekBar} would not be auto-inflated anyway.
 */
@SuppressLint("AppCompatCustomView")
public class PlaybackSeekBar extends SeekBar {
    private static final float TRACK_HEIGHT_DP = 3f;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<SeekBarSegment> mSegments = new ArrayList<>();
    private float mTrackHalfHeightPx;

    public PlaybackSeekBar(Context context) {
        super(context);
        init();
    }

    public PlaybackSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlaybackSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mPaint.setStyle(Paint.Style.FILL);
        mTrackHalfHeightPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TRACK_HEIGHT_DP, getResources().getDisplayMetrics()) / 2f;
    }

    /**
     * Replaces the painted segments. {@code null} or an empty list clears them.
     */
    public void setSegments(List<SeekBarSegment> segments) {
        mSegments.clear();

        if (segments != null) {
            mSegments.addAll(segments);
        }

        invalidate();
    }

    public boolean hasSegments() {
        return !mSegments.isEmpty();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mSegments.isEmpty()) {
            return;
        }

        int left = getPaddingLeft();
        int right = getWidth() - getPaddingRight();
        float trackWidth = right - left;

        if (trackWidth <= 0) {
            return;
        }

        float centerY = getHeight() / 2f;
        float top = centerY - mTrackHalfHeightPx;
        float bottom = centerY + mTrackHalfHeightPx;

        for (SeekBarSegment segment : mSegments) {
            if (segment == null) {
                continue;
            }

            float start = clamp(segment.startProgress);
            float end = clamp(segment.endProgress);

            if (end < start) {
                float tmp = start;
                start = end;
                end = tmp;
            }

            float startX = left + trackWidth * start;
            float endX = left + trackWidth * end;

            // Keep very short segments visible
            if (endX - startX < mTrackHalfHeightPx * 2) {
                endX = startX + mTrackHalfHeightPx * 2;
            }

            mPaint.setColor(segment.color);
            canvas.drawRect(startX, top, endX, bottom, mPaint);
        }
    }

    private static float clamp(float value) {
        if (value < 0) {
            return 0;
        }

        return value > 1 ? 1 : value;
    }
}
