package com.liskovsoft.smartyoutubetv2.droid.ui.splash;

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

        SplashPresenter presenter = SplashPresenter.instance(this);
        presenter.setView(this);
        presenter.onViewInitialized();
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
