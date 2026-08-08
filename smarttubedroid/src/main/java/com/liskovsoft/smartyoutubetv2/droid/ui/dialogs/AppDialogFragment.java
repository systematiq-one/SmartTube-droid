package com.liskovsoft.smartyoutubetv2.droid.ui.dialogs;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.droid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The one and only {@link AppDialogView} implementation of the touch UI. It renders ALL 13
 * settings categories, every video/section context menu and every player overlay dialog, because
 * all of them funnel through {@code AppDialogPresenter} and the 8 {@code OptionCategory} types.<br/>
 * <br/>
 * It MUST be a Fragment: {@code ViewManager.isVisible()} (used by
 * {@code AppDialogPresenter.isDialogShown()}) only understands Fragments.<br/>
 * <br/>
 * UI shape: a bottom-anchored rounded sheet over a dim scrim, backed by an internal STACK of
 * screens. Every {@code show()} call pushes a screen (nested dialogs re-enter {@code show()} while
 * the view is already up), tapping a category row pushes a screen, {@code goBack()} pops one.
 * This mirrors the child-fragment back stack of the TV {@code AppDialogFragment}.
 */
public class AppDialogFragment extends Fragment implements AppDialogView, AppDialogAdapter.Callback {
    private static final String TAG = AppDialogFragment.class.getSimpleName();
    private static final int SCRIM_COLOR = 0xB3000000;
    private static final int SCRIM_COLOR_TRANSPARENT = 0x4D000000;
    private static final int SCRIM_COLOR_OVERLAY = 0x00000000;
    private static final int SHEET_ALPHA = 255;
    private static final int SHEET_ALPHA_COMPACT = 235;
    private static final float COMPACT_MAX_HEIGHT_RATIO = 0.5f;
    private static final int SHEET_TOP_MARGIN_DP = 56;
    private static final int SHEET_ANIM_DURATION_MS = 180;

    private final List<Screen> mStack = new ArrayList<>();
    private AppDialogPresenter mPresenter;
    private AppDialogAdapter mAdapter;
    private View mScrim;
    private LinearLayout mSheet;
    private TextView mTitleView;
    private RecyclerView mRecyclerView;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private boolean mIsPaused;
    private int mId;

    /** One rendered screen of the internal back stack. */
    private static class Screen {
        final CharSequence title;
        final List<AppDialogAdapter.Item> items;
        final int radioSelection;

