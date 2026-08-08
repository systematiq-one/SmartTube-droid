package com.liskovsoft.smartyoutubetv2.droid.ui.dialogs;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.droid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders one screen of {@link AppDialogFragment}'s internal stack.<br/>
 * Row types mirror the TV {@code AppPreferenceManager} type→widget mapping, including its
 * {@code getRequired()} warning and {@code getRadio()} mutual-exclusion logic, which lives in the
 * TV module only and therefore has to be re-implemented here.
 */
public class AppDialogAdapter extends RecyclerView.Adapter<AppDialogAdapter.ViewHolder> {
    /** Opens the category contents as a new screen (TV: a nested preference fragment). */
    public static final int TYPE_CATEGORY = 0;
    /** {@code OptionCategory.TYPE_RADIO_LIST} entry. */
    public static final int TYPE_RADIO = 1;
    /** {@code OptionCategory.TYPE_CHECKBOX_LIST} entry. */
    public static final int TYPE_CHECK = 2;
    /** {@code OptionCategory.TYPE_STRING_LIST} entry (checkbox semantics, no checkbox drawn). */
    public static final int TYPE_STRING = 3;
    /** {@code OptionCategory.TYPE_SINGLE_SWITCH}. */
    public static final int TYPE_SWITCH = 4;
    /** {@code OptionCategory.TYPE_SINGLE_BUTTON} (every context menu entry is one of these). */
    public static final int TYPE_BUTTON = 5;
    /** {@code OptionCategory.TYPE_LONG_TEXT}. */
    public static final int TYPE_LONG_TEXT = 6;
    /** {@code OptionCategory.TYPE_CHAT} / {@code TYPE_COMMENTS} placeholder. */
    public static final int TYPE_UNSUPPORTED = 7;

    public interface Callback {
        void onCategoryClicked(OptionCategory category);
    }

    public static class Item {
        public final int type;
        public final CharSequence title;
        public final CharSequence description;
        public final OptionItem option;
        public final OptionCategory category;

        private Item(int type, CharSequence title, CharSequence description, OptionItem option, OptionCategory category) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.option = option;
            this.category = category;
        }

        public static Item category(OptionCategory category, CharSequence summary) {
            return new Item(TYPE_CATEGORY, category.title, summary, null, category);
        }

        public static Item radio(OptionItem option) {
            return new Item(TYPE_RADIO, option.getTitle(), option.getDescription(), option, null);
        }

        public static Item check(OptionItem option) {
            return new Item(TYPE_CHECK, option.getTitle(), option.getDescription(), option, null);
        }

        public static Item string(OptionItem option) {
            return new Item(TYPE_STRING, option.getTitle(), option.getDescription(), option, null);
        }

        public static Item switchItem(OptionItem option) {
            return new Item(TYPE_SWITCH, option.getTitle(), option.getDescription(), option, null);
        }

        public static Item button(OptionItem option) {
            return new Item(TYPE_BUTTON, option.getTitle(), option.getDescription(), option, null);
        }

        public static Item longText(CharSequence text) {
            return new Item(TYPE_LONG_TEXT, text, null, null, null);
        }

