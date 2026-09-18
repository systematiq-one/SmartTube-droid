package com.liskovsoft.smartyoutubetv2.droid;

import androidx.multidex.MultiDexApplication;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.droid.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.channel.ChannelActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.channeluploads.ChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.search.SearchActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.signin.SignInActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.splash.SplashActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.webbrowser.WebBrowserActivity;

public class DroidApplication extends MultiDexApplication {
    /** Bumped when {@link #TAB_ORDER_HEAD} changes, so the new order is applied once more. */
    private static final String TAB_ORDER_APPLIED = "droid_tab_order_v1";
    /** Top bar order for the phone UI. "Reels" is this codebase's Shorts section. */
    private static final int[] TAB_ORDER_HEAD = {
            MediaGroup.TYPE_HOME,
            MediaGroup.TYPE_SUBSCRIPTIONS,
            MediaGroup.TYPE_USER_PLAYLISTS,
            MediaGroup.TYPE_HISTORY,
            MediaGroup.TYPE_CHANNEL_UPLOADS, // "Channels"
            MediaGroup.TYPE_MY_VIDEOS,
            MediaGroup.TYPE_SHORTS
    };
    private static final int[] TAB_ORDER_TAIL = {MediaGroup.TYPE_SETTINGS};

    static {
        // Fix youtube buffering/throttling (same as TV app)
        System.setProperty("http.keepAlive", "false");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Phone UI: keep real device density; the TV 960dp emulation breaks touch layouts
        MotherActivity.setTvDpiScalingEnabled(false);

        // Phone UI: the OS owns the screen timeout, so no burn-in dimming or screen off
        ScreensaverManager.setSupported(false);

        applyTabOrder();

        setupViewManager();
    }

    /**
     * Lays the top bar tabs out the way the phone UI wants them. Applied once per install
     * rather than on every start, so a section the user moves by hand afterwards stays put.
     */
    private void applyTabOrder() {
        AppPrefs prefs = AppPrefs.instance(this);

        if (prefs.getBoolean(TAB_ORDER_APPLIED, false)) {
            return;
        }

        SidebarService.instance(this).orderSections(TAB_ORDER_HEAD, TAB_ORDER_TAIL);

        prefs.putBoolean(TAB_ORDER_APPLIED, true);
    }

    private void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);
        viewManager.setRoot(BrowseActivity.class);

        viewManager.register(SplashView.class, SplashActivity.class);
        viewManager.register(BrowseView.class, BrowseActivity.class);
        viewManager.register(PlaybackView.class, PlaybackActivity.class, BrowseActivity.class);
        viewManager.register(AppDialogView.class, AppDialogActivity.class, BrowseActivity.class);
        viewManager.register(SearchView.class, SearchActivity.class, BrowseActivity.class);
        viewManager.register(SignInView.class, SignInActivity.class, BrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, BrowseActivity.class);
        viewManager.register(ChannelView.class, ChannelActivity.class, BrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, ChannelUploadsActivity.class, BrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, BrowseActivity.class);
    }
}
