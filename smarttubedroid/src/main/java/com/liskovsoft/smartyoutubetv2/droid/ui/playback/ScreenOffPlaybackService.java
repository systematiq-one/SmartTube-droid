package com.liskovsoft.smartyoutubetv2.droid.ui.playback;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build.VERSION;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.droid.R;

/**
 * Keeps the sound going after the user switches the screen off or leaves the video ('Listen in background').<br/>
 * Android freezes or kills a background app within seconds unless it runs a foreground service, and the
 * CPU and Wi-Fi go to sleep with the screen. This service is the media playback foreground service
 * (with the usual media notification bound to the player's session) and holds a wake lock and a
 * Wi-Fi lock while audio is actually playing.<br/>
 * It is started while the player is in the foreground: Android 12+ refuses to start a foreground
 * service from the background, so it can't wait for the screen to go off. Later changes reach the
 * running instance directly.
 */
public class ScreenOffPlaybackService extends Service {
    private static final String TAG = ScreenOffPlaybackService.class.getSimpleName();
    private static final String PREFS_NAME = "droid_playback";
    private static final String PREF_ENABLED = "screen_off_playback";
    private static final String PREF_PREVIOUS_BACKGROUND_MODE = "previous_background_mode";
    private static final String CHANNEL_ID = "screen_off_playback";
    private static final int NOTIFICATION_ID = 0x5C0FF;
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_AUTHOR = "author";
    private static final String EXTRA_PLAYING = "playing";
    @SuppressLint("StaticFieldLeak") // cleared in onDestroy
    private static ScreenOffPlaybackService sInstance;
    private static MediaSessionCompat.Token sSessionToken;
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;
    private String mTitle;
    private String mAuthor;

    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    /**
     * The background mode to put back when 'Listen in background' is switched off
     */
    public static int getPreviousBackgroundMode(Context context) {
        return getPrefs(context).getInt(PREF_PREVIOUS_BACKGROUND_MODE, PlayerConstants.BACKGROUND_MODE_DEFAULT);
    }

    public static void setPreviousBackgroundMode(Context context, int mode) {
        getPrefs(context).edit().putInt(PREF_PREVIOUS_BACKGROUND_MODE, mode).apply();
    }

    /**
     * Updates the running service (title, wake locks). Never starts it: this may be called from the background.
     */
    public static void update(String title, String author, boolean isPlaying) {
        if (sInstance != null) {
            sInstance.apply(title, author, isPlaying, false);
        }
    }

    /**
     * Starts the service or updates the running one. Call only while the player is in the foreground.
     */
    public static void start(Context context, MediaSessionCompat.Token sessionToken, String title, String author, boolean isPlaying) {
        boolean sessionChanged = sSessionToken != sessionToken;
        sSessionToken = sessionToken;

        if (sInstance != null) {
            sInstance.apply(title, author, isPlaying, sessionChanged);
            return;
        }

        Intent intent = new Intent(context, ScreenOffPlaybackService.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_AUTHOR, author)
                .putExtra(EXTRA_PLAYING, isPlaying);

        try {
            if (VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException (Android 12+) when called from the background
            Log.e(TAG, "Can't start the service: %s", e.getMessage());
        }
    }

    public static void stop(Context context) {
        sSessionToken = null;

        if (sInstance != null) {
            context.stopService(new Intent(context, ScreenOffPlaybackService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":screen_off_playback");
            mWakeLock.setReferenceCounted(false);
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, getPackageName() + ":screen_off_playback");
            mWifiLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        String author = intent != null ? intent.getStringExtra(EXTRA_AUTHOR) : null;
        boolean isPlaying = intent != null && intent.getBooleanExtra(EXTRA_PLAYING, false);

        mTitle = title;
        mAuthor = author;

        try {
            if (VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, createNotification());
            }
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException: the screen went off before the service came up
            Log.e(TAG, "Can't enter the foreground: %s", e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        setLocksHeld(isPlaying);

        // The player lives in the activity: nothing to restore if the process dies
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        setLocksHeld(false);

        if (sInstance == this) {
            sInstance = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void apply(String title, String author, boolean isPlaying, boolean force) {
        setLocksHeld(isPlaying);

        if (force || title != null && (!title.equals(mTitle) || author != null && !author.equals(mAuthor))) {
            mTitle = title != null ? title : mTitle;
            mAuthor = author != null ? author : mAuthor;
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification());
            }
        }
    }

    /**
     * Only while sound is playing: a paused player shouldn't keep the phone awake.
     */
    @SuppressLint("WakelockTimeout") // released on pause, stop and destroy
    private void setLocksHeld(boolean held) {
        if (mWakeLock != null) {
            if (held && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!held && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }

        if (mWifiLock != null) {
            if (held && !mWifiLock.isHeld()) {
                mWifiLock.acquire();
            } else if (!held && mWifiLock.isHeld()) {
                mWifiLock.release();
            }
        }
    }

    private Notification createNotification() {
        if (VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                        getString(R.string.screen_off_playback_channel), NotificationManager.IMPORTANCE_LOW));
            }
        }

        // The player runs in a task of its own (see the manifest): bring that task back, not the
        // browse task the launcher intent would resume
        Intent launch = new Intent(this, PlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | (VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle();
        if (sSessionToken != null) {
            style.setMediaSession(sSessionToken); // play/pause/next on the lock screen and in the shade
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(mTitle != null ? mTitle : getString(R.string.app_name))
                .setContentText(mAuthor)
                .setContentIntent(contentIntent)
                .setStyle(style)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .build();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
