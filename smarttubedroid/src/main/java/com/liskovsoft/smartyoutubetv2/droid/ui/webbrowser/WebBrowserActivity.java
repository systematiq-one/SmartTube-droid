package com.liskovsoft.smartyoutubetv2.droid.ui.webbrowser;

import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.WebBrowserPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;

/**
 * Touch implementation of the in-app browser. The url isn't passed through intent extras:
 * {@link WebBrowserPresenter#loadUrl(String)} stores it and re-delivers it to the view
 * inside {@code onViewInitialized()} (or directly when the view is already alive).<br/>
 * Configured like TV's WebBrowserFragment: JS enabled, no cache.
 */
public class WebBrowserActivity extends DroidActivity implements WebBrowserView {
    private WebBrowserPresenter mWebBrowserPresenter;
    private WebView mWebView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebBrowserPresenter = WebBrowserPresenter.instance(this);
        mWebBrowserPresenter.setView(this);

        // Inflation may fail with "Failed to load WebView provider: No WebView installed" (as on TV)
        try {
            setContentView(R.layout.misc_webbrowser_activity);
        } catch (Exception e) {
            e.printStackTrace();
            MessageHelpers.showMessage(this, e.getMessage());
            finish();
            return;
        }

        mWebView = findViewById(R.id.misc_webview);
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        // Keep navigation inside the WebView (so Back walks the in-app history)
        mWebView.setWebViewClient(new WebViewClient());

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // No caching
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Presenter re-delivers the stored url via loadUrl()
        mWebBrowserPresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mWebBrowserPresenter.onViewResumed();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mWebBrowserPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mWebBrowserPresenter.onViewDestroyed();
    }

    @Override
    public void onBackPressed() {
        // Navigate the WebView history first; leave the screen when it's exhausted
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void loadUrl(String url) {
        if (mWebView != null && url != null) {
            mWebView.loadUrl(url);
        }
    }
}
