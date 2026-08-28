package com.liskovsoft.smartyoutubetv2.droid.ui.base;

import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.RequiresApi;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

/**
 * Base activity for all touch UI screens.
 * Extends MotherActivity because ViewManager casts to it (properlyFinishTheApp,
 * addOnResult/addOnPermissions plumbing used by sign-in and backup flows).
 */
public abstract class DroidActivity extends MotherActivity {
    // NOTE: the screensaver is turned off app wide in DroidApplication. A per-activity
    // setBlocked(true) doesn't hold: BasePresenter.enableScreenOffIfNeeded() clears it.

    private boolean mInsetsHooked;

    @Override
    protected void onResume() {
        super.onResume();

        // Keep ViewManager's activity stack accurate (TV's LeanbackActivity does the same)
        getViewManager().addTop(this);

        // MotherActivity hides every system bar on each resume (fullscreen mode is on by
        // default), so the portrait override has to run after it
        applySystemBars();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        applySystemBars();
    }

    @Override
    protected void initTheme() {
        // TV color-scheme themes are Leanback-based; the phone app uses its own Material theme
        // declared in the manifest, so skip the runtime theme override.
    }

    /**
     * The navigation bar (back/home/recents) stays on screen in portrait, so it doesn't have to
     * be swiped up first, and is hidden again in landscape, where the video fills the screen.
     * The status bar stays hidden either way, as before.
     */
    protected void applySystemBars() {
        setNavigationBarVisible(!isLandscape());
        hookInsets();
    }

    protected boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Note that {@code setSystemUiVisibility()} is a no-op once the window has been driven through
     * {@link WindowInsetsController} (which is what {@code Helpers.makeActivityFullscreen2()} does
     * on API 30+), so the bar has to be brought back through the very same controller.
     */
    @SuppressWarnings("deprecation")
    protected void setNavigationBarVisible(boolean visible) {
        if (VERSION.SDK_INT >= 30) {
            setNavigationBarVisible30(visible);
            return;
        }

        int hideNavigation = visible ? 0
                : View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        int sticky = visible || VERSION.SDK_INT < 19 ? 0 : View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(hideNavigation | sticky
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @RequiresApi(30)
    private void setNavigationBarVisible30(boolean visible) {
        WindowInsetsController controller = getWindow().getInsetsController();

        if (controller == null) {
            return;
        }

        if (visible) {
            controller.show(WindowInsets.Type.navigationBars());
        } else {
            controller.hide(WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    /**
     * Fullscreen mode draws edge to edge, so a visible navigation bar would sit on top of the
     * content. Inset the whole activity layout instead of touching every screen.
     */
    private void hookInsets() {
        if (mInsetsHooked) {
            return;
        }

        final View content = findViewById(android.R.id.content);

        if (content == null) {
            return;
        }

        mInsetsHooked = true;

        final int paddingBottom = content.getPaddingBottom();

        content.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    paddingBottom + (isLandscape() ? 0 : navigationBarInsetBottom(insets)));

            return insets;
        });

        content.requestApplyInsets();
    }

    /**
     * The navigation bar height alone — the deprecated system window insets would also carry the
     * soft keyboard, which the search screen already handles through {@code adjustResize}.
     */
    @SuppressWarnings("deprecation")
    private int navigationBarInsetBottom(WindowInsets insets) {
        if (VERSION.SDK_INT >= 30) {
            return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }

        return VERSION.SDK_INT >= 21 ? insets.getStableInsetBottom() : 0;
    }
}
