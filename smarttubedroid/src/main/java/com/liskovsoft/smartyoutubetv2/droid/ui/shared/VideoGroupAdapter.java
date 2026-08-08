package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for one {@link VideoGroup} stream (mirror of TV's VideoGroupObjectAdapter).<br/>
 * Implements ALL VideoGroup delta actions:
 * <ul>
 * <li>{@code ACTION_REPLACE} — clear everything, then add the group</li>
 * <li>{@code ACTION_APPEND} — a continuation re-sends the SAME group instance with more videos;
 *     only {@code videos.subList(oldSize, newSize)} is appended (keyed on instance identity)</li>
 * <li>{@code ACTION_PREPEND} — add at the beginning</li>
 * <li>{@code ACTION_REMOVE} — remove all occurrences of the group's videos (match by equals)</li>
 * <li>{@code ACTION_REMOVE_AUTHOR} — remove every video of the same author</li>
 * <li>{@code ACTION_SYNC} — rebind changed items in place (match by equals)</li>
 * </ul>
 * Pagination: {@link Listener#onScrollEnd(Video)} fires when a position within
 * {@link #SCROLL_CONTINUE_NUM} items of the end gets bound. The LAST adapter item is passed —
 * presenters derive the continuation from {@code item.getGroup().getMediaGroup()}.
 */
public class VideoGroupAdapter extends RecyclerView.Adapter<VideoCardHolder> {
    private static final int SCROLL_CONTINUE_NUM = 6;

    public interface Listener {
        void onVideoClicked(Video item);
        void onVideoLongClicked(Video item);   // context menu
        void onScrollEnd(Video lastItem);      // pagination: called near list end
    }

    private final Listener mListener;
    private final boolean mIsRow;
    private final List<Video> mVideoItems = new ArrayList<>();
    // Number of videos already taken from each group, keyed by group INSTANCE.
    // Also keeps groups from being garbage collected (Video.group is a WeakReference).
    private final Map<VideoGroup, Integer> mConsumedByGroup = new IdentityHashMap<>();
    // Prevents onScrollEnd from firing multiple times for the same item count
    private int mLastScrollEndCount = -1;

    /**
     * Grid card style.
     */
    public VideoGroupAdapter(Listener listener) {
        this(listener, false);
    }

    /**
     * @param isRow true: small horizontal-row card style, false: grid card style
     */
    public VideoGroupAdapter(Listener listener, boolean isRow) {
        mListener = listener;
        mIsRow = isRow;
    }

    @NonNull
    @Override
    public VideoCardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return VideoCardHolder.create(parent, mIsRow);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoCardHolder holder, int position) {
        holder.bind(mVideoItems.get(position), mListener);

        checkScrollEnd(position);
    }

    @Override
    public void onViewRecycled(@NonNull VideoCardHolder holder) {
        holder.unbind();
    }

    @Override
    public int getItemCount() {
        return mVideoItems.size();
    }

    /**
     * Dispatches on {@code group.getAction()}.
     */
    public void update(VideoGroup group) {
        if (group == null) {
            return;
        }

        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                clear();
                append(group);
                break;
            case VideoGroup.ACTION_REMOVE:
                remove(group);
                break;
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                removeAuthor(group);
                break;
            case VideoGroup.ACTION_SYNC:
                sync(group);
                break;
            case VideoGroup.ACTION_PREPEND:
                prepend(group);
                break;
            case VideoGroup.ACTION_APPEND:
            default:
                append(group);
                break;
        }
    }

    public void clear() {
        int itemCount = mVideoItems.size();
        mVideoItems.clear();
        mConsumedByGroup.clear();
        mLastScrollEndCount = -1;

        if (itemCount != 0) {
            notifyItemRangeRemoved(0, itemCount);
        }
    }

    public boolean isEmpty() {
        return mVideoItems.isEmpty();
    }

    public Video getLastItem() {
        return mVideoItems.isEmpty() ? null : mVideoItems.get(mVideoItems.size() - 1);
    }

    private void append(VideoGroup group) {
        List<Video> videos = extractVideos(group);

        if (videos == null) {
            return;
        }

        int begin = mVideoItems.size();
        Integer consumed = mConsumedByGroup.get(group);

        if (consumed != null) {
            // Same group instance re-sent (continuation): append only the new tail
            if (videos.size() > consumed) {
                mVideoItems.addAll(videos.subList(consumed, videos.size()));
            }
        } else {
            mVideoItems.addAll(videos);
        }

        mConsumedByGroup.put(group, videos.size());

        int inserted = mVideoItems.size() - begin;
        if (inserted > 0) {
            mLastScrollEndCount = -1; // new page arrived: allow the next scroll-end event
            // Fix double item blinking by specifying exact range
            notifyItemRangeInserted(begin, inserted);
        }
    }

    private void prepend(VideoGroup group) {
        List<Video> videos = extractVideos(group);

        if (videos == null) {
            return;
        }

        Integer consumed = mConsumedByGroup.get(group);
        int inserted;

        if (consumed != null) {
            // Same group instance re-sent: prepend only the not yet consumed tail
            if (videos.size() > consumed) {
                List<Video> tail = videos.subList(consumed, videos.size());
                mVideoItems.addAll(0, tail);
                inserted = tail.size();
            } else {
                inserted = 0;
            }
        } else {
            mVideoItems.addAll(0, videos);
            inserted = videos.size();
        }

        mConsumedByGroup.put(group, videos.size());

        if (inserted > 0) {
            notifyItemRangeInserted(0, inserted);
        }
    }

    private void remove(VideoGroup group) {
        List<Video> videos = extractVideos(group);

        if (videos == null) {
            return;
        }

        removeVideos(new ArrayList<>(videos));
    }

    private void removeAuthor(VideoGroup group) {
        List<Video> videos = extractVideos(group);

        if (videos == null) {
            return;
        }

        String author = videos.get(0).getAuthor(); // assume same author

        if (author == null) {
            return;
        }

        List<Video> result = new ArrayList<>();

        for (Video video : mVideoItems) {
            if (Helpers.equals(author, video.getAuthor())) {
                result.add(video);
            }
        }

        removeVideos(result);
    }

    private void removeVideos(List<Video> videos) {
        for (Video video : videos) {
            int index;
            while ((index = mVideoItems.indexOf(video)) != -1) { // remove all occurrences
                mVideoItems.remove(index);
                notifyItemRemoved(index);
                removeFromGroup(video);
            }
        }
    }

    private void sync(VideoGroup group) {
        List<Video> videos = extractVideos(group);

        if (videos == null) {
            return;
        }

        for (Video video : videos) {
            // Search for multiple occurrences (e.g. History section)
            for (int i = 0; i < mVideoItems.size(); i++) {
                Video origin = mVideoItems.get(i);
                if (origin.equals(video)) {
                    origin.sync(video);
                    notifyItemChanged(i);
                }
            }
        }
    }

    private void removeFromGroup(Video video) {
        VideoGroup group = video.getGroup();

        if (group == null) {
            return;
        }

        int sizeBefore = group.getSize();
        group.remove(video);

        // Keep continuation indices aligned with the shrunk source list
        Integer consumed = mConsumedByGroup.get(group);
        if (consumed != null && consumed > 0 && group.getSize() < sizeBefore) {
            mConsumedByGroup.put(group, consumed - 1);
        }
    }

    private void checkScrollEnd(int position) {
        int itemCount = mVideoItems.size();

        if (mListener == null || itemCount == 0) {
            return;
        }

        if (position >= itemCount - SCROLL_CONTINUE_NUM && itemCount != mLastScrollEndCount) {
            mLastScrollEndCount = itemCount;
            mListener.onScrollEnd(getLastItem());
        }
    }

    /**
     * Null-safe access: {@code VideoGroup.getVideos()} throws NPE when the backing list is null
     * (e.g. groups made with {@code VideoGroup.copy()}), so check {@code isEmpty()} first.
     */
    private static List<Video> extractVideos(VideoGroup group) {
        return group == null || group.isEmpty() ? null : group.getVideos();
    }
}
