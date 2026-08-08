package com.liskovsoft.smartyoutubetv2.droid.ui.search;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;

import java.util.List;

/**
 * Touch search screen: app bar with query field, voice input and filters,
 * tag chips (history/suggestions) and a two-column results grid.<br/>
 * Mirrors TV's SearchTagsFragment/SearchTagsActivity pair
 * (smarttubetv/.../tv/ui/search/tags) on top of common's {@link SearchPresenter}.
 */
public class SearchActivity extends DroidActivity implements SearchView, VideoGroupAdapter.Listener, SearchTagAdapter.Listener {
    private static final String TAG = SearchActivity.class.getSimpleName();
    private static final int REQUEST_SPEECH = 0x00000010;

    private SearchPresenter mSearchPresenter;
    private EditText mSearchEdit;
    private ImageButton mClearButton;
    private RecyclerView mTagsList;
    private RecyclerView mResultsGrid;
    private ProgressBar mProgressBar;
    private VideoGroupAdapter mResultsAdapter;
    private SearchTagAdapter mTagAdapter;
    private MediaServiceSearchTagProvider mTagsProvider;

    /**
     * MotherActivity clears its result callbacks after each delivery,
     * so this is re-registered on every recognizer launch (see startVoiceRecognition).
     */
    private final OnResult mVoiceSearchResult = (requestCode, resultCode, data) -> {
        if (requestCode != REQUEST_SPEECH) {
            return;
        }

        if (resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && !TextUtils.isEmpty(results.get(0))) {
                startSearch(results.get(0));
            }
        } else {
            Log.i(TAG, "Voice recognition canceled or failed. Result code: " + resultCode);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.search_activity);

        mSearchPresenter = SearchPresenter.instance(this);
        mSearchPresenter.setView(this);

        initAppBar();
        initTagsList();
        initResultsGrid();

        mSearchPresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSearchPresenter.onViewResumed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSearchPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSearchPresenter.onViewDestroyed();
    }

    private void initAppBar() {
        mSearchEdit = findViewById(R.id.search_edit_text);
        mClearButton = findViewById(R.id.search_clear_button);
        ImageButton backButton = findViewById(R.id.search_back_button);
        ImageButton voiceButton = findViewById(R.id.search_voice_button);
        ImageButton filterButton = findViewById(R.id.search_filter_button);

        backButton.setOnClickListener(v -> onBackPressed());
        voiceButton.setOnClickListener(v -> startVoiceRecognition());
        filterButton.setOnClickListener(v -> mSearchPresenter.onSearchSettingsClicked());
        mClearButton.setOnClickListener(v -> {
            mSearchEdit.setText("");
            showKeyboard();
        });

        mSearchEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitSearch(getSearchText());
                return true;
            }

            return false;
        });

        // Mirrors TV's onQueryTextChange: refresh tag suggestions as the user types
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mClearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                loadTags(s.toString());
            }
        });
    }

    private void initTagsList() {
        mTagsList = findViewById(R.id.search_tags_list);
        mTagAdapter = new SearchTagAdapter(this);
        mTagsList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        mTagsList.setAdapter(mTagAdapter);
    }

    private void initResultsGrid() {
        mResultsGrid = findViewById(R.id.search_results_grid);
        mProgressBar = findViewById(R.id.search_progress);
        mResultsAdapter = new VideoGroupAdapter(this); // grid card style
        mResultsGrid.setLayoutManager(new GridLayoutManager(this, 2));
        mResultsGrid.setAdapter(mResultsAdapter);
    }

    // ------------------------------------------------------------------
    // SearchView contract
    // ------------------------------------------------------------------

    @Override
    public void updateSearch(VideoGroup group) {
        mResultsAdapter.update(group);
    }

    @Override
    public void clearSearch() {
        mResultsAdapter.clear();
    }

    @Override
    public void clearSearchTags() {
        mTagAdapter.clear();
    }

    @Override
    public void removeSearchTag(Tag tag) {
        mTagAdapter.remove(tag);
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
        loadTags(getSearchText());
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void startSearch(String searchText) {
        if (searchText != null) {
            mSearchEdit.setText(searchText);
            mSearchEdit.setSelection(mSearchEdit.getText().length());
            submitSearch(searchText);
        } else {
            mSearchEdit.selectAll();
            showKeyboard();
            loadTags(getSearchText());
        }
    }

    @Override
    public String getSearchText() {
        return mSearchEdit != null ? mSearchEdit.getText().toString() : null;
    }

    @Override
    public void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(com.liskovsoft.smartyoutubetv2.common.R.string.action_search));

        addOnResult(mVoiceSearchResult); // MotherActivity plumbing; cleared after each result

        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException | SecurityException | NullPointerException e) {
            // ActivityNotFoundException: no recognizer app installed
            // NullPointerException: recognizer can't obtain applicationInfo (seen on TV)
            Log.e(TAG, "Can't start voice recognition", e);
            MessageHelpers.showMessage(this, R.string.search_voice_unavailable);
        }
    }

    @Override
    public void finishReally() {
        // Plain finish: search is a child screen of the phone task
        // (MotherActivity's finishAndRemoveTask variant would kill the whole task)
        super.finish();

        // Same as TV's SearchTagsActivity.finishReally: reset presenter search state
        mSearchPresenter.onFinish();
    }

    // ------------------------------------------------------------------
    // VideoGroupAdapter.Listener (results grid)
    // ------------------------------------------------------------------

    @Override
    public void onVideoClicked(Video item) {
        mSearchPresenter.onVideoItemSelected(item); // track current video (crash restore)
        mSearchPresenter.onVideoItemClicked(item);
    }

    @Override
    public void onVideoLongClicked(Video item) {
        mSearchPresenter.onVideoItemSelected(item);
        mSearchPresenter.onVideoItemLongClicked(item);
    }

    @Override
    public void onScrollEnd(Video lastItem) {
        mSearchPresenter.onScrollEnd(lastItem);
    }

    // ------------------------------------------------------------------
    // SearchTagAdapter.Listener (tag chips)
    // ------------------------------------------------------------------

    @Override
    public void onTagClicked(Tag tag) {
        startSearch(tag.tag);
    }

    @Override
    public void onTagLongClicked(Tag tag) {
        mSearchPresenter.onTagLongClicked(tag);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void submitSearch(String searchText) {
        if (TextUtils.isEmpty(searchText)) {
            return;
        }

        hideKeyboard();

        SearchPresenter.instance(this).onSearch(searchText);
    }

    private void loadTags(String query) {
        if (mTagsProvider == null) {
            return;
        }

        // NOTE: provider results may arrive on a background thread
        mTagsProvider.search(query, results -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                mTagAdapter.setTags(results);
            }
        }));
    }

    private void showKeyboard() {
        mSearchEdit.requestFocus();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchEdit, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchEdit.getWindowToken(), 0);
        }

        mSearchEdit.clearFocus();
    }
}
