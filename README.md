<img src="logo.png" width="110" alt="logo">

# SmartTube Droid

**Watch videos on your Android phone with no ads.** Free and open source.

It is the phone version of [SmartTube](https://github.com/yuliskov/SmartTube), the popular
ad-free app for Android TV, with an interface made for touchscreens.

## What you get

- No ads
- Sponsor segments and intros skipped automatically ([SponsorBlock](https://sponsor.ajay.app))
- Listen in the background, even with the phone locked
- Picture-in-picture
- Simple gestures: double-tap to skip, swipe for volume and brightness, pinch to zoom,
  hold for 2× speed
- Sign in to see your subscriptions, playlists and history
- Light and dark theme, following your phone
- No proprietary services needed, no tracking

## Install

You have two options. **Option 2 is recommended**: you get updates automatically.

### Option 1: download the APK

1. On your phone, open the
   **[latest release](https://github.com/systematiq-one/SmartTube-droid/releases/latest)**.
2. Under **Assets**, tap the file ending in **`arm64-v8a.apk`**. If it won't install, use
   the one ending in **`universal.apk`** instead.
3. Open the downloaded file. If your phone asks, allow installing apps from this source.
4. Tap **Install**.

To update, repeat these steps when a new version comes out.

### Option 2: install with Obtainium (automatic updates)

[Obtainium](https://github.com/ImranR98/Obtainium) is a free app that installs apps straight
from their GitHub page and keeps them up to date.

1. Install Obtainium from its
   [releases page](https://github.com/ImranR98/Obtainium/releases/latest) (pick the
   `arm64-v8a` APK, same as above).
2. Open Obtainium and tap **Add App**.
3. In **App Source URL**, paste:

   ```
   https://github.com/systematiq-one/SmartTube-droid
   ```

4. Tap **Add**, then **Install**.

Obtainium now checks for new versions and lets you know when an update is ready.

**Requires Android 5.0 or newer.** Only download the app from this page; copies on other
websites may contain malware.

## For developers

Needs JDK 17 and the Android SDK (platform 34, build-tools 30.0.3, NDK 21.0.6113669).

```bash
git clone --recurse-submodules https://github.com/systematiq-one/SmartTube-droid.git
cd SmartTube-droid
./gradlew :smarttubedroid:assembleDebug
```

The phone interface lives in `smarttubedroid`. Everything else (playback, ad and sponsor
skipping, the video service connection) comes from SmartTube and is kept in sync with it.
Pushing a `v*` tag builds and publishes a release; its "What's new" text comes from
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## Credits

Built on [**SmartTube** by Yuri Liskov](https://github.com/yuliskov/SmartTube), who wrote
nearly all of the hard parts. MIT licence, like the original.

This is an unofficial app, not affiliated with any video platform. It is provided as is,
without warranty.
