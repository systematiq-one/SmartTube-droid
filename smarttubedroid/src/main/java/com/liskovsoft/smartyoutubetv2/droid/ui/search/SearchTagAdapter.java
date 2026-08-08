package com.liskovsoft.smartyoutubetv2.droid.ui.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.droid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal chip list of search tags (history entries + query suggestions).<br/>
 * Fed by {@link com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider}
 * results (see {@link SearchActivity}).
 */
public class SearchTagAdapter extends RecyclerView.Adapter<SearchTagAdapter.TagHolder> {
    public interface Listener {
        void onTagClicked(Tag tag);
        void onTagLongClicked(Tag tag);
    }

    private final List<Tag> mTags = new ArrayList<>();
    private final Listener mListener;

    public SearchTagAdapter(Listener listener) {
        mListener = listener;
    }

    /**
     * Replaces the whole tag list. {@code null} or empty clears it.
     */
    public void setTags(List<Tag> tags) {
        mTags.clear();

        if (tags != null) {
            mTags.addAll(tags);
        }

        notifyDataSetChanged();
    }

    public void remove(Tag tag) {
        int index = mTags.indexOf(tag);

        if (index != -1) {
            mTags.remove(index);
            notifyItemRemoved(index);
        }
    }

    public void clear() {
        if (!mTags.isEmpty()) {
            mTags.clear();
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public TagHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView itemView = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.search_tag_item, parent, false);
        return new TagHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TagHolder holder, int position) {
        final Tag tag = mTags.get(position);

        holder.mTextView.setText(tag.tag);
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onTagClicked(tag);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (mListener != null) {
                mListener.onTagLongClicked(tag);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mTags.size();
    }

    static class TagHolder extends RecyclerView.ViewHolder {
        final TextView mTextView;

        TagHolder(@NonNull TextView itemView) {
            super(itemView);
            mTextView = itemView;
        }
    }
}