        Screen(CharSequence title, List<AppDialogAdapter.Item> items, int radioSelection) {
            this.title = title;
            this.items = items;
            this.radioSelection = radioSelection;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = AppDialogPresenter.instance(getActivity());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mScrim = view.findViewById(R.id.dialog_scrim);
        mSheet = view.findViewById(R.id.dialog_sheet);
        mTitleView = view.findViewById(R.id.dialog_title);
        mRecyclerView = view.findViewById(R.id.dialog_list);

        // Like a real bottom sheet: tap outside to dismiss the whole dialog
        mScrim.setOnClickListener(v -> finish());
        // Swallow taps so they don't reach the scrim
        mSheet.setOnClickListener(v -> { });

        mAdapter = new AppDialogAdapter(getActivity(), this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mAdapter);

        // The scrim view does all the dimming, so the window shouldn't add its own
        // (the activity theme enables backgroundDim for the non-transparent case).
        Activity activity = getActivity();
        if (activity != null) {
            Window window = activity.getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        }

        applyStyle();
        animateSheetIn();

        if (mStack.isEmpty()) {
            // Fix the presenter losing the view between concurrent dialogs (same as TV)
            mPresenter.setView(this);
            // NOTE: AppDialogPresenter overrides onViewInitialized() and calls show() from it.
            // Must happen exactly once per dialog, hence the empty-stack guard.
            mPresenter.onViewInitialized();
        } else {
            // show() already arrived before the views existed
            render();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Workaround for dialogs that are destroyed with a delay (e.g. transparent ones)
        mIsPaused = false;
        mPresenter.onViewResumed();
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsPaused = true;
        mPresenter.onViewPaused();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Guard is mandatory: a newer dialog may already own the presenter, and
        // AppDialogPresenter.onViewDestroyed() wipes the pending dialog data.
        if (mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    // ------------------------------------------------------------------ AppDialogView

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable, boolean isTransparent,
                     boolean isOverlay, int id) {
        if (!Utils.checkActivity(getActivity())) {
            return;
        }

        if (categories == null) {
            // Nothing to render. Happens when the fragment is recreated (e.g. rotation) after the
            // presenter has already dropped its backup data - don't leave an empty sheet hanging.
            if (mStack.isEmpty()) {
                Log.d(TAG, "Empty dialog data. Closing.");
                finish();
            }
            return;
        }

        // Only the root screen may switch the whole dialog into transparent mode (TV contract)
        mIsTransparent = mStack.isEmpty() ? isTransparent : mIsTransparent;
        mIsOverlay = isOverlay;
        mId = id;

        Screen screen;

        if (isExpandable && categories.size() == 1) {
            // Most player dialogs and every nested picker rely on this: render the category
            // contents directly instead of a single row that opens them.
            screen = createCategoryScreen(categories.get(0), title);
        } else {
            screen = createRootScreen(categories, title);
        }

        mStack.add(screen);

        applyStyle();
        render();
    }

    /**
     * Routed through the activity so that the presenter gets {@code onFinish()} exactly once
     * (see {@code AppDialogActivity.finish()}). {@code AppDialogPresenter.onFinish()} runs the
     * dialog's onFinish callbacks and cancels the close timeout, so this path must not be
     * short-circuited.
     */
    @Override
    public void finish() {
        Activity activity = getActivity();

        if (activity instanceof AppDialogActivity) {
            ((AppDialogActivity) activity).finishFromView();
        } else if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public void goBack() {
        if (canGoBack()) {
            mStack.remove(mStack.size() - 1);
            render();
        } else {
            finish();
        }
    }

    @Override
    public void clearBackstack() {
        // Same effect as TV's "null the child fragment manager": the current screen stays
        // visible, but there's no history left, so the next back press closes the dialog.
        if (mStack.size() > 1) {
            Screen top = mStack.get(mStack.size() - 1);
            mStack.clear();
            mStack.add(top);
        }
    }

    @Override
    public boolean canGoBack() {
        return mStack.size() > 1;
    }

    @Override
    public boolean isShown() {
        return isVisible() && getUserVisibleHint();
    }

    @Override
    public boolean isTransparent() {
        return mIsTransparent;
    }

    @Override
    public boolean isOverlay() {
        return mIsOverlay;
    }

    @Override
    public boolean isPaused() {
        return mIsPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }

    // ------------------------------------------------------------------ AppDialogActivity hook

    /**
     * Called by the host activity on the final close. Runs the dialog's onFinish callbacks and
     * clears the presenter's close timeout.
     */
    void onFinish() {
        mPresenter.onFinish();
    }

    // ------------------------------------------------------------------ AppDialogAdapter.Callback

    @Override
    public void onCategoryClicked(OptionCategory category) {
        if (category == null) {
            return;
        }

        mStack.add(createCategoryScreen(category, category.title));
        render();
    }

    // ------------------------------------------------------------------ screen building

    /**
     * Root screen: one row per category. Switch/button categories are interactive right here
     * (TV renders them as inline preferences); list categories open a sub screen.
     */
    private Screen createRootScreen(List<OptionCategory> categories, CharSequence title) {
        List<AppDialogAdapter.Item> items = new ArrayList<>();

        for (OptionCategory category : categories) {
            if (category == null || category.options == null || category.options.isEmpty()) {
                continue;
            }

            switch (category.type) {
                case OptionCategory.TYPE_SINGLE_SWITCH:
                    items.add(AppDialogAdapter.Item.switchItem(category.options.get(0)));
                    break;
                case OptionCategory.TYPE_SINGLE_BUTTON:
                    items.add(AppDialogAdapter.Item.button(category.options.get(0)));
                    break;
                case OptionCategory.TYPE_CHAT:
                case OptionCategory.TYPE_COMMENTS:
                    items.add(createUnsupportedItem(category));
                    break;
                default:
                    items.add(AppDialogAdapter.Item.category(category, createCategorySummary(category)));
                    break;
            }
        }

        return new Screen(title, items, RecyclerView.NO_POSITION);
    }

    /** A category's contents rendered as a screen of its own. */
    private Screen createCategoryScreen(OptionCategory category, CharSequence fallbackTitle) {
        CharSequence title = category != null && category.title != null ? category.title : fallbackTitle;
        List<AppDialogAdapter.Item> items = new ArrayList<>();
        int radioSelection = RecyclerView.NO_POSITION;

        if (category == null || category.options == null) {
            return new Screen(title, items, radioSelection);
        }

        List<OptionItem> options = category.options;

        switch (category.type) {
            case OptionCategory.TYPE_RADIO_LIST:
                for (int i = 0; i < options.size(); i++) {
                    OptionItem option = options.get(i);
                    items.add(AppDialogAdapter.Item.radio(option));
                    if (radioSelection == RecyclerView.NO_POSITION && option.isSelected()) {
                        radioSelection = i;
                    }
                }
                break;
            case OptionCategory.TYPE_CHECKBOX_LIST:
                for (OptionItem option : options) {
                    items.add(AppDialogAdapter.Item.check(option));
                }
                break;
            case OptionCategory.TYPE_STRING_LIST:
                for (OptionItem option : options) {
                    items.add(AppDialogAdapter.Item.string(option));
                }
                break;
            case OptionCategory.TYPE_LONG_TEXT:
                if (!options.isEmpty()) {
                    items.add(AppDialogAdapter.Item.longText(options.get(0).getTitle()));
                }
                break;
            case OptionCategory.TYPE_SINGLE_SWITCH:
                if (!options.isEmpty()) {
                    items.add(AppDialogAdapter.Item.switchItem(options.get(0)));
                }
                break;
            case OptionCategory.TYPE_SINGLE_BUTTON:
                if (!options.isEmpty()) {
                    items.add(AppDialogAdapter.Item.button(options.get(0)));
                }
                break;
            case OptionCategory.TYPE_CHAT:
            case OptionCategory.TYPE_COMMENTS:
                items.add(createUnsupportedItem(category));
                break;
            default:
                Log.e(TAG, "Unknown category type: %s", category.type);
                break;
        }

        return new Screen(title, items, radioSelection);
    }

    /**
     * Live chat and comments aren't preference shaped at all (TV renders them with the chatkit
     * module driven by ChatReceiver/CommentsReceiver). Not supported by the touch UI in v1 -
     * shown as an inert row so the rest of the dialog keeps working.
     */
    private AppDialogAdapter.Item createUnsupportedItem(OptionCategory category) {
        CharSequence title = category.title != null ? category.title : getString(R.string.dialog_not_supported);
        return AppDialogAdapter.Item.unsupported(title, getString(R.string.dialog_not_supported));
    }

    /** Preference-style summary: the currently selected entry of a radio list. */
    private CharSequence createCategorySummary(OptionCategory category) {
        if (category.type != OptionCategory.TYPE_RADIO_LIST || category.options == null) {
            return null;
        }

        for (OptionItem option : category.options) {
            if (option.isSelected()) {
                return option.getTitle();
            }
        }

        return null;
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        if (mAdapter == null || mStack.isEmpty()) {
            return;
        }

        Screen screen = mStack.get(mStack.size() - 1);

        if (mTitleView != null) {
            if (TextUtils.isEmpty(screen.title)) {
                mTitleView.setVisibility(View.GONE);
            } else {
                mTitleView.setVisibility(View.VISIBLE);
                // Spannable, set as is
                mTitleView.setText(screen.title);
            }
        }

        mAdapter.setItems(screen.items, screen.radioSelection);

        if (mRecyclerView != null) {
            mRecyclerView.scrollToPosition(screen.radioSelection > 0 ? screen.radioSelection : 0);
        }
    }

    /**
     * Transparent/overlay dialogs float above the video player: dim less (or not at all) and keep
     * the sheet compact so the picture stays visible.
     */
    private void applyStyle() {
        if (mScrim == null || mSheet == null) {
            return;
        }

        boolean isCompact = mIsTransparent || mIsOverlay;

        mScrim.setBackgroundColor(mIsOverlay ? SCRIM_COLOR_OVERLAY : (mIsTransparent ? SCRIM_COLOR_TRANSPARENT : SCRIM_COLOR));

        ViewGroup.LayoutParams params = mSheet.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            int screenHeightPx = getResources().getDisplayMetrics().heightPixels;
            int topMargin = isCompact
                    ? (int) (screenHeightPx * COMPACT_MAX_HEIGHT_RATIO)
                    : (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, SHEET_TOP_MARGIN_DP,
                            getResources().getDisplayMetrics());
            ((FrameLayout.LayoutParams) params).topMargin = topMargin;
            mSheet.setLayoutParams(params);
        }

        Drawable background = mSheet.getBackground();
        if (background != null) {
            background.mutate().setAlpha(isCompact ? SHEET_ALPHA_COMPACT : SHEET_ALPHA);
        }
    }

    private void animateSheetIn() {
        final LinearLayout sheet = mSheet;

        if (sheet == null) {
            return;
        }

        sheet.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                sheet.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                sheet.setTranslationY(sheet.getHeight());
                sheet.setVisibility(View.VISIBLE);
                sheet.animate().translationY(0).setDuration(SHEET_ANIM_DURATION_MS).start();
            }
        });
    }
}
