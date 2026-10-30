package com.liskovsoft.smartyoutubetv2.droid.ui.signin;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;

import com.google.android.material.button.MaterialButton;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
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
 * with the code already filled in (on a phone tapping a link beats scanning a QR code).<br/>
 * The presenter closes the screen automatically once the user finishes on the activation page.
 */
public class SignInActivity extends DroidActivity implements SignInView {
    /** Same as the layout's code text */
    private static final float CODE_TEXT_SIZE_SP = 34;
    private static final float CODE_LETTER_SPACING = 0.2f;
    private static final float ERROR_TEXT_SIZE_SP = 24;
    /** How long a return from the browser waits for the sign-in before showing the code again */
    private static final long CHECKING_TIMEOUT_MS = 10_000;
    /**
     * Where the presenter's youtube.com/qr/activate link redirects to. youtube.com links make
     * browsers offer the YouTube app instead. enable_delegation lists the YouTube brand accounts.
     */
    private static final String DEVICE_SIGN_IN_URL = "https://accounts.google.com/o/oauth2/device/usercode?enable_delegation=1&user_code=";
    private SignInPresenter mSignInPresenter;
    private TextView mCodeText;
    private TextView mDescriptionText;
    private TextView mWaitingText;
    private MaterialButton mOpenBrowserButton;
    private MaterialButton mCopyButton;
    private ProgressBar mProgressBar;
    private String mUserCode;
    private String mFullSignInUrl;
    private boolean mIsError;
    private int mAccountCount;
    private boolean mIsBrowserOpened;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mStopChecking = this::showCodeAgain;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.signin_activity);

        mAccountCount = getAccountCount();

        mCodeText = findViewById(R.id.signin_code_text);
        mDescriptionText = findViewById(R.id.signin_description_text);
        mOpenBrowserButton = findViewById(R.id.signin_open_browser_button);
        mCopyButton = findViewById(R.id.signin_copy_button);
        mProgressBar = findViewById(R.id.signin_progress);
        mWaitingText = findViewById(R.id.signin_waiting_text);
        MaterialButton cancelButton = findViewById(R.id.signin_cancel_button);

        // Same initial description as TV's SignInFragment guidance (empty url placeholder)
        mDescriptionText.setText(getString(R.string.signin_view_description, ""));

        mSignInPresenter = SignInPresenter.instance(this);
        mSignInPresenter.setView(this);

        mOpenBrowserButton.setOnClickListener(v -> openInBrowser());
        mCopyButton.setOnClickListener(v -> copyCode());
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
        // An account added, not just any account: this screen also adds a second one.
        if (getAccountCount() > mAccountCount) {
            finish();
            return;
        }

        // The presenter only notices a few seconds later: Android pauses the app in the
        // background, so the poll runs again only now, and storing the account takes a moment.
        // Don't show a code that's already been used meanwhile.
        if (mIsBrowserOpened) {
            mIsBrowserOpened = false;
            showChecking();
        }

        mSignInPresenter.onViewResumed();
    }

    private int getAccountCount() {
        try {
            List<Account> accounts = YouTubeServiceManager.instance().getSignInService().getAccounts();
            return accounts != null ? accounts.size() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
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

        mHandler.removeCallbacksAndMessages(null);

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
        if (TextUtils.isEmpty(signInUrl)) {
            showError(userCode);
            return;
        }

        mIsError = false;
        mUserCode = userCode;
        mFullSignInUrl = DEVICE_SIGN_IN_URL + Uri.encode(getDashedCode());
        showCodeAgain();

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

    /**
     * Back from the browser, where the user most likely just confirmed the code.
     */
    private void showChecking() {
        if (mIsError || TextUtils.isEmpty(mUserCode)) {
            return;
        }

        mCodeText.setText(R.string.signin_checking);
        mCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, ERROR_TEXT_SIZE_SP);
        mCodeText.setLetterSpacing(0);
        mProgressBar.setVisibility(View.VISIBLE);
        mCopyButton.setVisibility(View.GONE);
        mOpenBrowserButton.setEnabled(false);

        mHandler.removeCallbacks(mStopChecking);
        mHandler.postDelayed(mStopChecking, CHECKING_TIMEOUT_MS);
    }

    /**
     * The code, as a new one arrives or when the sign-in didn't go through after all.
     */
    private void showCodeAgain() {
        mHandler.removeCallbacks(mStopChecking);

        if (mIsError || TextUtils.isEmpty(mUserCode)) {
            return;
        }

        mProgressBar.setVisibility(View.GONE);
        mCodeText.setText(mUserCode);
        mCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, CODE_TEXT_SIZE_SP);
        mCodeText.setLetterSpacing(CODE_LETTER_SPACING);
        mCopyButton.setVisibility(View.VISIBLE);
        mOpenBrowserButton.setText(R.string.signin_open_browser);
        mOpenBrowserButton.setEnabled(true);
    }

    /**
     * The raw exception text used to fill the code slot in huge letters. Say what went wrong
     * instead, and turn the browser button into a retry, which asks for a fresh code.
     */
    private void showError(String message) {
        mIsError = true;
        mHandler.removeCallbacks(mStopChecking);
        mProgressBar.setVisibility(View.GONE);
        boolean isNetworkError = isNetworkError(message);

        mCodeText.setText(isNetworkError ? R.string.signin_error_network : R.string.signin_error);
        mCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, ERROR_TEXT_SIZE_SP);
        mCodeText.setLetterSpacing(0);
        mDescriptionText.setText(isNetworkError ? getString(R.string.signin_error_network_hint) : message);

        mUserCode = null;
        mFullSignInUrl = null;
        mCopyButton.setVisibility(View.GONE);
        mOpenBrowserButton.setText(R.string.signin_try_again);
        mOpenBrowserButton.setEnabled(true);

        if (mWaitingText != null) {
            mWaitingText.setVisibility(View.GONE);
        }
    }

    private static boolean isNetworkError(String message) {
        return message != null && (message.contains("java.net.") || message.contains("javax.net.ssl.")
                || message.contains("Unable to resolve host"));
    }

    private void retry() {
        mCodeText.setText(R.string.signin_view_title);
        mDescriptionText.setText(getString(R.string.signin_view_description, ""));
        mOpenBrowserButton.setText(R.string.signin_open_browser);
        mOpenBrowserButton.setEnabled(false);

        mSignInPresenter.onViewInitialized(); // requests a new code
    }

    private void openInBrowser() {
        if (mIsError) {
            retry();
            return;
        }

        if (TextUtils.isEmpty(mFullSignInUrl)) {
            return;
        }

        // Nothing sends the user back here afterwards, so say what happens next
        if (mWaitingText != null) {
            mWaitingText.setVisibility(View.VISIBLE);
        }

        // The page fills the code in by itself (the QR link carries it). Should it not, the code
        // is on the clipboard to paste
        copyCode();

        mIsBrowserOpened = true;
        Uri uri = Uri.parse(mFullSignInUrl);

        try {
            startActivity(createBrowserIntent(uri));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                e2.printStackTrace();
                MessageHelpers.showMessage(this, e2.getMessage());
            }
        }
    }

    /**
     * youtube.com links are claimed by the YouTube app (and by this app too), which can't sign a
     * device in: send the link to the browser instead. The default browser when there is one,
     * otherwise a choice of browsers only.
     */
    private Intent createBrowserIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);

        // A bare scheme with no host matches browsers only
        ResolveInfo browser = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("http://")), PackageManager.MATCH_DEFAULT_ONLY);

        // No default browser: the system's chooser ("android") comes back
        if (browser != null && browser.activityInfo != null && !"android".equals(browser.activityInfo.packageName)) {
            intent.setPackage(browser.activityInfo.packageName);
        } else {
            intent.setSelector(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        }

        return intent;
    }

    /**
     * Shown spaced, entered dashed: XXX-XXX-XXX
     */
    private String getDashedCode() {
        return mUserCode.trim().replace(" ", "-");
    }

    private void copyCode() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (clipboard == null || TextUtils.isEmpty(mUserCode)) {
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.signin_view_title), getDashedCode()));

        // Android 13+ confirms a copy itself
        if (VERSION.SDK_INT < 33) {
            MessageHelpers.showMessage(this, R.string.signin_code_copied);
        }
    }
}
