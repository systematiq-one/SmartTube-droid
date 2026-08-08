package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.os.Parcelable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.droid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list of titled horizontal video rows (mirror of TV's MultipleRowsFragment).<br/>
 * Fed with {@link VideoGroup} deltas keyed by {@code group.getId()}: one inner
 * {@link VideoGroupAdapter} (row card style) per group id; a continuation with a known id is
 * appended to the existing row, an unknown id creates a new row.<br/>
 * Row layout: {@code shared_video_row.xml}.
 */
public class VideoRowsAdapter extends RecyclerView.Adapter<VideoRowsAdapter.RowHolder> {
    private final VideoGroupAdapter.Listener mListener;
    private final List<RowEntry> mRows = new ArrayList<>();
    // Card views are identical across rows: share one pool between all inner lists
    private final RecyclerView.RecycledViewPool mCardPool = new RecyclerView.RecycledViewPool();

    public VideoRowsAdapter(VideoGroupAdapter.Listener listener) {
        mListener = listener;
    }

    /**
     * Routes the delta to the row with {@code group.getId()}, creates the row if new.
     */
    public void update(VideoGroup group) {
        if (group == null) {
            return;
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear(); // empty REPLACE group == clear the whole fragment
            } else {
                removeRow(group.getId()); // positioned replace: swap a single row (e.g. channel search)
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            RowEntry entry = findRow(group.getId());
            if (entry != null) {
                entry.adapter.update(group);
                if (entry.adapter.isEmpty()) {
                    removeRow(group.getId());
                }
            }
            return;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            // The author's videos may sit in any row
            for (int i = mRows.size() - 1; i >= 0; i--) {
                RowEntry entry = mRows.get(i);
                entry.adapter.update(group);
                if (entry.adapter.isEmpty()) {
                    removeRowAt(i);
                }
            }
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            RowEntry entry = findRow(group.getId());
            if (entry != null) {
                entry.adapter.update(group);
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        RowEntry entry = findRow(group.getId());

        if (entry == null) {
            VideoGroupAdapter rowAdapter = new VideoGroupAdapter(mListener, true);
            entry = new RowEntry(group.getId(), group.getTitle(), rowAdapter);
            rowAdapter.update(group);

            int position = group.getPosition();
            if (position < 0 || position > mRows.size()) {
                mRows.add(entry);
                notifyItemInserted(mRows.size() - 1);
            } else {
                mRows.add(position, entry);
                notifyItemInserted(position);
            }
        } else {
            entry.adapter.update(group); // continue: same instance appends only the new tail

            if (!TextUtils.isEmpty(group.getTitle()) && !TextUtils.equals(entry.title, group.getTitle())) {
                entry.title = group.getTitle();
                notifyItemChanged(mRows.indexOf(entry));
            }
        }
    }

    public void clear() {
        int rowCount = mRows.size();
        mRows.clear();

        if (rowCount != 0) {
            notifyItemRangeRemoved(0, rowCount);
        }
    }

    public boolean isEmpty() {
        return mRows.isEmpty();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.shared_video_row, parent, false);

        RowHolder holder = new RowHolder(view);

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(parent.getContext(), RecyclerView.HORIZONTAL, false);
        layoutManager.setInitialPrefetchItemCount(4);
        holder.mList.setLayoutManager(layoutManager);
        holder.mList.setRecycledViewPool(mCardPool);
        holder.mList.setNestedScrollingEnabled(false);

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        RowEntry entry = mRows.get(position);
        holder.mEntry = entry;

        if (TextUtils.isEmpty(entry.title)) {
            holder.mTitle.setVisibility(View.GONE);
        } else {
            holder.mTitle.setVisibility(View.VISIBLE);
            holder.mTitle.setText(entry.title);
        }

        if (holder.mList.getAdapter() != entry.adapter) {
            holder.mList.setAdapter(entry.adapter);
        }

        RecyclerView.LayoutManager layoutManager = holder.mList.getLayoutManager();
        if (layoutManager != null) {
            if (entry.scrollState != null) {
                layoutManager.onRestoreInstanceState(entry.scrollState);
            } else {
                layoutManager.scrollToPosition(0);
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull RowHolder holder) {
        // Remember the horizontal scroll position of the recycled row
        if (holder.mEntry != null && holder.mList.getLayoutManager() != null) {
            holder.mEntry.scrollState = holder.mList.getLayoutManager().onSaveInstanceState();
            holder.mEntry = null;
        }

        holder.mList.setAdapter(null); // release the card holders into the shared pool
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    private RowEntry findRow(int groupId) {
        for (RowEntry entry : mRows) {
            if (entry.groupId == groupId) {
                return entry;
            }
        }

        return null;
    }

    private void removeRow(int groupId) {
        for (int i = 0; i < mRows.size(); i++) {
            if (mRows.get(i).groupId == groupId) {
                removeRowAt(i);
                return;
            }
        }
    }

    private void removeRowAt(int index) {
        mRows.remove(index);
        notifyItemRemoved(index);
    }

    /**
     * One titled horizontal row backed by its own {@link VideoGroupAdapter}.
     */
    private static final class RowEntry {
        private final int groupId;
        private String title;
        private final VideoGroupAdapter adapter;
        private Parcelable scrollState;

        RowEntry(int groupId, String title, VideoGroupAdapter adapter) {
            this.groupId = groupId;
            this.title = title;
            this.adapter = adapter;
        }
    }

    public static final class RowHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final RecyclerView mList;
        private RowEntry mEntry;

        RowHolder(View itemView) {
            super(itemView);

            mTitle = itemView.findViewById(R.id.shared_row_title);
            mList = itemView.findViewById(R.id.shared_row_list);
        }
    }
}
