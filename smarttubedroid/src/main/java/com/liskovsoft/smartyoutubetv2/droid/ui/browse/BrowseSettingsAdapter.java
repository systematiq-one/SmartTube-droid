package com.liskovsoft.smartyoutubetv2.droid.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.droid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Settings section of the Browse screen (BrowseSection.TYPE_SETTINGS_GRID).<br/>
 * Renders the {@link SettingsItem} list delivered through
 * {@code BrowseView.updateSection(SettingsGroup)} as simple icon+title rows.
 * Row click runs the item's own {@link SettingsItem#onClick} callback (the presenter
 * side opens the corresponding settings dialog).
 */
public class BrowseSettingsAdapter extends RecyclerView.Adapter<BrowseSettingsAdapter.SettingsViewHolder> {
    private final List<SettingsItem> mItems = new ArrayList<>();

    public void setItems(List<SettingsItem> items) {
        mItems.clear();

        if (items != null) {
            mItems.addAll(items);
        }

        notifyDataSetChanged();
    }

    public void clear() {
        setItems(null);
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    @NonNull
    @Override
    public SettingsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.browse_settings_item, parent, false);

        return new SettingsViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SettingsViewHolder holder, int position) {
        SettingsItem item = mItems.get(position);

        holder.title.setText(item.title);

        if (item.imageResId > 0) {
            holder.icon.setImageResource(item.imageResId);
            holder.icon.setVisibility(View.VISIBLE);
        } else {
            holder.icon.setImageDrawable(null);
            // Keep the slot (invisible, not gone) so titles stay aligned
            holder.icon.setVisibility(View.INVISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (item.onClick != null) {
                item.onClick.run();
            }
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;

        SettingsViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.browse_settings_item_icon);
            title = itemView.findViewById(R.id.browse_settings_item_title);
        }
    }
}
