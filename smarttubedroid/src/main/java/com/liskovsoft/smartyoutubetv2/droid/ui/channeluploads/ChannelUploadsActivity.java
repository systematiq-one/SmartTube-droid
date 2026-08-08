package com.liskovsoft.smartyoutubetv2.droid.ui.channeluploads;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;

/**
 * Flat grid of a single channel's uploads or a playlist's content.<br/>
 * Touch counterpart of TV's {@code ChannelUploadsFragment}. All data logic lives in
 * {@link ChannelUploadsPresenter}; this class renders {@link VideoGroup} deltas
 * (including APPEND continuations of the same group instance) via the shared adapter.
 */
public class ChannelUploadsActivity extends DroidActivity implements ChannelUploadsView, VideoGroupAdapter.Listener {
    private static final int GRID_COLUMNS = 2;
    private ChannelUploadsPresenter mPresenter;
    private VideoGroupAdapter mGridAdapter;
    private RecyclerView mGridView;
    private ProgressBar mProgressBar;
    private TextView mTitleView;
    private boolean mIsActivityCreated;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.channel_uploads_activity);

        mIsActivityCreated = true;
        mPresenter = ChannelUploadsPresenter.instance(this);

        initViews();

        // Presenter lifecycle (design rule 8)
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        updateTitle(null);
    }

    private void initViews() {
        mTitleView = findViewById(R.id.channel_uploads_title);
        mProgressBar = findViewById(R.id.channel_uploads_progress);

        mGridView = findViewById(R.id.channel_uploads_grid);
        mGridView.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        mGridAdapter = new VideoGroupAdapter(this); // grid card style
        mGridView.setAdapter(mGridAdapter);

        ImageButton backButton = findViewById(R.id.channel_uploads_back_button);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Skip the resume that immediately follows onViewInitialized (mirrors TV fragments)
        if (!mIsActivityCreated) {
            mPresenter.onViewResumed();
        }

        mIsActivityCreated = false;
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
        mPresenter.onFinish();
        super.onBackPressed();
    }

    @Override
    public void finishReally() {
        super.finishReally();
        mPresenter.onFinish();
    }

    // ChannelUploadsView

    @Override
    public void update(VideoGroup group) {
        if (mGridAdapter != null && group != null) {
            mGridAdapter.update(group);
        }

        updateTitle(group);
    }

    @Override
    public void clear() {
        if (mGridAdapter != null) {
            mGridAdapter.clear();
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
     * The presenter copies the channel/playlist title onto the base group
     * (see ChannelUploadsPresenter.update(MediaGroup)); fall back to the source item.
     */
    private void updateTitle(@Nullable VideoGroup group) {
        if (mTitleView == null) {
            return;
        }

        String title = group != null ? group.getTitle() : null;

        if (TextUtils.isEmpty(title)) {
            Video channel = mPresenter.getChannel();

            if (channel != null) {
                title = Helpers.firstNonNull(channel.getTitle(), channel.getAuthor());
            }
        }

        if (!TextUtils.isEmpty(title)) {
            mTitleView.setText(title);
        }
    }
}
