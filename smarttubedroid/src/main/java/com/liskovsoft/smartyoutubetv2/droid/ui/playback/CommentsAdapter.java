package com.liskovsoft.smartyoutubetv2.droid.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.droid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the comments list of the player's details sheet.<br/>
 * Holds a flat list of {@link CommentItem}s plus an optional spinner footer that doubles as
 * the "load the next page" trigger: it asks the listener for more as soon as it's bound.
 */
public class CommentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_COMMENT = 0;
    private static final int TYPE_LOADING = 1;

    public interface Listener {
        /** Tap on a comment that has replies. */
        void onCommentClicked(CommentItem item);
        /** The footer became visible — fetch the next page. */
        void onLoadMore();
    }

    private final List<CommentItem> mItems = new ArrayList<>();
    private final Listener mListener;
    private boolean mHasMore;

    public CommentsAdapter(Listener listener) {
        mListener = listener;
    }

    public void clear() {
        mItems.clear();
        mHasMore = false;

        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    /**
     * Snapshot of the rows on screen, so the caller can stash a level of the list
     * while the replies of one of its comments are shown.
     */
    public List<CommentItem> getItems() {
        return new ArrayList<>(mItems);
    }

    /**
     * Appends a page. {@code hasMore} keeps the spinner footer alive so the list asks for
     * the page after this one once the user scrolls that far.
     */
    public void append(List<CommentItem> items, boolean hasMore) {
        if (items != null) {
            mItems.addAll(items);
        }

        mHasMore = hasMore;

        // The footer both appears and disappears here, so the cheap targeted
        // notifications would have to special-case every combination
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size() + (mHasMore ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        return position < mItems.size() ? TYPE_COMMENT : TYPE_LOADING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_LOADING) {
            return new LoadingHolder(inflater.inflate(R.layout.playback_comment_loading, parent, false));
        }

        return new CommentHolder(inflater.inflate(R.layout.playback_comment_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CommentHolder) {
            ((CommentHolder) holder).bind(mItems.get(position), mListener);
            return;
        }

        // The spinner is only ever bound once the user has scrolled down to it
        if (mListener != null) {
            mListener.onLoadMore();
        }
    }

    private static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View itemView) {
            super(itemView);
        }
    }

    private static class CommentHolder extends RecyclerView.ViewHolder {
        private final ImageView mAvatar;
        private final TextView mAuthor;
        private final TextView mMessage;
        private final View mFooter;
        private final ImageView mLikeIcon;
        private final TextView mLikeCount;
        private final TextView mReplies;

        CommentHolder(View itemView) {
            super(itemView);

            mAvatar = itemView.findViewById(R.id.playback_comment_avatar);
            mAuthor = itemView.findViewById(R.id.playback_comment_author);
            mMessage = itemView.findViewById(R.id.playback_comment_message);
            mFooter = itemView.findViewById(R.id.playback_comment_footer);
            mLikeIcon = itemView.findViewById(R.id.playback_comment_like_icon);
            mLikeCount = itemView.findViewById(R.id.playback_comment_like_count);
            mReplies = itemView.findViewById(R.id.playback_comment_replies);
        }

        void bind(CommentItem item, Listener listener) {
            if (item == null) {
                return;
            }

            Context context = itemView.getContext();

            mMessage.setText(item.getMessage());
            mAuthor.setText(joinNonEmpty(item.getAuthorName(), item.getPublishedDate()));
            mAuthor.setVisibility(TextUtils.isEmpty(mAuthor.getText()) ? View.GONE : View.VISIBLE);

            loadAvatar(context, item.getAuthorPhoto());

            // An empty item is the service's own "nothing here" placeholder: message only
            boolean isPlaceholder = item.isEmpty();

            String likeCount = item.getLikeCount();
            boolean hasLikes = !isPlaceholder && !TextUtils.isEmpty(likeCount);
            mLikeIcon.setVisibility(hasLikes ? View.VISIBLE : View.GONE);
            mLikeCount.setVisibility(hasLikes ? View.VISIBLE : View.GONE);
            mLikeCount.setText(likeCount);

            String replyCount = item.getReplyCount();
            boolean hasReplies = !isPlaceholder && !TextUtils.isEmpty(replyCount) && item.getNestedCommentsKey() != null;
            mReplies.setVisibility(hasReplies ? View.VISIBLE : View.GONE);
            mReplies.setText(hasReplies ? context.getString(R.string.playback_comment_replies, replyCount) : null);

            mFooter.setVisibility(hasLikes || hasReplies ? View.VISIBLE : View.GONE);

            itemView.setClickable(hasReplies);
            itemView.setOnClickListener(hasReplies ? v -> {
                if (listener != null) {
                    listener.onCommentClicked(item);
                }
            } : null);
        }

        private void loadAvatar(Context context, String url) {
            if (TextUtils.isEmpty(url) || isDestroyed(context)) {
                mAvatar.setImageDrawable(null);
                return;
            }

            Glide.with(context)
                    .load(url)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.playback_channel_placeholder)
                    .error(R.drawable.playback_channel_placeholder)
                    .into(mAvatar);
        }

        /** Glide throws when handed a finishing activity (binds can outlive it). */
        private static boolean isDestroyed(Context context) {
            if (!(context instanceof Activity)) {
                return false;
            }

            Activity activity = (Activity) context;

            return activity.isFinishing() || (VERSION.SDK_INT >= 17 && activity.isDestroyed());
        }

        private static String joinNonEmpty(String first, String second) {
            if (TextUtils.isEmpty(first)) {
                return TextUtils.isEmpty(second) ? null : second;
            }

            return TextUtils.isEmpty(second) ? first : first + "  •  " + second;
        }
    }
}
