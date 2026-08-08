package com.liskovsoft.smartyoutubetv2.droid.ui.base;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

/**
 * Base activity for all touch UI screens.
 * Extends MotherActivity because ViewManager casts to it (properlyFinishTheApp,
 * addOnResult/addOnPermissions plumbing used by sign-in and backup flows).
 */
public abstract class DroidActivity extends MotherActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TV burn-in screensaver is wrong on phones: the OS owns the screen timeout
        if (getScreensaverManager() != null) {
            getScreensaverManager().setBlocked(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Keep ViewManager's activity stack accurate (TV's LeanbackActivity does the same)
        getViewManager().addTop(this);
    }

    @Override
    protected void initTheme() {
        // TV color-scheme themes are Leanback-based; the phone app uses its own Material theme
        // declared in the manifest, so skip the runtime theme override.
    }
}
