package com.liskovsoft.smartyoutubetv2.droid.ui.browse;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textview.MaterialTextView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoRowsAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Home screen of the touch UI. Implements common's {@link BrowseView}:
 * a top toolbar (search/account/refresh), a horizontally scrollable {@link TabLayout}
 * of dynamic sections and a content area that swaps between a 2-column video grid,
 * a rows list and a settings list depending on {@link BrowseSection#getType()}.
 * All data comes from {@link BrowsePresenter} — this class contains zero business logic.
 */
public class BrowseActivity extends DroidActivity implements BrowseView {
    private static final String TAG = BrowseActivity.class.getSimpleName();
    private static final int GRID_COLUMNS = 2;

    private BrowsePresenter mBrowsePresenter;
    /** Mirrors the tab order. Index in this list == tab position. */
    private final List<BrowseSection> mSections = new ArrayList<>();
    private TabLayout mTabLayout;
    private RecyclerView mGridView;
    private RecyclerView mRowsView;
    private RecyclerView mSettingsView;
    private View mErrorContainer;
    private MaterialTextView mErrorMessage;
    private MaterialButton mErrorAction;
    private ProgressBar mProgressBar;
    private VideoGroupAdapter mGridAdapter;
    private VideoRowsAdapter mRowsAdapter;
    private BrowseSettingsAdapter mSettingsAdapter;
    private BrowseSection mCurrentSection;
    /** Ignore TabLayout callbacks while tabs are being (re)built programmatically. */
    private boolean mSuppressTabEvents;
    /** Skip the onViewResumed() that immediately follows onCreate (mirrors TV's BrowseFragment). */
    private boolean mJustCreated;

    private final VideoGroupAdapter.Listener mCardListener = new VideoGroupAdapter.Listener() {
        @Override
        public void onVideoClicked(Video item) {
            mBrowsePresenter.onVideoItemClicked(item);
        }

        @Override
        public void onVideoLongClicked(Video item) {
            // Selected first: keeps presenter's mCurrentVideo/multi-grid state in sync
            mBrowsePresenter.onVideoItemSelected(item);
            mBrowsePresenter.onVideoItemLongClicked(item);
        }

        @Override
        public void onScrollEnd(Video item) {
            mBrowsePresenter.onScrollEnd(item);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.browse_activity);

        mBrowsePresenter = BrowsePresenter.instance(this);
        mBrowsePresenter.setView(this);

        initToolbar();
        initTabs();
        initContent();

        mJustCreated = true;

        mBrowsePresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mJustCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mJustCreated = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        mBrowsePresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBrowsePresenter.onViewDestroyed();
    }

    @Override
    public void onBackPressed() {
        // YouTube-app-style back: first jump to the first section, then exit
        if (mTabLayout != null && mTabLayout.getSelectedTabPosition() > 0 && mTabLayout.getTabCount() > 0) {
            selectSection(0, false);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ init

    private void initToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.browse_toolbar);
        // Not an AppCompatActivity: inflate the menu on the standalone toolbar directly
        toolbar.inflateMenu(R.menu.browse_toolbar);
        toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked);
    }

    private boolean onToolbarItemClicked(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.browse_action_search) {
            SearchPresenter.instance(this).startSearch(null);
            return true;
        } else if (itemId == R.id.browse_action_account) {
            AccountSelectionPresenter.instance(this).show(true);
            return true;
        } else if (itemId == R.id.browse_action_refresh) {
            mBrowsePresenter.refresh();
            return true;
        }

        return false;
    }

    private void initTabs() {
        mTabLayout = findViewById(R.id.browse_tabs);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!mSuppressTabEvents) {
                    focusSection(tab.getPosition());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // NOP
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Tap on the current tab refreshes the section (mirrors TV's selectSection behavior)
                if (!mSuppressTabEvents) {
                    focusSection(tab.getPosition());
                }
            }
        });
    }

    private void initContent() {
        mGridView = findViewById(R.id.browse_grid);
        mRowsView = findViewById(R.id.browse_rows);
        mSettingsView = findViewById(R.id.browse_settings);
        mErrorContainer = findViewById(R.id.browse_error_container);
        mErrorMessage = findViewById(R.id.browse_error_message);
        mErrorAction = findViewById(R.id.browse_error_action);
        mProgressBar = findViewById(R.id.browse_progress);

        mGridAdapter = new VideoGroupAdapter(mCardListener);
        mGridView.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        mGridView.setAdapter(mGridAdapter);

        mRowsAdapter = new VideoRowsAdapter(mCardListener);
        mRowsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mRowsView.setAdapter(mRowsAdapter);

        mSettingsAdapter = new BrowseSettingsAdapter();
        mSettingsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mSettingsView.setAdapter(mSettingsAdapter);
    }

    // ---------------------------------------------------------- tab plumbing

    /**
     * A tab became active: remember the section, swap the content area to the matching
     * view type and let the presenter load the data.
     */
    private void focusSection(int position) {
        if (position < 0 || position >= mSections.size()) {
            return;
        }

        BrowseSection section = mSections.get(position);
        mCurrentSection = section;
        showContentForType(section.getType());

        mBrowsePresenter.onSectionFocused(section.getId());
    }

    private int indexOf(int sectionId) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == sectionId) {
                return i;
            }
        }

        return -1;
    }

    private TabLayout.Tab createTab(BrowseSection section) {
        TabLayout.Tab tab = mTabLayout.newTab();
        tab.setText(section.getTitle());
        // Section options (rename/move/unpin...) on long tap, like TV's header long-press
        tab.view.setOnLongClickListener(v -> {
            mBrowsePresenter.onSectionLongPressed(section.getId());
            return true;
        });

        return tab;
    }

    // ------------------------------------------------------------ BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        int existingIndex = indexOf(section.getId());

        // Already present at the requested spot — nothing to do (mirrors TV logic)
        if (existingIndex != -1 && (index == -1 || existingIndex == index)) {
            // Sync possibly changed title (sections can be renamed)
            TabLayout.Tab tab = mTabLayout.getTabAt(existingIndex);
            if (tab != null) {
                tab.setText(section.getTitle());
            }
            mSections.set(existingIndex, section);
            return;
        }

        removeSection(section);

        int insertIndex = (index < 0 || index > mSections.size()) ? mSections.size() : index;

        mSuppressTabEvents = true;
        try {
            mSections.add(insertIndex, section);
            // setSelected=false: never auto-select here; the presenter drives selection
            // through selectSection() after all sections are added
            mTabLayout.addTab(createTab(section), insertIndex, false);
        } finally {
            mSuppressTabEvents = false;
        }
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        int index = indexOf(section.getId());

        if (index == -1) {
            return;
        }

        mSuppressTabEvents = true;
        try {
            mSections.remove(index);
            mTabLayout.removeTabAt(index);

            if (mCurrentSection != null && mCurrentSection.getId() == section.getId()) {
                // TabLayout auto-selected a neighbor while events were suppressed — resync
                int selected = mTabLayout.getSelectedTabPosition();
                mCurrentSection = (selected >= 0 && selected < mSections.size()) ? mSections.get(selected) : null;
            }
        } finally {
            mSuppressTabEvents = false;
        }
    }

    @Override
    public void removeAllSections() {
        mSuppressTabEvents = true;
        try {
            mSections.clear();
            mTabLayout.removeAllTabs();
            mCurrentSection = null;
        } finally {
            mSuppressTabEvents = false;
        }
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || mTabLayout.getTabCount() == 0) {
            return;
        }

        // Fallback to the last section if index is above size (mirrors TV logic)
        int target = Math.min(index, mTabLayout.getTabCount() - 1);

        if (mTabLayout.getSelectedTabPosition() == target) {
            // Selecting the same tab fires no onTabSelected — refresh the section manually
            focusSection(target);
        } else {
            TabLayout.Tab tab = mTabLayout.getTabAt(target);
            if (tab != null) {
                tab.select(); // fires onTabSelected -> focusSection()
            }
        }

        if (focusOnContent) {
            focusOnContent();
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) {
            return;
        }

        int type = group.getSection() != null ? group.getSection().getType() :
                mCurrentSection != null ? mCurrentSection.getType() : BrowseSection.TYPE_GRID;

        // Content arrived — make sure the error state is gone (TV's restoreMainFragment analog)
        showContentForType(type);

        if (type == BrowseSection.TYPE_ROW) {
            mRowsAdapter.update(group);
        } else {
            // TYPE_GRID / TYPE_SHORTS_GRID / TYPE_MULTI_GRID all render as a 2-column grid.
            // MULTI_GRID's second column (uploads preview) is simplified away on the phone:
            // tapping a channel card opens it (BrowsePresenter handles that path itself).
            mGridAdapter.update(group);
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) {
            return;
        }

        showContentForType(BrowseSection.TYPE_SETTINGS_GRID);
        mSettingsAdapter.setItems(group.getItems());
    }

    @Override
    public void clearSection(BrowseSection section) {
        // TV clears whatever the current fragment shows; do the same for all content views
        mGridAdapter.clear();
        mRowsAdapter.clear();
        mSettingsAdapter.clear();
    }

    @Override
    public void selectSectionItem(int index) {
        if (index < 0) {
            return;
        }

        RecyclerView current = getCurrentContentView();

        if (current != null && current.getLayoutManager() != null && current.getAdapter() != null
                && index < current.getAdapter().getItemCount()) {
            current.getLayoutManager().scrollToPosition(index);
        }
    }

    @Override
    public void selectSectionItem(Video item) {
        // TV-only: restores DPAD focus on a particular card after boot/crash.
        // The shared adapter API exposes no Video->position lookup and phones
        // have no card focus concept, so this is a documented no-op.
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (data == null) {
            return;
        }

        showContentView(mErrorContainer);

        mErrorMessage.setText(data.getMessage());

        String actionText = data.getActionText();

        if (actionText != null) {
            mErrorAction.setText(actionText);
            mErrorAction.setVisibility(View.VISIBLE);
            mErrorAction.setOnClickListener(v -> data.onAction());
        } else {
            mErrorAction.setVisibility(View.GONE);
            mErrorAction.setOnClickListener(null);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // May be called from a background thread (TV posts to the main looper too)
        runOnUiThread(() -> mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE;
    }

    @Override
    public void focusOnContent() {
        // No DPAD focus on phones; just make sure the visible content gets view focus
        RecyclerView current = getCurrentContentView();

        if (current != null) {
            current.requestFocus();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mErrorContainer != null && mErrorContainer.getVisibility() == View.VISIBLE) {
            return true; // error state == no content
        }

        int type = mCurrentSection != null ? mCurrentSection.getType() : BrowseSection.TYPE_GRID;

        if (type == BrowseSection.TYPE_SETTINGS_GRID) {
            return mSettingsAdapter == null || mSettingsAdapter.isEmpty();
        } else if (type == BrowseSection.TYPE_ROW) {
            return mRowsAdapter == null || mRowsAdapter.isEmpty();
        }

        return mGridAdapter == null || mGridAdapter.isEmpty();
    }

    @Override
    public void updateBadge() {
        // TV-only: swaps the top-right Leanback badge drawable with the bridge app's icon.
        // The phone toolbar shows a fixed app title instead, so nothing to update.
    }

    // -------------------------------------------------------------- helpers

    /** Show the content view matching a section type, hiding all the others. */
    private void showContentForType(int sectionType) {
        switch (sectionType) {
            case BrowseSection.TYPE_ROW:
                showContentView(mRowsView);
                break;
            case BrowseSection.TYPE_SETTINGS_GRID:
                showContentView(mSettingsView);
                break;
            case BrowseSection.TYPE_ERROR:
                // Content stays as-is; the presenter follows up with showError()
                break;
            case BrowseSection.TYPE_GRID:
            case BrowseSection.TYPE_SHORTS_GRID:
            case BrowseSection.TYPE_MULTI_GRID:
            default:
                showContentView(mGridView);
                break;
        }
    }

    private void showContentView(View view) {
        mGridView.setVisibility(view == mGridView ? View.VISIBLE : View.GONE);
        mRowsView.setVisibility(view == mRowsView ? View.VISIBLE : View.GONE);
        mSettingsView.setVisibility(view == mSettingsView ? View.VISIBLE : View.GONE);
        mErrorContainer.setVisibility(view == mErrorContainer ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private RecyclerView getCurrentContentView() {
        if (mRowsView != null && mRowsView.getVisibility() == View.VISIBLE) {
            return mRowsView;
        } else if (mSettingsView != null && mSettingsView.getVisibility() == View.VISIBLE) {
            return mSettingsView;
        } else if (mGridView != null && mGridView.getVisibility() == View.VISIBLE) {
            return mGridView;
        }

        return null;
    }
}