        public static Item unsupported(CharSequence title, CharSequence description) {
            return new Item(TYPE_UNSUPPORTED, title, description, null, null);
        }
    }

    private final Context mContext;
    private final Callback mCallback;
    private List<Item> mItems = new ArrayList<>();
    /**
     * Radio lists keep their selection here instead of reading {@code OptionItem.isSelected()}:
     * selecting an entry only calls {@code onSelect(true)} on the new one (TV's ListPreference
     * behaves the same), so the previously selected item still reports {@code isSelected() == true}.
     */
    private int mRadioSelection = RecyclerView.NO_POSITION;

    public AppDialogAdapter(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    public void setItems(List<Item> items, int radioSelection) {
        mItems = items != null ? items : new ArrayList<Item>();
        mRadioSelection = radioSelection;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutResId;

        switch (viewType) {
            case TYPE_CATEGORY:
                layoutResId = R.layout.dialog_row_category;
                break;
            case TYPE_RADIO:
                layoutResId = R.layout.dialog_row_radio;
                break;
            case TYPE_CHECK:
                layoutResId = R.layout.dialog_row_check;
                break;
            case TYPE_SWITCH:
                layoutResId = R.layout.dialog_row_switch;
                break;
            case TYPE_LONG_TEXT:
                layoutResId = R.layout.dialog_row_long_text;
                break;
            default: // TYPE_STRING, TYPE_BUTTON, TYPE_UNSUPPORTED
                layoutResId = R.layout.dialog_row_button;
                break;
        }

        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layoutResId, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        Item item = mItems.get(position);

        // NOTE: titles are CharSequence Spannables (colored markers, italics). Never toString() them.
        if (holder.title != null) {
            holder.title.setText(item.title);
        }

        if (holder.description != null) {
            if (TextUtils.isEmpty(item.description)) {
                holder.description.setVisibility(View.GONE);
            } else {
                holder.description.setVisibility(View.VISIBLE);
                holder.description.setText(item.description);
            }
        }

        if (holder.widget != null) {
            if (item.type == TYPE_RADIO) {
                holder.widget.setChecked(position == mRadioSelection);
            } else {
                holder.widget.setChecked(item.option != null && item.option.isSelected());
            }
        }

        if (item.type == TYPE_LONG_TEXT || item.type == TYPE_UNSUPPORTED) {
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v -> onItemClicked(holder.getAdapterPosition()));
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    private void onItemClicked(int position) {
        if (position == RecyclerView.NO_POSITION || position >= mItems.size()) {
            return;
        }

        Item item = mItems.get(position);

        switch (item.type) {
            case TYPE_CATEGORY:
                if (mCallback != null) {
                    mCallback.onCategoryClicked(item.category);
                }
                break;
            case TYPE_RADIO:
                // TV mod (RadioListPreferenceDialogFragment.AdapterRadio): don't leave the screen
                // after the selection has been set. Only the new item gets a callback.
                mRadioSelection = position;
                if (item.option != null) {
                    item.option.onSelect(true);
                }
                notifyDataSetChanged();
                break;
            case TYPE_CHECK:
            case TYPE_STRING:
                // String lists are multi-select lists without a visible checkbox on TV, so they
                // share the very same toggle path (incl. required/radio enforcement).
                toggle(item.option);
                notifyDataSetChanged();
                break;
            case TYPE_SWITCH:
                if (item.option != null) {
                    item.option.onSelect(!item.option.isSelected());
                }
                notifyDataSetChanged();
                break;
            case TYPE_BUTTON:
                // Callbacks usually close the dialog themselves (AppDialogPresenter.closeDialog())
                // or push another dialog. Never close it here — TV doesn't either.
                if (item.option != null) {
                    item.option.onSelect(true);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Port of the {@code MultiSelectListPreference} change listener of TV's
     * {@code AppPreferenceManager.initMultiSelectListPreference}: warn about unchecked
     * dependencies, drop the mutually exclusive siblings, then flip the item itself.
     */
    private void toggle(OptionItem item) {
        if (item == null) {
            return;
        }

        boolean isSelected = !item.isSelected();

        if (isSelected) {
            OptionItem[] requiredItems = item.getRequired();

            if (requiredItems != null) {
                for (OptionItem requiredItem : requiredItems) {
                    if (requiredItem != null && !requiredItem.isSelected()) {
                        MessageHelpers.showMessage(mContext, mContext.getString(
                                com.liskovsoft.smartyoutubetv2.common.R.string.require_checked, requiredItem.getTitle()));
                    }
                }
            }

            OptionItem[] radioItems = item.getRadio();

            if (radioItems != null) {
                for (OptionItem radioItem : radioItems) {
                    if (radioItem != null) {
                        radioItem.onSelect(false);
                    }
                }
            }
        }

        item.onSelect(isSelected);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView description;
        private final Checkable widget;

        public ViewHolder(View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.dialog_row_title);
            description = itemView.findViewById(R.id.dialog_row_description);
            View widgetView = itemView.findViewById(R.id.dialog_row_widget);
            widget = widgetView instanceof Checkable ? (Checkable) widgetView : null;
        }
    }
}
