package com.liskovsoft.smartyoutubetv2.droid.ui.signin;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Touch implementation of the device-code sign-in screen.<br/>
 * Shows the big user code, hints at the activation page and offers to open it in the browser
 * (on a phone tapping a link beats scanning a QR code).<br/>
 * The presenter closes the screen automatically once the user finishes on the activation page.
 */
public class SignInActivity extends DroidActivity implements SignInView {
    private SignInPresenter mSignInPresenter;
    private TextView mCodeText;
    private TextView mDescriptionText;
    private TextView mWaitingText;
    private MaterialButton mOpenBrowserButton;
    private String mFullSignInUrl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.signin_activity);

        mCodeText = findViewById(R.id.signin_code_text);
        mDescriptionText = findViewById(R.id.signin_description_text);
        mOpenBrowserButton = findViewById(R.id.signin_open_browser_button);
        mWaitingText = findViewById(R.id.signin_waiting_text);
        MaterialButton cancelButton = findViewById(R.id.signin_cancel_button);

        // Same initial description as TV's SignInFragment guidance (empty url placeholder)
        mDescriptionText.setText(getString(R.string.signin_view_description, ""));

        mSignInPresenter = SignInPresenter.instance(this);
        mSignInPresenter.setView(this);

        mOpenBrowserButton.setOnClickListener(v -> openInBrowser());
        cancelButton.setOnClickListener(v -> mSignInPresenter.onActionClicked());

        mSignInPresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Coming back from the browser: the activation page cannot send the user back here, so
        // check whether the sign-in already went through while this screen was in the background.
        // The presenter polls every few seconds and closes us itself, but the process can be
        // frozen while backgrounded, which would leave a stale code on screen.
        if (isSignedIn()) {
            finish();
            return;
        }

        mSignInPresenter.onViewResumed();
    }

    private boolean isSignedIn() {
        try {
            return YouTubeServiceManager.instance().getSignInService().isSigned();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSignInPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSignInPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (TextUtils.isEmpty(userCode)) {
            return;
        }

        // NOTE: on error the presenter passes the error message as the code and an empty url
        mCodeText.setText(userCode);

        mFullSignInUrl = !TextUtils.isEmpty(fullSignInUrl) ? fullSignInUrl : signInUrl;
        mOpenBrowserButton.setEnabled(!TextUtils.isEmpty(mFullSignInUrl));

        if (!TextUtils.isEmpty(signInUrl)) {
            // "To sign-in enter this code on page {signInUrl}" with the url highlighted (as on TV)
            String description = getString(R.string.signin_view_description, signInUrl);
            int start = description.indexOf(signInUrl);
            int end = start + signInUrl.length();
            mDescriptionText.setText(Utils.color(description, Color.RED, start, end));
        }
    }

    @Override
    public void close() {
        finish();
    }

    private void openInBrowser() {
        if (TextUtils.isEmpty(mFullSignInUrl)) {
            return;
        }

        // Nothing sends the user back here afterwards, so say what happens next
        if (mWaitingText != null) {
            mWaitingText.setVisibility(android.view.View.VISIBLE);
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mFullSignInUrl)));
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }
}
