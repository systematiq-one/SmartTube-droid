package com.liskovsoft.smartyoutubetv2.droid.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Rational;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.vkay94.dtpv.DoubleTapPlayerViewImpl;
import com.github.vkay94.dtpv.youtube.YouTubeOverlay;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueNavigator;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoGroupAdapter;
import com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoRowsAdapter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Touch player screen. Implements common's {@link PlaybackView} = {@code PlayerManager}
 * ({@code PlayerEngine} + {@code PlayerUI}), so every existing controller
 * (video loader, suggestions, SponsorBlock, HQ dialog, remote, state...) works unchanged.
 * <br/>
 * The engine half is pure delegation to {@link ExoPlayerController} — the wiring order is
 * copied from smarttubetv's {@code EmbedPlayerView} (the minimal non-Leanback host).
 * The UI half is re-implemented as touch UI: a controls overlay with a 3s auto-hide,
 * a portrait metadata panel with suggestions rows, and YouTube-like gestures
 * (see {@link PlaybackGestureHandler}).
 */
public class PlaybackActivity extends DroidActivity implements PlaybackView,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView,
        PlaybackGestureHandler.Listener, VideoGroupAdapter.Listener {
    private static final String TAG = PlaybackActivity.class.getSimpleName();
    private static final long OVERLAY_AUTO_HIDE_MS = 3_000;
    private static final long PROGRESS_UPDATE_MS = 500;
    private static final int SEEK_BAR_MAX = 1_000;
    private static final float SPEED_BOOST = 2f;
    /** A full-width horizontal swipe scrubs this share of the video... */
    private static final float SEEK_SWIPE_FRACTION = 0.5f;
    /** ...but never more than this, so long videos stay controllable. */
    private static final long MAX_SEEK_SWIPE_MS = 10 * 60 * 1_000L;
    private static final long INDICATOR_HIDE_DELAY_MS = 900;
    /** {@code CaptionTrack.TYPE_ASR} — autogenerated captions. The constant itself is private. */
    private static final String SUBTITLE_TYPE_ASR = "asr";

    // Button ids of common's vocabulary. The two lb_control_* ids live in leanback-1.0.0 and
    // reach common's R transitively (see report result13.md, correction C1).
    private static final int BUTTON_QUALITY = com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_high_quality;
    private static final int BUTTON_CAPTIONS = com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_closed_captioning;
    private static final int BUTTON_ROTATE = com.liskovsoft.smartyoutubetv2.common.R.id.action_rotate;
    private static final int BUTTON_PIP = com.liskovsoft.smartyoutubetv2.common.R.id.action_pip;
    private static final int BUTTON_LIKE = com.liskovsoft.smartyoutubetv2.common.R.id.action_thumbs_up;
    private static final int BUTTON_DISLIKE = com.liskovsoft.smartyoutubetv2.common.R.id.action_thumbs_down;
    private static final int BUTTON_SHARE = com.liskovsoft.smartyoutubetv2.common.R.id.action_share;
    private static final int BUTTON_SPEED = com.liskovsoft.smartyoutubetv2.common.R.id.action_video_speed;
    private static final int BUTTON_SUBSCRIBE = com.liskovsoft.smartyoutubetv2.common.R.id.action_subscribe;
    private static final int BUTTON_CHANNEL = com.liskovsoft.smartyoutubetv2.common.R.id.action_channel;
    private static final int BUTTON_MUTE = com.liskovsoft.smartyoutubetv2.common.R.id.action_sound_off;
    private static final int BUTTON_PLAYLIST_ADD = com.liskovsoft.smartyoutubetv2.common.R.id.action_playlist_add;

    private PlaybackPresenter mPlaybackPresenter;
    private ExoPlayerInitializer mPlayerInitializer;
    private ExoPlayerController mExoPlayerController;
    private SimpleExoPlayer mPlayer;
    private SubtitleManager mSubtitleManager;
    /** Kept for the subtitle track metadata the player's own format list doesn't carry. */
    private MediaItemFormatInfo mFormatInfo;
    private MediaSessionCompat mMediaSession;
    private MediaSessionConnector mMediaSessionConnector;
    private PlaybackGestureHandler mGestureHandler;

    // Views
    private ConstraintLayout mRoot;
    private FrameLayout mPlayerContainer;
    private DoubleTapPlayerViewImpl mPlayerView;
    private SubtitleView mSubtitleView;
    private YouTubeOverlay mYouTubeOverlay;
    private ImageView mBackgroundView;
    private ProgressBar mProgressBar;
    /** A hide arrived while the player was buffering; applied once buffering ends. */
    private boolean mProgressHidePending;
    private View mOverlay;
    private View mTopBar;
    private View mCenterBar;
    private View mBottomBar;
    private View mPanel;
    private ImageButton mPlayButton;
    private ImageButton mFullscreenButton;
    private TextView mOverlayTitle;
    private TextView mPositionLabel;
    private TextView mDurationLabel;
    private TextView mEndingTimeLabel;
    private TextView mSeekPreviewLabel;
    private TextView mNextTitleLabel;
    private PlaybackSeekBar mSeekBar;
    private TextView mPanelTitle;
    private TextView mPanelSecondTitle;
    private TextView mChannelName;
    private ImageView mChannelIcon;
    private MaterialButton mQualityChip;
    private RecyclerView mSuggestionsView;
    private LinearLayout mIndicator;
    private ImageView mIndicatorIcon;
    private TextView mIndicatorText;

    private VideoRowsAdapter mSuggestionsAdapter;
    /** Mirrors the adapter's row order so getSuggestionsIndex/getSuggestionsByIndex can answer. */
    private final List<VideoGroup> mSuggestionGroups = new ArrayList<>();
    private final Map<Integer, Integer> mButtonStates = new HashMap<>();
    /** buttonId -> views representing it (a button may live both in the overlay and in the chips row). */
    private final Map<Integer, List<View>> mButtonViews = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideOverlay = new Runnable() {
        @Override
        public void run() {
            if (isPlaying() && !mIsScrubbing) {
                showOverlay(false);
            } else {
                scheduleAutoHide(); // still paused: check again later
            }
        }
    };
    private final Runnable mUpdateProgress = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mHandler.postDelayed(this, PROGRESS_UPDATE_MS);
        }
    };

    private boolean mIsEngineBlocked;
    private boolean mIsScrubbing;
    private boolean mIsBackPressed;
    private boolean mIsOverlayShown;
    private boolean mIsLandscape;
    private int mZoomPercents = 100;
    private boolean mFlipEnabled;
    private int mRotationAngle;
    private float mSavedSpeed = 1f;
    private boolean mSpeedBoosted;
    private float mDragVolume = -1;
    /** Position the current horizontal scrub started from; -1 when no scrub is in progress. */
    private long mSeekAnchorMs = -1;
    private long mSeekTargetMs;
    private final Runnable mHideIndicator = this::hideIndicator;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.playback_activity);

        initViews();

        mPlayerInitializer = new ExoPlayerInitializer(this);
        mPlaybackPresenter = PlaybackPresenter.instance(this);
        mPlaybackPresenter.setView(this);
        mExoPlayerController = new ExoPlayerController(this, mPlaybackPresenter);
        mExoPlayerController.setPlayerView(this);

        mGestureHandler = new PlaybackGestureHandler(this, mPlayerView, mYouTubeOverlay, this);
        mGestureHandler.setSeekSeconds(getPlayerData().getSeekIncrementMs() / 1_000);

        applyOrientation();

        mPlaybackPresenter.onViewInitialized(); // init all controllers
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (VERSION.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIsBackPressed = false;

        if (VERSION.SDK_INT <= 23 || mPlayer == null) {
            initializePlayer();
        }

        // NOTE: don't move this into another place! Multiple components rely on it.
        mPlaybackPresenter.onViewResumed();

        showHideWidgets(true); // PIP mode fix
        blockEngine(false); // reset bg mode
    }

    @Override
    protected void onPause() {
        super.onPause();

        // NOTE: don't move this into another place! Multiple components rely on it.
        mPlaybackPresenter.onViewPaused();

        if (VERSION.SDK_INT <= 23) {
            maybeReleasePlayer();
        }

        showHideWidgets(false); // PIP mode fix
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (VERSION.SDK_INT > 23) {
            maybeReleasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mHandler.removeCallbacksAndMessages(null);

        // Fix situations when the engine wasn't properly destroyed (e.g. after closing dialogs)
        releasePlayer();

        if (mGestureHandler != null) {
            mGestureHandler.release();
        }

        if (mPlaybackPresenter.getView() == this) {
            mPlaybackPresenter.onViewDestroyed();
        }
    }

    @Override
    public void onBackPressed() {
        mIsBackPressed = true;

        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Observe only: the event keeps travelling to the controls, so buttons keep working
        if (mGestureHandler != null) {
            mGestureHandler.onTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    // ------------------------------------------------------------ view setup

    private void initViews() {
        mRoot = findViewById(R.id.playback_root);
        mPlayerContainer = findViewById(R.id.playback_player_container);
        mPlayerView = findViewById(R.id.playback_player_view);
        mSubtitleView = findViewById(R.id.playback_subtitles);
        mYouTubeOverlay = findViewById(R.id.playback_youtube_overlay);
        mBackgroundView = findViewById(R.id.playback_background);
        mProgressBar = findViewById(R.id.playback_progress);
        mIndicator = findViewById(R.id.playback_indicator);
        mIndicatorIcon = findViewById(R.id.playback_indicator_icon);
        mIndicatorText = findViewById(R.id.playback_indicator_text);

        mOverlay = findViewById(R.id.playback_overlay_root);
        mTopBar = findViewById(R.id.playback_top_bar);
        mCenterBar = findViewById(R.id.playback_center_bar);
        mBottomBar = findViewById(R.id.playback_bottom_bar);
        mOverlayTitle = findViewById(R.id.playback_overlay_title);
        mPlayButton = findViewById(R.id.playback_btn_play);
        mFullscreenButton = findViewById(R.id.playback_btn_fullscreen);
        mPositionLabel = findViewById(R.id.playback_position);
        mDurationLabel = findViewById(R.id.playback_duration);
        mEndingTimeLabel = findViewById(R.id.playback_ending_time);
        mSeekPreviewLabel = findViewById(R.id.playback_seek_preview);
        mNextTitleLabel = findViewById(R.id.playback_next_title);
        mSeekBar = findViewById(R.id.playback_seekbar);

        mPanel = findViewById(R.id.playback_panel);
        mPanelTitle = findViewById(R.id.playback_title);
        mPanelSecondTitle = findViewById(R.id.playback_second_title);
        mChannelName = findViewById(R.id.playback_channel_name);
        mChannelIcon = findViewById(R.id.playback_channel_icon);
        mQualityChip = findViewById(R.id.playback_chip_quality);
        mSuggestionsView = findViewById(R.id.playback_suggestions);

        // The player view hosts a subtitle view of its own: hide it, we render subs
        // through common's SubtitleManager (styling, position, autogenerated subs fix)
        if (mPlayerView.getSubtitleView() != null) {
            mPlayerView.getSubtitleView().setVisibility(View.GONE);
        }
        // Double tapping is driven by PlaybackGestureHandler (it also owns drag + long press)
        mPlayerView.setDoubleTapEnabled(false);
        mPlayerView.setUseController(false);

        mSuggestionsAdapter = new VideoRowsAdapter(this);
        mSuggestionsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mSuggestionsView.setAdapter(mSuggestionsAdapter);

        mEndingTimeLabel.setVisibility(getPlayerData().isGlobalEndingTimeEnabled() ? View.VISIBLE : View.GONE);

        initControlListeners();
        initButtons();
        initWindowInsets();
    }

    /**
     * The app draws edge to edge (Helpers.makeActivityFullscreen2), and since the notch fix in
     * SharedModules it draws into the display cutout too. Without this the top row of player
     * buttons ends up under the status bar / notch in portrait and can't be tapped.
     *
     * Padding is added on top of what the layout already declares, so the bars keep their look.
     * The overlay only needs the bottom inset in landscape — in portrait it sits inside the 16:9
     * video box at the top of the screen. Down there it's the suggestions list that has to clear
     * the navigation bar instead.
     */
    private void initWindowInsets() {
        final int topBarPaddingTop = mTopBar.getPaddingTop();
        final int topBarPaddingLeft = mTopBar.getPaddingLeft();
        final int topBarPaddingRight = mTopBar.getPaddingRight();
        final int bottomBarPaddingLeft = mBottomBar.getPaddingLeft();
        final int bottomBarPaddingRight = mBottomBar.getPaddingRight();
        final int bottomBarPaddingBottom = mBottomBar.getPaddingBottom();
        final int suggestionsPaddingBottom = mSuggestionsView.getPaddingBottom();

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();

            // System bars are hidden in fullscreen mode, so the cutout is the only inset left
            if (VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();

                if (cutout != null) {
                    top = Math.max(top, cutout.getSafeInsetTop());
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }

            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            mTopBar.setPadding(topBarPaddingLeft + left, topBarPaddingTop + top,
                    topBarPaddingRight + right, mTopBar.getPaddingBottom());
            mBottomBar.setPadding(bottomBarPaddingLeft + left, mBottomBar.getPaddingTop(),
                    bottomBarPaddingRight + right, bottomBarPaddingBottom + (isLandscape ? bottom : 0));

            // In portrait the navigation bar stays on screen (see applySystemUi) and the panel
            // reaches the bottom edge, so the suggestions have to clear it. clipToPadding is off,
            // so the rows still scroll underneath.
            mSuggestionsView.setPadding(mSuggestionsView.getPaddingLeft(), mSuggestionsView.getPaddingTop(),
                    mSuggestionsView.getPaddingRight(), suggestionsPaddingBottom + (isLandscape ? 0 : bottom));

            return insets;
        });

        mRoot.requestApplyInsets();
    }

    private void initControlListeners() {
        mPlayButton.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.playback_btn_prev).setOnClickListener(v -> {
            resetAutoHide();
            mPlaybackPresenter.onPreviousClicked();
        });
        findViewById(R.id.playback_btn_next).setOnClickListener(v -> {
            resetAutoHide();
            mPlaybackPresenter.onNextClicked();
        });
        findViewById(R.id.playback_btn_back).setOnClickListener(v -> onBackPressed());
        mFullscreenButton.setOnClickListener(v -> toggleFullscreen());

        findViewById(R.id.playback_btn_more).setOnClickListener(this::showOverflowMenu);
        findViewById(R.id.playback_chip_more).setOnClickListener(this::showOverflowMenu);

        findViewById(R.id.playback_channel_row).setOnClickListener(v -> dispatchButton(BUTTON_CHANNEL));

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                long positionMs = progressToPositionMs(progress);
                mPositionLabel.setText(formatTime(positionMs));
                // Feeds chapter/segment titles (PlayerUIController.updateSeekPreviewTitle)
                mPlaybackPresenter.onSeekPositionChanged(positionMs);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsScrubbing = true;
                cancelAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsScrubbing = false;
                setPositionMs(progressToPositionMs(seekBar.getProgress()));
                resetAutoHide();
            }
        });
    }

    /**
     * Binds every control that maps onto one of common's {@code R.id.action_*} button ids.
     * Clicks report {@code presenter.onButtonClicked(buttonId, currentState)} and long clicks
     * {@code onButtonLongClicked} — exactly like TV's action buttons.
     */
    private void initButtons() {
        registerButton(BUTTON_QUALITY, findViewById(R.id.playback_chip_quality));
        registerButton(BUTTON_SPEED, findViewById(R.id.playback_btn_speed));
        registerButton(BUTTON_CAPTIONS, findViewById(R.id.playback_btn_captions));
        registerButton(BUTTON_MUTE, findViewById(R.id.playback_btn_mute));
        registerButton(BUTTON_PLAYLIST_ADD, findViewById(R.id.playback_btn_playlist));
        registerButton(BUTTON_PLAYLIST_ADD, findViewById(R.id.playback_chip_playlist));
        registerButton(BUTTON_LIKE, findViewById(R.id.playback_chip_like));
        registerButton(BUTTON_DISLIKE, findViewById(R.id.playback_chip_dislike));
        registerButton(BUTTON_SHARE, findViewById(R.id.playback_chip_share));
        registerButton(BUTTON_SPEED, findViewById(R.id.playback_chip_speed));
        registerButton(BUTTON_SUBSCRIBE, findViewById(R.id.playback_chip_subscribe));

        // The tap goes straight to the autogenerated subtitles instead of common's
        // "restore the last used track" behaviour. Long press is untouched: it still opens
        // the whole track list through PlayerUIController.
        View captionsButton = findViewById(R.id.playback_btn_captions);

        if (captionsButton != null) {
            captionsButton.setOnClickListener(v -> toggleSubtitles());
        }
    }

    /**
     * Tap: switch the autogenerated subtitles on, or off again when they're already showing.
     */
    private void toggleSubtitles() {
        resetAutoHide();

        List<FormatItem> formats = getSubtitleFormats();

        if (formats == null || formats.isEmpty()) {
            return;
        }

        if (getButtonState(BUTTON_CAPTIONS) == BUTTON_ON) {
            applySubtitleFormat(FormatItem.SUBTITLE_NONE);
            return;
        }

        FormatItem format = findAutoSubtitleFormat(formats);

        if (format == null) {
            // No captions of the video's own, or the source carried no track metadata:
            // fall back to the full list, same as a long press
            mPlaybackPresenter.onButtonLongClicked(BUTTON_CAPTIONS, getButtonState(BUTTON_CAPTIONS));
            return;
        }

        applySubtitleFormat(format);
    }

    /**
     * The autogenerated track in the video's own language.
     *
     * The player's own subtitle list can't answer this: YouTube marks autogenerated and auto
     * translated tracks alike with a trailing '*', and the tracks are sorted by name before they
     * reach us, so neither the marker nor the position identifies the original. The format info
     * behind the manifest does — every translation is the origin track's url plus a 'tlang' query.
     */
    private FormatItem findAutoSubtitleFormat(List<FormatItem> formats) {
        MediaSubtitle origin = findOriginSubtitle();

        if (origin == null) {
            return null;
        }

        String language = origin.getName() != null ? origin.getName() : origin.getLanguageCode();

        for (FormatItem format : formats) {
            // Both manifest builders use exactly this string as the track language
            if (format != null && !format.isDefault() && Helpers.equals(language, format.getLanguage())) {
                return format;
            }
        }

        return null;
    }

    /**
     * The autogenerated captions, or the video's own captions when it has none. Translations are
     * listed after the real caption tracks, so the first untranslated one is the origin — which
     * also covers the translation into the origin's own language, the one case where a
     * translation carries no 'tlang' of its own.
     */
    private MediaSubtitle findOriginSubtitle() {
        if (mFormatInfo == null || mFormatInfo.getSubtitles() == null) {
            return null;
        }

        MediaSubtitle firstOrigin = null;

        for (MediaSubtitle sub : mFormatInfo.getSubtitles()) {
            if (sub == null || isTranslatedSubtitle(sub)) {
                continue;
            }

            if (SUBTITLE_TYPE_ASR.equals(sub.getType())) {
                return sub;
            }

            if (firstOrigin == null) {
                firstOrigin = sub;
            }
        }

        return firstOrigin;
    }

    private boolean isTranslatedSubtitle(MediaSubtitle sub) {
        return sub.getBaseUrl() != null && sub.getBaseUrl().contains("tlang=");
    }

    private void applySubtitleFormat(FormatItem format) {
        boolean enabled = !format.isDefault();

        setFormat(format);
        getPlayerData().setFormat(format);

        // Mirrors PlayerUIController.enableSubtitleForChannel(): the per channel memory is what
        // decides whether subtitles come back on the next video of the same channel
        if (getPlayerData().isSubtitlesPerChannelEnabled() && getVideo() != null) {
            if (enabled) {
                getPlayerData().enableSubtitlesPerChannel(getVideo().channelId);
            } else {
                getPlayerData().disableSubtitlesPerChannel(getVideo().channelId);
            }
        }

        setButtonState(BUTTON_CAPTIONS, enabled ? BUTTON_ON : BUTTON_OFF);
    }

    private void registerButton(int buttonId, View view) {
        if (view == null) {
            return;
        }

        List<View> views = mButtonViews.get(buttonId);

        if (views == null) {
            views = new ArrayList<>();
            mButtonViews.put(buttonId, views);
        }

        views.add(view);

        view.setOnClickListener(v -> dispatchButton(buttonId));
        view.setOnLongClickListener(v -> {
            resetAutoHide();
            mPlaybackPresenter.onButtonLongClicked(buttonId, getButtonState(buttonId));
            return true;
        });

        applyButtonStyle(view, getButtonState(buttonId));
    }

    private void dispatchButton(int buttonId) {
        resetAutoHide();
        mPlaybackPresenter.onButtonClicked(buttonId, getButtonState(buttonId));
    }

    /**
     * Actions that have no dedicated control on the phone UI are collected in an overflow menu.
     * Menu item ids ARE common's action ids, so the click handler is a single dispatch call.
     */
    private void showOverflowMenu(View anchor) {
        resetAutoHide();

        PopupMenu menu = new PopupMenu(this, anchor);
        Menu m = menu.getMenu();

        // The top bar slot went to the speed toggle, so quality lives here now
        addMenuItem(m, BUTTON_QUALITY,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_high_quality);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_playback_queue,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_playback_queue);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_repeat,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_repeat_mode);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_content_block,
                com.liskovsoft.smartyoutubetv2.common.R.string.content_block_categories);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_video_zoom,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_video_zoom);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_flip,
                com.liskovsoft.smartyoutubetv2.common.R.string.video_flip);
        // Mute and add-to-playlist have their own buttons in the top bar, so they aren't repeated here
        addMenuItem(m, BUTTON_ROTATE,
                com.liskovsoft.smartyoutubetv2.common.R.string.video_rotate);
        addMenuItem(m, BUTTON_PIP,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_pip);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_seek_interval,
                com.liskovsoft.smartyoutubetv2.common.R.string.seek_interval);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_info,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_video_info);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_video_stats,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_video_stats);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_search,
                com.liskovsoft.smartyoutubetv2.common.R.string.action_search);
        addMenuItem(m, com.liskovsoft.smartyoutubetv2.common.R.id.action_screen_dimming,
                com.liskovsoft.smartyoutubetv2.common.R.string.screen_dimming);

        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                dispatchButton(item.getItemId());
                return true;
            }
        });

        menu.show();
    }

    private void addMenuItem(Menu menu, int buttonId, int titleResId) {
        MenuItem item = menu.add(Menu.NONE, buttonId, Menu.NONE, titleResId);
        item.setCheckable(true);
        item.setChecked(getButtonState(buttonId) == BUTTON_ON);
    }

    // ---------------------------------------------------------- engine setup

    private void initializePlayer() {
        if (mPlayer != null) {
            Log.d(TAG, "Skip player initialization.");
            return;
        }

        createPlayerObjects();

        mPlaybackPresenter.setView(this); // may have been replaced by the embed player
        mPlaybackPresenter.onEngineInitialized();
    }

    /**
     * NOTE: the order is copied from EmbedPlayerView/PlaybackFragment. Don't reshuffle.
     */
    private void createPlayerObjects() {
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(this);
        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);

        mExoPlayerController.setPlayer(mPlayer);
        mPlayerView.setPlayer(mPlayer);

        // Releases a hide that arrived while the player was still buffering (see showProgressBar)
        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (mProgressHidePending && !isPlayerBuffering()) {
                    showProgressBar(false);
                }
            }
        });

        createMediaSession();

        mGestureHandler.setPlayer(mPlayer);

        applyVideoTransform();
    }

    private void maybeReleasePlayer() {
        if (isEngineBlocked()) {
            Log.d(TAG, "releasePlayer: engine is blocked (background playback). Exiting...");
            return;
        }

        if (isInPIPMode()) {
            Log.d(TAG, "releasePlayer: running in PIP. Exiting...");
            return;
        }

        releasePlayer();
    }

    private void releasePlayer() {
        if (mPlayer == null) {
            return;
        }

        Log.d(TAG, "releasePlayer: Start releasing player engine...");

        mPlaybackPresenter.onEngineReleased();

        releaseMediaSession();

        mPlayerInitializer.release();
        mExoPlayerController.release();
        mPlayer = null;
        mSubtitleManager = null;
        mFormatInfo = null;

        mGestureHandler.setPlayer(null);
        mPlayerView.setPlayer(null);
    }

    private void createMediaSession() {
        if (VERSION.SDK_INT <= 19) {
            // Fix Android 4.4 bug: MediaButtonReceiver component may not be null
            return;
        }

        boolean disableNotifications = getPlayerTweaksData().isPlaybackNotificationsDisabled();
        mMediaSession = new MediaSessionCompat(getApplicationContext(), getPackageName());
        mMediaSession.setActive(!disableNotifications);
        mMediaSessionConnector = new MediaSessionConnector(mMediaSession);

        try {
            mMediaSessionConnector.setPlayer(mPlayer);
        } catch (NoSuchMethodError e) {
            // Android 9, Sony: No virtual method setState(IJFJ) in PlaybackState$Builder
            return;
        }

        // NOTE: Don't set to null. This won't disable notifications but makes them empty.
        mMediaSessionConnector.setMediaMetadataProvider(player -> {
            Video video = getVideo();

            if (video == null) {
                return null;
            }

            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.getTitleFull());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.getTitleFull());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.getAuthor());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, Helpers.toString(video.getSecondTitleFull()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, video.getCardImageUrl());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationMs());

            return metadataBuilder.build();
        });

        mMediaSessionConnector.setQueueNavigator(new QueueNavigator() {
            @Override
            public long getSupportedQueueNavigatorActions(Player player) {
                return PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
            }

            @Override
            public void onTimelineChanged(Player player) {
                // NOP: the queue lives in common's Playlist, not in the exo timeline
            }

            @Override
            public void onCurrentWindowIndexChanged(Player player) {
                // NOP
            }

            @Override
            public long getActiveQueueItemId(@Nullable Player player) {
                return 0;
            }

            @Override
            public void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher) {
                mPlaybackPresenter.onPreviousClicked();
            }

            @Override
            public void onSkipToQueueItem(Player player, ControlDispatcher controlDispatcher, long id) {
                // NOP
            }

            @Override
            public void onSkipToNext(Player player, ControlDispatcher controlDispatcher) {
                mPlaybackPresenter.onNextClicked();
            }

            @Override
            public boolean onCommand(Player player, ControlDispatcher controlDispatcher, String command,
                                     Bundle extras, android.os.ResultReceiver cb) {
                return false;
            }
        });
    }

    private void releaseMediaSession() {
        if (mMediaSessionConnector != null) {
            mMediaSessionConnector.setPlayer(null);
            mMediaSessionConnector.setMediaMetadataProvider(null);
            mMediaSessionConnector.setQueueNavigator(null);
            mMediaSessionConnector = null;
        }

        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
    }

    private void createSubtitleManager() {
        if (mSubtitleManager == null && mPlayer != null && mSubtitleView != null) {
            mSubtitleManager = new SubtitleManager(mSubtitleView);

            if (mPlayer.getTextComponent() != null) {
                mPlayer.getTextComponent().addTextOutput(mSubtitleManager);
            }
        }
    }

    // ---------------------------------------------------- orientation / PIP

    private void applyOrientation() {
        mIsLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        boolean fullscreen = mIsLandscape || isInPIPMode();

        // Fullscreen: the video fills the screen. Portrait: 16:9 pinned to the top, panel below.
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mRoot);

        if (fullscreen) {
            constraintSet.setDimensionRatio(R.id.playback_player_container, null);
            constraintSet.connect(R.id.playback_player_container, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        } else {
            constraintSet.setDimensionRatio(R.id.playback_player_container, "H,16:9");
            constraintSet.clear(R.id.playback_player_container, ConstraintSet.BOTTOM);
        }

        constraintSet.applyTo(mRoot);

        mPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mFullscreenButton.setImageResource(fullscreen
                ? R.drawable.playback_ic_fullscreen_exit : R.drawable.playback_ic_fullscreen);

        applySystemUi(fullscreen);
    }

    /**
     * Fullscreen (landscape/PIP) hides the navigation bar; portrait keeps it on screen so the
     * back/home/recents buttons don't have to be swiped up first. The status bar stays hidden
     * either way — the overlay's top row sits where it would be.
     */
    private void applySystemUi(boolean fullscreen) {
        setNavigationBarVisible(!fullscreen);
    }

    /**
     * Called on every resume — MotherActivity hides the bars again each time, and coming back
     * from a dialog or from PIP must not leave portrait without its navigation bar. Overridden
     * because PIP counts as fullscreen here, and because the insets are handled by
     * {@link #initWindowInsets()} rather than by the base class.
     */
    @Override
    protected void applySystemBars() {
        applySystemUi(mIsLandscape || isInPIPMode());
    }

    private void toggleFullscreen() {
        resetAutoHide();

        // The activity handles orientation changes itself (see manifest configChanges),
        // so this only flips the requested orientation; onConfigurationChanged does the rest.
        setRequestedOrientation(mIsLandscape
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // The activity is NOT recreated (configChanges in the manifest): the engine keeps running
        applyOrientation();

        // Status bar and cutout sit on different edges per orientation; recompute the bar padding
        mRoot.requestApplyInsets();
    }

    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        if (!Helpers.isPictureInPictureSupported(this)) {
            return;
        }

        if (!wannaEnterToPip()) {
            return;
        }

        Log.d(TAG, "Entering PIP mode...");

        try {
            if (VERSION.SDK_INT >= 26) {
                enterPictureInPictureMode(createPipParams());
            } else {
                enterPictureInPictureMode();
            }
        } catch (Exception e) {
            // Device doesn't support picture-in-picture mode
            Log.e(TAG, e.getMessage());
        }
    }

    @TargetApi(26)
    private PictureInPictureParams createPipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();

        builder.setAspectRatio(createPipRatio());

        return builder.build();
    }

    @TargetApi(26)
    private Rational createPipRatio() {
        int width = 16;
        int height = 9;

        if (mPlayer != null && mPlayer.getVideoFormat() != null
                && mPlayer.getVideoFormat().width > 0 && mPlayer.getVideoFormat().height > 0) {
            width = mPlayer.getVideoFormat().width;
            height = mPlayer.getVideoFormat().height;
        }

        // Android rejects ratios outside 0.418..2.39
        float ratio = (float) width / height;

        if (ratio < 0.5f || ratio > 2.3f) {
            width = 16;
            height = 9;
        }

        return new Rational(width, height);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        sIsInPipMode = isInPictureInPictureMode;

        showHideWidgets(!isInPictureInPictureMode);
        applyOrientation();
    }

    /**
     * HOME pressed (or the app is otherwise left). Port of TV's background-mode logic.
     */
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();

        // The activity may just be overlapped by a dialog/search: not a real leave
        if (mIsBackPressed || isFinishing() || getViewManager().isNewViewPending()
                || getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK) {
            return;
        }

        switch (getPlayerData().getBackgroundMode()) {
            case PlayerData.BACKGROUND_MODE_PIP:
                enterPipMode();
                if (doNotDestroy()) {
                    blockEngine(true);
                    getViewManager().blockTop(this);
                }
                break;
            case PlayerData.BACKGROUND_MODE_PLAY_BEHIND:
                // 'Play behind' is an Android TV only API (requestVisibleBehind, removed in API 26).
                // On phones it degrades to the sound-only background mode below.
            case PlayerData.BACKGROUND_MODE_SOUND:
                if (doNotDestroy()) {
                    blockEngine(true);
                    getViewManager().blockTop(this);
                }
                break;
        }
    }

    /**
     * BACK pressed, or the PIP window's close button.
     */
    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");

        if (!skipPip()) {
            enterPipMode(); // without this call the app hangs when pressing the PIP button
        }

        if (doNotDestroy() && !skipPip()) {
            blockEngine(true);
            // Ensure this activity is reopened when the user returns to the app
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            if (getPlayerTweaksData().isKeepFinishedActivityEnabled()) {
                getViewManager().startParentView(this);
                maybeReleasePlayer();
            } else {
                super.finish();
            }
        }
    }

    /**
     * Force finish (PIP, app exit etc).
     */
    @Override
    public void finishReally() {
        super.finishReally();

        releasePlayer();

        mPlaybackPresenter.onFinish();
    }

    private boolean skipPip() {
        return mIsBackPressed
                && getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        boolean isPip = getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP || isEngineBlocked();

        return isPip && !isInPIPMode();
    }

    private boolean doNotDestroy() {
        sIsInPipMode = isInPIPMode();

        boolean isBackground = getPlayerData().getBackgroundMode() == BACKGROUND_MODE_SOUND || isEngineBlocked();

        return sIsInPipMode || isBackground;
    }

    /**
     * PIP mode fix: the overlay and the panel can't be shown inside the small window.
     */
    private void showHideWidgets(boolean show) {
        if (!show) {
            showOverlay(false);
        }

        mPanel.setVisibility(show && !mIsLandscape && !isInPIPMode() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------ overlay handling

    private void togglePlayPause() {
        resetAutoHide();

        boolean playing = getPlayWhenReady();

        setPlayWhenReady(!playing);

        if (playing) {
            mPlaybackPresenter.onPauseClicked();
        } else {
            mPlaybackPresenter.onPlayClicked();
        }

        updatePlayButton();
    }

    private void updatePlayButton() {
        mPlayButton.setImageResource(getPlayWhenReady()
                ? R.drawable.playback_ic_pause : R.drawable.playback_ic_play);
    }

    private void scheduleAutoHide() {
        mHandler.removeCallbacks(mHideOverlay);
        mHandler.postDelayed(mHideOverlay, OVERLAY_AUTO_HIDE_MS);
    }

    private void cancelAutoHide() {
        mHandler.removeCallbacks(mHideOverlay);
    }

    /** Keeps the overlay alive for another auto-hide period (any user interaction). */
    private void resetAutoHide() {
        if (mIsOverlayShown) {
            scheduleAutoHide();
        }
    }

    private void updateProgress() {
        long positionMs = getPositionMs();
        long durationMs = getDurationMs();

        if (positionMs < 0) {
            positionMs = 0;
        }

        if (!mIsScrubbing) {
            mSeekBar.setProgress(durationMs > 0 ? (int) (positionMs * SEEK_BAR_MAX / durationMs) : 0);
            mPositionLabel.setText(formatTime(positionMs));
        }

        mSeekBar.setSecondaryProgress(mPlayer != null ? mPlayer.getBufferedPercentage() * (SEEK_BAR_MAX / 100) : 0);
        mDurationLabel.setText(formatTime(durationMs));

        updatePlayButton();
        updateEndingTime();
    }

    private long progressToPositionMs(int progress) {
        long durationMs = getDurationMs();

        return durationMs > 0 ? durationMs * progress / SEEK_BAR_MAX : 0;
    }

    private static String formatTime(long timeMs) {
        if (timeMs < 0) {
            timeMs = 0;
        }

        long totalSec = timeMs / 1_000;
        long seconds = totalSec % 60;
        long minutes = (totalSec / 60) % 60;
        long hours = totalSec / 3_600;

        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    // --------------------------------------------------------- gestures

    @Override
    public void onTap() {
        // Reset common's ui timers (the same trick TV's applyTickle does)
        mPlaybackPresenter.onKeyDown(-1);

        showOverlay(!mIsOverlayShown);
    }

    @Override
    public void onVolumeDelta(float delta) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (audioManager == null) {
            return;
        }

        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        if (mDragVolume < 0) {
            mDragVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        mDragVolume = clamp(mDragVolume + delta * max, 0, max);

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(mDragVolume), 0);
        } catch (SecurityException e) {
            // Do not disturb mode
            return;
        }

        showIndicator(R.drawable.playback_ic_volume, percent(mDragVolume / max));
    }

    @Override
    public void onBrightnessDelta(float delta) {
        WindowManager.LayoutParams params = getWindow().getAttributes();

        float current = params.screenBrightness;

        if (current < 0) { // BRIGHTNESS_OVERRIDE_NONE: take the system value as a start
            current = getSystemBrightness();
        }

        float brightness = clamp(current + delta, 0.01f, 1f);
        params.screenBrightness = brightness;
        getWindow().setAttributes(params);

        showIndicator(R.drawable.playback_ic_brightness, percent(brightness));
    }

    private float getSystemBrightness() {
        try {
            int value = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return value / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    @Override
    public void onSeekStart() {
        mSeekAnchorMs = getPositionMs();
        mSeekTargetMs = mSeekAnchorMs;
        // Stop the periodic updater from overwriting the preview position
        mIsScrubbing = true;
    }

    @Override
    public void onSeekDelta(float totalDelta) {
        long durationMs = getDurationMs();

        if (durationMs <= 0 || mSeekAnchorMs < 0) {
            return;
        }

        // A full-width swipe covers SEEK_SWIPE_FRACTION of the video, capped so that very long
        // videos stay controllable and very short ones don't jump wildly
        long spanMs = Math.min((long) (durationMs * SEEK_SWIPE_FRACTION), MAX_SEEK_SWIPE_MS);
        mSeekTargetMs = clampLong(mSeekAnchorMs + (long) (totalDelta * spanMs), 0, durationMs);

        long diffMs = mSeekTargetMs - mSeekAnchorMs;
        String label = String.format(Locale.US, "%s  [%s%s]",
                formatTime(mSeekTargetMs), diffMs >= 0 ? "+" : "-", formatTime(Math.abs(diffMs)));

        showIndicator(diffMs >= 0 ? R.drawable.playback_ic_next : R.drawable.playback_ic_prev, label);

        // Move the scrubber and the clock along so the controls agree with the preview
        if (mSeekBar != null) {
            mSeekBar.setProgress((int) (mSeekTargetMs * SEEK_BAR_MAX / durationMs));
        }

        if (mPositionLabel != null) {
            mPositionLabel.setText(formatTime(mSeekTargetMs));
        }
    }

    /**
     * Pinch out fills the screen (crops the sides/top of the frame), pinch in fits the whole
     * frame inside the screen. Persisted through PlayerData like the TV zoom setting, so the
     * choice survives to the next video.
     */
    @Override
    public void onZoomChanged(boolean fillScreen) {
        int mode = fillScreen ? PlayerConstants.RESIZE_MODE_FIT_BOTH : PlayerConstants.RESIZE_MODE_DEFAULT;

        setResizeMode(mode);
        getPlayerData().setResizeMode(mode);

        showIndicator(fillScreen ? R.drawable.playback_ic_fullscreen : R.drawable.playback_ic_fullscreen_exit,
                getString(fillScreen ? R.string.playback_zoom_fill : R.string.playback_zoom_fit));

        mHandler.removeCallbacks(mHideIndicator);
        mHandler.postDelayed(mHideIndicator, INDICATOR_HIDE_DELAY_MS);
    }

    @Override
    public void onDragEnd() {
        mDragVolume = -1;

        // Commit the scrub only now: seeking on every touch move stutters playback
        if (mSeekAnchorMs >= 0) {
            if (mSeekTargetMs != mSeekAnchorMs) {
                setPositionMs(mSeekTargetMs);
            }

            mSeekAnchorMs = -1;
            mIsScrubbing = false;
        }

        hideIndicator();
    }

    @Override
    public void onSpeedBoostStart() {
        if (mPlayer == null || mSpeedBoosted) {
            return;
        }

        float speed = mExoPlayerController.getSpeed();
        mSavedSpeed = speed > 0 ? speed : 1f;
        mSpeedBoosted = true;

        // Bypass setSpeed(): the boost is temporary and must not be persisted or reported
        mExoPlayerController.setSpeed(SPEED_BOOST);

        showIndicator(R.drawable.playback_ic_speed, getString(R.string.playback_speed_boost));
    }

    @Override
    public void onSpeedBoostEnd() {
        if (!mSpeedBoosted) {
            return;
        }

        mSpeedBoosted = false;

        if (mPlayer != null) {
            mExoPlayerController.setSpeed(mSavedSpeed);
        }

        hideIndicator();
    }

    @Override
    public boolean isDragEnabled() {
        // Volume/brightness swipes are fullscreen-only (in portrait the panel owns the drag)
        return mIsLandscape && !isInPIPMode();
    }

    @Override
    public boolean isGestureAllowedAt(float playerX, float playerY) {
        if (!mIsOverlayShown) {
            return true;
        }

        // Don't fight with the visible controls
        return !isInside(mTopBar, playerX, playerY)
                && !isInside(mCenterBar, playerX, playerY)
                && !isInside(mBottomBar, playerX, playerY);
    }

    private static boolean isInside(View view, float x, float y) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }

        return x >= view.getLeft() && x <= view.getRight() && y >= view.getTop() && y <= view.getBottom();
    }

    private void showIndicator(int iconResId, String text) {
        mIndicatorIcon.setImageResource(iconResId);
        mIndicatorText.setText(text);
        mIndicator.setVisibility(View.VISIBLE);
    }

    private void hideIndicator() {
        mIndicator.setVisibility(View.GONE);
    }

    private static String percent(float value) {
        return Math.round(value * 100) + "%";
    }

    private static long clampLong(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }

        return value > max ? max : value;
    }

    // ------------------------------------------- VideoGroupAdapter.Listener

    @Override
    public void onVideoClicked(Video item) {
        mPlaybackPresenter.onSuggestionItemClicked(item);
    }

    @Override
    public void onVideoLongClicked(Video item) {
        mPlaybackPresenter.onSuggestionItemLongClicked(item);
    }

    @Override
    public void onScrollEnd(Video item) {
        mPlaybackPresenter.onScrollEnd(item);
    }

    // ------------------------------------------------- PlayerView (engine)

    /**
     * Quality label produced by the engine (e.g. "1080p 60fps avc"). The chips are icon-only,
     * so it becomes the chip's tooltip/description rather than a visible label.
     */
    @Override
    public void setQualityInfo(String info) {
        if (mQualityChip != null) {
            CharSequence label = TextUtils.isEmpty(info) ? getString(R.string.playback_quality) : info;
            mQualityChip.setContentDescription(label);
            TooltipCompat.setTooltipText(mQualityChip, label);
        }
    }

    // ------------------------------------------------------------ PlayerUI

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        mSuggestionsAdapter.update(group);

        trackSuggestionGroup(group);
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        // The caller builds a fresh group holding the videos to drop; the shared adapter
        // dispatches on the action, so state it explicitly.
        group.setAction(VideoGroup.ACTION_REMOVE);
        mSuggestionsAdapter.update(group);
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return group != null ? indexOfSuggestionGroup(group.getId()) : -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        return index >= 0 && index < mSuggestionGroups.size() ? mSuggestionGroups.get(index) : null;
    }

    @Override
    public void focusSuggestedItem(int index) {
        // TV-only: moves DPAD focus inside a suggestions row. Phones have no card focus.
    }

    @Override
    public void focusSuggestedItem(Video video) {
        // TV-only (see above).
    }

    @Override
    public void resetSuggestedPosition() {
        if (mSuggestionsView != null) {
            mSuggestionsView.scrollToPosition(0);
        }
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return mSuggestionsAdapter == null || mSuggestionsAdapter.isEmpty();
    }

    @Override
    public void clearSuggestions() {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.clear();
        }

        mSuggestionGroups.clear();
    }

    private void trackSuggestionGroup(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE && group.getPosition() == -1) {
            mSuggestionGroups.clear();
            return;
        }

        if (action == VideoGroup.ACTION_REMOVE || action == VideoGroup.ACTION_REMOVE_AUTHOR
                || action == VideoGroup.ACTION_SYNC || group.isEmpty()) {
            return;
        }

        int index = indexOfSuggestionGroup(group.getId());

        if (index != -1) {
            mSuggestionGroups.set(index, group);
            return;
        }

        int position = group.getPosition();

        if (position < 0 || position > mSuggestionGroups.size()) {
            mSuggestionGroups.add(group);
        } else {
            mSuggestionGroups.add(position, group);
        }
    }

    private int indexOfSuggestionGroup(int groupId) {
        for (int i = 0; i < mSuggestionGroups.size(); i++) {
            if (mSuggestionGroups.get(i).getId() == groupId) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void showOverlay(boolean show) {
        if (show && isInPIPMode()) {
            return; // the UI can't be displayed inside the PIP window
        }

        if (!show && mIsScrubbing) {
            return; // never hide the controls while the user drags the seek bar
        }

        boolean changed = mIsOverlayShown != show;
        mIsOverlayShown = show;
        mOverlay.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            updateProgress();
            mHandler.removeCallbacks(mUpdateProgress);
            mHandler.post(mUpdateProgress);
            scheduleAutoHide();
        } else {
            mHandler.removeCallbacks(mUpdateProgress);
            cancelAutoHide();
        }

        if (changed) {
            mPlaybackPresenter.onControlsShown(show);
        }
    }

    @Override
    public boolean isOverlayShown() {
        return mIsOverlayShown;
    }

    @Override
    public void showSuggestions(boolean show) {
        // In portrait the suggestions list is always on screen below the video; in fullscreen
        // it's hidden. All this can do is bring the controls up and scroll the list to the top.
        showOverlay(show);

        if (show) {
            resetSuggestedPosition();
        }
    }

    @Override
    public boolean isSuggestionsShown() {
        // On TV this means "the user left the controls and is browsing the suggestions row",
        // which makes SuggestionsController SKIP reloading them (see appendSuggestions()).
        // The phone panel is always visible, so answering true would break suggestions entirely.
        return false;
    }

    @Override
    public void showControls(boolean show) {
        showOverlay(show);
    }

    @Override
    public boolean isControlsShown() {
        return mIsOverlayShown;
    }

    @Override
    public int getButtonState(int buttonId) {
        Integer state = mButtonStates.get(buttonId);

        return state != null ? state : BUTTON_OFF;
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        mButtonStates.put(buttonId, buttonState);

        List<View> views = mButtonViews.get(buttonId);

        if (views == null) {
            return; // an id with no control on this UI (overflow-only action): silently stored
        }

        for (View view : views) {
            applyButtonStyle(view, buttonState);
        }
    }

    /**
     * ON (or any non-zero mode, e.g. the repeat modes) is accented, DISABLED is dimmed.
     */
    private void applyButtonStyle(View view, int buttonState) {
        if (buttonState == BUTTON_DISABLED) {
            view.setEnabled(false);
            view.setAlpha(0.4f);
            return;
        }

        view.setEnabled(true);
        view.setAlpha(1f);

        int color = androidx.core.content.ContextCompat.getColor(this,
                buttonState == BUTTON_OFF ? R.color.playback_control : R.color.playback_accent);

        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            button.setIconTint(android.content.res.ColorStateList.valueOf(color));
            button.setTextColor(color);
        } else if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(color);
        }
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        if (mChannelIcon == null) {
            return;
        }

        if (TextUtils.isEmpty(iconUrl)) {
            mChannelIcon.setImageDrawable(null);
            return;
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        Glide.with(this)
                .load(iconUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.playback_channel_placeholder)
                .error(R.drawable.playback_channel_placeholder)
                .into(mChannelIcon);
    }

    @Override
    public void setSeekPreviewTitle(String title) {
        if (mSeekPreviewLabel == null) {
            return;
        }

        mSeekPreviewLabel.setText(title);
        mSeekPreviewLabel.setVisibility(TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
    }

    @Override
    public void setNextTitle(Video nextVideo) {
        if (mNextTitleLabel == null) {
            return;
        }

        if (nextVideo == null || nextVideo.getTitle() == null) {
            mNextTitleLabel.setText(null);
            mNextTitleLabel.setVisibility(View.GONE);
            return;
        }

        String info = nextVideo.getAuthor() != null
                ? nextVideo.getTitle() + " " + Video.TERTIARY_TEXT_DELIM + " " + nextVideo.getAuthor()
                : nextVideo.getTitle();

        mNextTitleLabel.setText(getString(R.string.playback_next_format, info));
        mNextTitleLabel.setVisibility(View.VISIBLE);
    }

    @Override
    public void showDebugInfo(boolean show) {
        // v1: no debug overlay on the phone UI. common's DebugInfoManager needs a dedicated
        // ViewGroup; wire it here when the stats screen is designed.
    }

    @Override
    public void showSubtitles(boolean show) {
        createSubtitleManager();

        if (mSubtitleManager != null) {
            mSubtitleManager.show(show);
        }
    }

    @Override
    public void loadStoryboard() {
        // v1: no seek-preview thumbnails (TV uses Leanback's StoryboardSeekDataProvider).
    }

    @Override
    public void setTitle(String title) {
        if (mOverlayTitle != null) {
            mOverlayTitle.setText(title);
        }

        if (mPanelTitle != null) {
            mPanelTitle.setText(title);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // May be called off the main thread (same as on TV)
        runOnUiThread(() -> {
            if (mProgressBar == null) {
                return;
            }

            // Fix interrupted progress: suggestions finish loading mid-buffer and hide the spinner
            // while the video is still stalled. Defer the hide instead of dropping it — these calls
            // are one-shot, so a swallowed hide would leave the spinner up for the whole video.
            if (!show && isPlayerBuffering()) {
                mProgressHidePending = true;
                return;
            }

            mProgressHidePending = false;
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    /**
     * Only STATE_BUFFERING counts as stalled. Don't fold isLoading() in here: ExoPlayer reports
     * loading whenever it tops the buffer up, which is most of a normal playback.
     */
    private boolean isPlayerBuffering() {
        return mExoPlayerController != null && mExoPlayerController.isBuffering();
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (mSeekBar != null) {
            mSeekBar.setSegments(segments);
        }
    }

    @Override
    public void updateEndingTime() {
        if (mEndingTimeLabel == null || mEndingTimeLabel.getVisibility() != View.VISIBLE) {
            return;
        }

        Video video = getVideo();
        long remainingMs = 0;

        if (video != null && !video.isLive) {
            remainingMs = getDurationMs() - getPositionMs();

            float speed = getSpeed();
            if (speed > 0) {
                remainingMs = (long) (remainingMs / speed);
            }

            if (remainingMs < 0) {
                remainingMs = 0;
            }
        }

        mEndingTimeLabel.setText(remainingMs > 0
                ? String.format("%s %s", Helpers.HOURGLASS, DateHelper.toShortTime(System.currentTimeMillis() + remainingMs))
                : null);
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        // v1: live chat isn't part of the phone player yet.
    }

    // -------------------------------------------------------- PlayerManager

    @Override
    public void setVideo(Video item) {
        mExoPlayerController.setVideo(item);

        if (item == null) {
            return;
        }

        setTitle(item.getTitleFull() != null ? item.getTitleFull() : item.getTitle());

        if (mPanelSecondTitle != null) {
            CharSequence secondTitle = item.getSecondTitleFull();
            mPanelSecondTitle.setText(secondTitle);
            mPanelSecondTitle.setVisibility(TextUtils.isEmpty(secondTitle) ? View.GONE : View.VISIBLE);
        }

        if (mChannelName != null) {
            mChannelName.setText(item.getAuthor());
        }
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController != null ? mExoPlayerController.getVideo() : null;
    }

    @Override
    public void showBackground(String url) {
        if (mBackgroundView == null) {
            return;
        }

        if (TextUtils.isEmpty(url) || isFinishing() || isDestroyed()) {
            return;
        }

        mBackgroundView.setVisibility(View.VISIBLE);
        Glide.with(this).load(url).into(mBackgroundView);
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        if (mBackgroundView == null) {
            return;
        }

        Glide.with(this).clear(mBackgroundView);
        mBackgroundView.setImageDrawable(null);
        mBackgroundView.setVisibility(View.GONE);

        try {
            mRoot.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, colorResId));
        } catch (Exception e) {
            // Resource may come from another module's R: fall back to plain black
            mRoot.setBackgroundColor(0xFF000000);
        }
    }

    @Override
    public void resetPlayerState() {
        mExoPlayerController.resetPlayerState();

        setSeekBarSegments(null);
        setSeekPreviewTitle(null);
        setNextTitle(null);
    }

    @Override
    public boolean isEmbed() {
        return false;
    }

    // ---------------------------------------------------------- PlayerEngine

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mFormatInfo = null;
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mFormatInfo = null;
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mFormatInfo = null;
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mFormatInfo = null;
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mFormatInfo = formatInfo;
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mFormatInfo = null;
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        long durationMs = mExoPlayerController.getDurationMs();

        long liveDurationMs = getVideo() != null ? getVideo().getLiveDurationMs() : 0;

        if (durationMs > Video.MAX_LIVE_DURATION_MS && liveDurationMs != 0) {
            durationMs = liveDurationMs;
        }

        return durationMs;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        if (isFinishing() || isDestroyed()) {
            Log.e(TAG, "Can't restart engine. The player activity is being destroyed.");
            return;
        }

        releasePlayer();
        initializePlayer();
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mPlaybackPresenter.onEngineReleased();
            mPlaybackPresenter.onEngineInitialized();
        }
    }

    @Override
    public void blockEngine(boolean block) {
        mIsEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mIsEngineBlocked;
    }

    @Override
    public boolean isInPIPMode() {
        return VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
        // NOTE: the real speed isn't applied immediately, so use the supplied value
        setButtonState(BUTTON_SPEED, speed != 1.0f ? BUTTON_ON : BUTTON_OFF);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        mExoPlayerController.setPitch(pitch);
    }

    @Override
    public float getPitch() {
        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    @Override
    public void setResizeMode(int mode) {
        mPlayerView.setResizeMode(mode);
    }

    @Override
    public int getResizeMode() {
        return mPlayerView.getResizeMode();
    }

    @Override
    public void setZoomPercents(int percents) {
        mZoomPercents = percents > 0 ? percents : 100;

        applyVideoTransform();
    }

    @Override
    public void setAspectRatio(float ratio) {
        AspectRatioFrameLayout contentFrame = getContentFrame();

        if (contentFrame != null && ratio > 0) {
            // NOTE: overwritten by the engine on the next video size change (2.10 has no
            // dedicated 'user aspect ratio' hook on PlayerView)
            contentFrame.setAspectRatio(ratio);
        }
    }

    @Override
    public void setRotationAngle(int angle) {
        mRotationAngle = angle;

        applyVideoTransform();
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
        mFlipEnabled = enabled;

        applyVideoTransform();
    }

    @Override
    public void setVideoGravity(int gravity) {
        AspectRatioFrameLayout contentFrame = getContentFrame();

        if (contentFrame == null) {
            return;
        }

        ViewGroup.LayoutParams params = contentFrame.getLayoutParams();

        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = gravity != 0 ? gravity : Gravity.CENTER;
            contentFrame.setLayoutParams(params);
        }
    }

    /** Zoom + flip + rotation are applied together to the video surface. */
    private void applyVideoTransform() {
        if (mPlayerView == null) {
            return;
        }

        float scale = mZoomPercents / 100f;

        mPlayerView.setScaleX(mFlipEnabled ? -scale : scale);
        mPlayerView.setScaleY(scale);
        mPlayerView.setRotation(mRotationAngle);
    }

    private AspectRatioFrameLayout getContentFrame() {
        View view = mPlayerView != null
                ? mPlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_content_frame) : null;

        return view instanceof AspectRatioFrameLayout ? (AspectRatioFrameLayout) view : null;
    }
}
