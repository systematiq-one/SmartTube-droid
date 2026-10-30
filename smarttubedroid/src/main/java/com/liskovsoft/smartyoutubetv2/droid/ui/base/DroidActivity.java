package com.liskovsoft.smartyoutubetv2.droid.ui.base;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity;

/**
 * Base activity for all touch UI screens.
 * Extends MotherActivity because ViewManager casts to it (properlyFinishTheApp,
 * addOnResult/addOnPermissions plumbing used by sign-in and backup flows).
 */
public abstract class DroidActivity extends MotherActivity {
    // NOTE: the screensaver is turned off app wide in DroidApplication. A per-activity
    // setBlocked(true) doesn't hold: BasePresenter.enableScreenOffIfNeeded() clears it.

    /** The main task: shown in Recents */
    protected static final int RECENTS_MAIN = 0;
    /** The player: while it's in front, the only entry of the app in Recents */
    protected static final int RECENTS_SOLE = 1;
    /** Left as the manifest sets it (the dialogs are excluded there) */
    protected static final int RECENTS_IGNORE = 2;

    /** Started (visible) screens of the app, see {@link #onAppReturned()} */
    private static int sStartedCount;
    /** Every screen stopped: the user went to the home screen or another app */
    private static boolean sIsAppLeft;

    private boolean mInsetsHooked;

    @Override
    protected void onStart() {
        super.onStart();

        if (!isReturnTracked()) {
            return;
        }

        if (sStartedCount++ == 0 && sIsAppLeft) {
            sIsAppLeft = false;
            onAppReturned();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isReturnTracked()) {
            return;
        }

        sStartedCount = Math.max(sStartedCount - 1, 0);

        if (sStartedCount == 0 && !isChangingConfigurations()) {
            sIsAppLeft = true;
        }
    }

    /**
     * Whether this screen counts in {@link #onAppReturned()}. The splash screen doesn't: it handles
     * the return itself, see {@link #onAppReturnHandled()}.
     */
    protected boolean isReturnTracked() {
        return true;
    }

    /**
     * The return to the app needs no more handling (the splash screen opened the right screen).
     */
    protected static void onAppReturnHandled() {
        sIsAppLeft = false;
    }

    /**
     * The user came back from the home screen or another app. On TV the launcher always goes
     * through the splash screen, which drops the exit state and reopens the last screen
     * ({@code ViewManager.startDefaultView()}). Here the launcher just brings the main task back as
     * it was, so do the same:<br/>
     * - a stale move-to-back state (left by a video opened from another app) would send every
     * screen straight back to the background, and the app couldn't be opened again.<br/>
     * - the player runs in a task of its own, so leaving a video with HOME and tapping the
     * launcher icon would show the screen under the video instead of the video.
     */
    protected void onAppReturned() {
        getViewManager().resetExitState();

        if (getRecentsMode() == RECENTS_MAIN && getViewManager().getTopView() == PlaybackView.class) {
            PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(this);

            // A PIP window stays a PIP window: the user is back for the other screens
            if (playbackPresenter.getView() != null && !playbackPresenter.isInPipMode()) {
                startActivity(new Intent(this, PlaybackActivity.class));
                overridePendingTransition(0, 0);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Keep ViewManager's activity stack accurate (TV's LeanbackActivity does the same)
        getViewManager().addTop(this);

        // MotherActivity hides every system bar on each resume (fullscreen mode is on by
        // default), so the portrait override has to run after it
        applySystemBars();

        updateRecentsEntries();
    }

    /**
     * How this screen's task shows in Recents, see {@link #updateRecentsEntries()}.
     */
    protected int getRecentsMode() {
        return RECENTS_MAIN;
    }

    /**
     * One app, one Recents entry. The player runs in a task of its own (see the manifest), so while
     * it's in front the main task behind it is hidden from Recents, and any screen of the main task
     * shows it again. (The player's own task leaves Recents once it finishes: autoRemoveFromRecents.)
     */
    private void updateRecentsEntries() {
        int mode = getRecentsMode();

        if (mode == RECENTS_IGNORE) {
            return;
        }

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        if (manager == null) {
            return;
        }

        try {
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                boolean isOwnTask = getTaskId(task.getTaskInfo()) == getTaskId();

                if (mode == RECENTS_SOLE && !isOwnTask) {
                    task.setExcludeFromRecents(true);
                } else if (mode == RECENTS_MAIN && isOwnTask) {
                    task.setExcludeFromRecents(false);
                }
            }
        } catch (IllegalArgumentException | SecurityException e) {
            // A task went away meanwhile
        }
    }

    @SuppressWarnings("deprecation")
    private static int getTaskId(ActivityManager.RecentTaskInfo info) {
        return VERSION.SDK_INT >= 29 ? info.taskId : info.persistentId;
    }

    /**
     * BACK. The player and the dialogs run in tasks of their own (see the manifest), so Android's
     * back stack can't return to the video from a screen opened in the player, e.g. a channel.
     * Go through ViewManager's record of the opened screens instead, as TV's LeanbackActivity does.<br/>
     * BACK on the first screen while the sound plays in the background goes to the home screen and
     * leaves the app as it is: finishing would just uncover the player's task behind it.
     */
    @Override
    public void finish() {
        if (isBackPressed()) {
            if (getViewManager().hasParentView(this)) {
                getViewManager().startParentView(this);
            } else if (PlaybackPresenter.instance(this).isRunningInBackground()) {
                resetBackState();
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }

        super.finish();
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
        applyNavigationBarStyle(!isLandscape());
    }

    /**
     * The fullscreen helper makes the navigation bar translucent, which shows the system's gray
     * scrim behind the buttons (unnoticeable on dark, a gray band on light). While the bar is on
     * screen the content is inset above it anyway (see hookInsets), so give the bar the tonal
     * surface color instead, with dark buttons on light. A hidden bar (fullscreen) stays
     * translucent: swiped in, it shows over the content.
     */
    @SuppressWarnings("deprecation")
    protected void applyNavigationBarStyle(boolean isBarShown) {
        if (VERSION.SDK_INT < 21) {
            return;
        }

        Window window = getWindow();

        if (!isBarShown) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            return;
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.shared_surface));

        if (VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }

        boolean isLight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;

        if (VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();

            if (controller != null) {
                controller.setSystemBarsAppearance(isLight ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (VERSION.SDK_INT >= 26) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            decorView.setSystemUiVisibility(isLight ? flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
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
