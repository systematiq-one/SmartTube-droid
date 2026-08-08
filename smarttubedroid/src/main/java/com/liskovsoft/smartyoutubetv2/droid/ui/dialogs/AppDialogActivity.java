package com.liskovsoft.smartyoutubetv2.droid.ui.dialogs;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;

/**
 * Universal dialog/settings host. A single transparent activity (theme
 * {@code Theme.SmartTubeDroid.Dialog}) that renders ALL settings screens, video context
 * menus and player overlay dialogs of the app.<br/>
 * The actual {@code AppDialogView} implementation lives in {@link AppDialogFragment}:
 * {@code ViewManager.isVisible()} (used by {@code AppDialogPresenter.isDialogShown()})
 * only recognizes Fragments, so the view MUST be a fragment — same interplay as the TV
 * {@code AppDialogActivity}/{@code AppDialogFragment} pair.
 */
public class AppDialogActivity extends DroidActivity {
    private static final String FRAGMENT_TAG = "dialog_app_dialog_fragment";
    private AppDialogFragment mFragment;
    private boolean mIsBackPressed;
    private boolean mIsFinishNotified;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_activity);

        FragmentManager manager = getSupportFragmentManager();
        mFragment = (AppDialogFragment) manager.findFragmentByTag(FRAGMENT_TAG);
        if (mFragment == null) {
            mFragment = new AppDialogFragment();
            manager.beginTransaction()
                    .add(R.id.dialog_fragment_container, mFragment, FRAGMENT_TAG)
                    .commitNow();
        }
    }

    @Override
    public void onBackPressed() {
        // Distinguish system back from other finish paths: back pops one dialog
        // screen first (TV AppDialogActivity contract)
        mIsBackPressed = true;
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsBackPressed = false;
    }

    /**
     * Presenter-initiated close ({@code closeDialog()}, close timeout, scrim tap):
     * closes the whole dialog, skipping the back-press pop-one-screen logic.
     */
    void finishFromView() {
        mIsBackPressed = false;
        finish();
    }

    /**
     * TV contract (see TV's AppDialogActivity.finish): every finish path funnels here.
     * System back pops one screen while a back stack exists; the final close notifies
     * the presenter ({@code AppDialogPresenter.onFinish()} — this is what runs the
     * dialog's onFinish callbacks and cancels the close timeout) and then really
     * finishes the activity.
     */
    @Override
    public void finish() {
        if (mFragment != null) {
            if (mIsBackPressed && mFragment.canGoBack()) {
                mIsBackPressed = false;
                mFragment.goBack();
                return;
            }

            if (!mIsFinishNotified) {
                mIsFinishNotified = true;
                mFragment.onFinish();
            }
        }

        // Destroy the dialog for real. noHistory alone isn't reliable.
        finishReally();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Dialogs don't survive leaving the app (TV contract). Prevents stale overlay
        // dialogs from hanging over nothing when the user returns.
        finish();
    }
}
