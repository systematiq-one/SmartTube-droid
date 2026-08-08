package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.app.Activity;
import android.content.Context;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.droid.R;

/**
 * ViewHolder for a single video card (grid or row style).<br/>
 * Binds title, second title, thumbnail (Glide with fallback), duration badge,
 * LIVE/SHORTS badges, NEW dot and watch-progress bar.<br/>
 * Layouts: {@code shared_video_card.xml} (grid), {@code shared_video_card_row.xml} (row).
 */
public class VideoCardHolder extends RecyclerView.ViewHolder {
    private final ImageView mThumbnail;
    private final TextView mBadge;
    private final View mNewDot;
    private final ProgressBar mProgress;
    private final TextView mTitle;
    private final TextView mSubtitle;
    private Video mVideo;

    /**
     * Inflates the proper card layout and wraps it into a holder.
     *
     * @param parent recycler parent
     * @param isRow true: small horizontal-row card, false: grid card
     */
    public static VideoCardHolder create(ViewGroup parent, boolean isRow) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(isRow ? R.layout.shared_video_card_row : R.layout.shared_video_card, parent, false);
        return new VideoCardHolder(view);
    }

    public VideoCardHolder(View itemView) {
        super(itemView);

        mThumbnail = itemView.findViewById(R.id.shared_card_thumbnail);
        mBadge = itemView.findViewById(R.id.shared_card_badge);
        mNewDot = itemView.findViewById(R.id.shared_card_new_dot);
        mProgress = itemView.findViewById(R.id.shared_card_progress);
        mTitle = itemView.findViewById(R.id.shared_card_title);
        mSubtitle = itemView.findViewById(R.id.shared_card_subtitle);
    }

    /**
     * Binds a video to the card and wires tap/long-press to the listener.
     */
    public void bind(Video video, VideoGroupAdapter.Listener listener) {
        mVideo = video;

        if (video == null) {
            return;
        }

        Context context = itemView.getContext();

        mTitle.setText(video.getTitle());
        itemView.setContentDescription(video.getTitle());

        CharSequence secondTitle = video.getSecondTitle();
        if (TextUtils.isEmpty(secondTitle)) {
            mSubtitle.setVisibility(View.GONE);
        } else {
            mSubtitle.setVisibility(View.VISIBLE);
            mSubtitle.setText(secondTitle);
        }

        bindBadges(context, video);
        bindProgress(video);
        bindThumbnail(context, video);

        if (listener != null) {
            itemView.setOnClickListener(v -> listener.onVideoClicked(video));
            itemView.setOnLongClickListener(v -> {
                listener.onVideoLongClicked(video);
                return true;
            });
        }
    }

    /**
     * Releases image resources. Call from {@code onViewRecycled}.
     */
    public void unbind() {
        mVideo = null;

        itemView.setOnClickListener(null);
        itemView.setOnLongClickListener(null);

        // Application context: the holder may outlive its activity
        Glide.with(itemView.getContext().getApplicationContext()).clear(mThumbnail);
        mThumbnail.setImageDrawable(null);
    }

    /**
     * Currently bound video or null.
     */
    public Video getVideo() {
        return mVideo;
    }

    private void bindBadges(Context context, Video video) {
        String badgeText;
        boolean isRedBadge;

        if (video.isLive) {
            badgeText = context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.badge_live);
            isRedBadge = true;
        } else if (video.isShorts) {
            badgeText = context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.header_shorts).toUpperCase();
            isRedBadge = false;
        } else {
            badgeText = video.badge; // duration text usually
            isRedBadge = video.isUpcoming;
        }

        if (TextUtils.isEmpty(badgeText)) {
            mBadge.setVisibility(View.GONE);
        } else {
            mBadge.setVisibility(View.VISIBLE);
            mBadge.setText(badgeText);
            mBadge.setBackgroundResource(isRedBadge ? R.drawable.shared_badge_live_bg : R.drawable.shared_badge_bg);
        }

        mNewDot.setVisibility(video.hasNewContent ? View.VISIBLE : View.GONE);
    }

    private void bindProgress(Video video) {
        // Count progress that very close to zero. E.g. when user closed video immediately.
        int progress = video.percentWatched > 0 && video.percentWatched < 1 ? 1 : Math.round(video.percentWatched);

        if (progress > 0) {
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setMax(100);
            mProgress.setProgress(Math.min(progress, 100));
        } else {
            mProgress.setVisibility(View.GONE);
        }
    }

    private void bindThumbnail(Context context, Video video) {
        if (context instanceof Activity && ((Activity) context).isDestroyed()) {
            // Glide.with(context): IllegalArgumentException: You cannot start a load for a destroyed activity
            return;
        }

        Glide.with(context)
                .load(video.getCardImageUrl())
                .apply(glideOptions())
                .placeholder(R.drawable.shared_card_placeholder)
                .error(
                        // Alt thumbnail url not found. Fallback to the always working one.
                        Glide.with(context)
                                .load(video.cardImageUrl)
                                .apply(glideOptions())
                                .error(R.drawable.shared_card_placeholder)
                )
                .into(mThumbnail);
    }

    private static RequestOptions glideOptions() {
        return new RequestOptions()
                .skipMemoryCache(true) // mirror TV: ensure animated thumbs restart
                // Cache makes app crashing on old android versions
                .diskCacheStrategy(VERSION.SDK_INT > 21 ? DiskCacheStrategy.ALL : DiskCacheStrategy.NONE);
    }
}
