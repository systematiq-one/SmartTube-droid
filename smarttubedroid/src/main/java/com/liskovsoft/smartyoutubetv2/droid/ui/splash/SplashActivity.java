package com.liskovsoft.smartyoutubetv2.droid.ui.splash;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;

public class SplashActivity extends DroidActivity implements SplashView {
    private Intent mNewIntent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNewIntent = getIntent();

        onAppReturnHandled();

        if (savedInstanceState == null && isLauncherIntent(mNewIntent) && returnToApp()) {
            finish();
            return;
        }

        SplashPresenter presenter = SplashPresenter.instance(this);
        presenter.setView(this);
        presenter.onViewInitialized();
    }

    private static boolean isLauncherIntent(Intent intent) {
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.getData() == null;
    }

    /**
     * Launcher icon tapped while the app runs: bring back the screen that was left, as is. The
     * video when it was the last screen, else the main task.<br/>
     * The presenter would start the last screen anew instead (ViewManager.startDefaultView), a
     * second instance of it for most screens here, which TV's single instance screens don't have.
     * Done before the first frame, so the launcher's opening animation goes to that task: the
     * video grows out of the icon it shrank into.
     */
    private boolean returnToApp() {
        if (getViewManager().getTopView() == null) {
            return false; // nothing open yet: a regular start
        }

        getViewManager().resetExitState();

        // The main task goes right behind the video, out of the reach of the home screen (see
        // DroidActivity.updateRecentsEntries), so the app keeps a single Recents entry
        boolean isMainTaskMoved = moveMainTaskToFront();

        if (reopenPlayer()) {
            if (isMainTaskMoved) {
                onMainTaskMovedBehind();
            }

            return true;
        }

        return isMainTaskMoved;
    }

    private boolean moveMainTaskToFront() {
        ActivityManager.AppTask mainTask = findMainTask();

        if (mainTask == null) {
            return false;
        }

        try {
            mainTask.moveToFront();
        } catch (IllegalArgumentException | SecurityException e) {
            return false; // went away meanwhile
        }

        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;

        onAppReturnHandled();

        SplashPresenter presenter = SplashPresenter.instance(this);
        presenter.setView(this);
        presenter.onViewInitialized();
    }

    /**
     * The presenter opens the right screen itself (ViewManager.startDefaultView or the intent's
     * video, channel...), so a return through here needs nothing more. Not counted as a screen
     * either: it's gone before the next one starts.
     */
    @Override
    protected boolean isReturnTracked() {
        return false;
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }

    @Override
    public void finishView() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SplashPresenter.instance(this).onViewDestroyed();
    }
}
