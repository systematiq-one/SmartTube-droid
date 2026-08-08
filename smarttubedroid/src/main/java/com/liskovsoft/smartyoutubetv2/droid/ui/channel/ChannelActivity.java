package com.liskovsoft.smartyoutubetv2.droid.ui.channel;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoRowsAdapter;

/**
 * Channel screen: vertical list of titled rows (Uploads, Playlists, Live now...).<br/>
 * Mirrors TV's {@code ChannelFragment} (MultipleRowsFragment) with a touch RecyclerView UI.<br/>
 * All data logic lives in {@link ChannelPresenter}; this class only renders
 * {@link VideoGroup} deltas and forwards user input.
 */
public class ChannelActivity extends DroidActivity implements ChannelView, VideoGroupAdapter.Listener {
    private ChannelPresenter mPresenter;
    private VideoRowsAdapter mRowsAdapter;
    private RecyclerView mRowsList;
    private LinearLayoutManager mLayoutManager;
    private ProgressBar mProgressBar;
    private TextView mTitleView;
    private boolean mIsActivityCreated;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.channel_activity);

        mIsActivityCreated = true;
        mPresenter = ChannelPresenter.instance(this);

        initViews();

        // Presenter lifecycle (design rule 8)
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        updateTitle();
    }

    private void initViews() {
        mTitleView = findViewById(R.id.channel_title);
        mProgressBar = findViewById(R.id.channel_progress);

        mRowsList = findViewById(R.id.channel_rows_list);
        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRowsList.setLayoutManager(mLayoutManager);
        mRowsAdapter = new VideoRowsAdapter(this);
        mRowsList.setAdapter(mRowsAdapter);

        ImageButton backButton = findViewById(R.id.channel_back_button);
        backButton.setOnClickListener(v -> onBackPressed());

        ImageButton searchButton = findViewById(R.id.channel_search_button);
        searchButton.setOnClickListener(v -> showSearchDialog());

        ImageButton sortButton = findViewById(R.id.channel_sort_button);
        sortButton.setOnClickListener(v -> mPresenter.onSearchSettingsClicked());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Skip the resume that immediately follows onViewInitialized (TV's ChannelFragment does the same)
        if (!mIsActivityCreated) {
            mPresenter.onViewResumed();
        }

        mIsActivityCreated = false;

        updateTitle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onBackPressed() {
        // User explicitly leaves the screen: let the presenter drop its cache
        // (TV routes this through LeanbackActivity.finish() -> finishReally() -> onFinish())
        mPresenter.onFinish();
        super.onBackPressed();
    }

    @Override
    public void finishReally() {
        super.finishReally();
        mPresenter.onFinish();
    }

    // ChannelView

    @Override
    public void update(VideoGroup group) {
        if (mRowsAdapter != null && group != null) {
            // Rows are keyed by group id inside the adapter; ACTION_REPLACE groups with
            // fixed ids (112 search results, 144 sorted uploads) replace that row in place.
            mRowsAdapter.update(group);
        }

        // Channel metadata may arrive after the first rows
        updateTitle();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0 || mRowsList == null) {
            return;
        }

        // Post: let a just-delivered update() pass through layout first
        mRowsList.post(() -> {
            int count = mRowsAdapter.getItemCount();

            if (count == 0) {
                return;
            }

            mLayoutManager.scrollToPositionWithOffset(Math.min(index, count - 1), 0);
        });
    }

    @Override
    public void clear() {
        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // VideoGroupAdapter.Listener (shared-ui contract)

    @Override
    public void onVideoClicked(Video item) {
        mPresenter.onVideoItemClicked(item);
    }

    @Override
    public void onVideoLongClicked(Video item) {
        // TV selects the card before opening the context menu; keep the order
        mPresenter.onVideoItemSelected(item);
        mPresenter.onVideoItemLongClicked(item);
    }

    @Override
    public void onScrollEnd(Video lastItem) {
        // Must be the last ADAPTER item: the presenter derives the continuation
        // from lastItem.getGroup().getMediaGroup()
        mPresenter.onScrollEnd(lastItem);
    }

    // Internals

    /**
     * Same info line as TV's ChannelHeaderPresenter: "author • subscribers"
     */
    private void updateTitle() {
        if (mTitleView == null) {
            return;
        }

        String title = null;

        Video channel = mPresenter.getChannel();

        if (channel == null) {
            String channelId = mPresenter.getChannelId();
            title = Helpers.startsWith(channelId, "@") ? channelId : null;
        } else {
            String author = channel.getAuthor();
            String name = channel.getTitle();
            String subs = channel.subscriberCount;

            title = Helpers.toString(YouTubeHelper.createInfo(Helpers.firstNonNull(author, name), subs));
        }

        if (!TextUtils.isEmpty(title)) {
            mTitleView.setText(title);
        }
    }

    private void showSearchDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.channel_dialog_search, null);
        TextInputEditText searchField = content.findViewById(R.id.channel_search_edit);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.channel_search_in_channel)
                .setView(content)
                .setPositiveButton(R.string.action_search, (dlg, which) -> submitSearch(searchField))
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(searchField);
                dialog.dismiss();
                return true;
            }

            return false;
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        dialog.show();
        searchField.requestFocus();
    }

    private void submitSearch(TextInputEditText searchField) {
        CharSequence text = searchField.getText();
        String query = text != null ? text.toString().trim() : "";

        if (!query.isEmpty()) {
            mPresenter.onSearchSubmit(query);
        }
    }
}
