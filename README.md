# SmartTube Droid

**An ad-free YouTube client for Android phones.** A touch-first port of
[SmartTube](https://github.com/yuliskov/SmartTube), which is built for Android TV and
navigated with a remote. This fork keeps SmartTube's engine and replaces the TV interface
with one designed for a phone screen and your thumbs.

> ⚠️ **Early development — version 0.1.0.** The app builds and installs, but it has not yet
> been through real-world testing. Expect bugs. There is no release build yet; see
> [Building](#building) if you want to try it now.

## Why this exists

SmartTube blocks YouTube ads and skips sponsor segments, and it is excellent — on a TV.
On a phone it still works, but every screen assumes a D-pad: focus rectangles, no gestures,
text sized for a couch three metres away. This fork keeps everything that makes SmartTube
worth using and rebuilds only the parts you touch.

## What it does

Everything SmartTube does, because it runs the same engine:

- **No ads.** Ads are never fetched in the first place — the app talks to YouTube's internal
  API and simply doesn't request or parse ad data. Nothing to block, nothing to fail.
- **SponsorBlock.** Sponsor segments, intros, self-promos and more are skipped automatically,
  with the segments marked directly on the seek bar.
- **No Google Play Services required.** Sign in with a code, or don't sign in at all.
- **Full settings.** All of SmartTube's settings, rendered as phone bottom sheets.

Rebuilt for touch:

- **Player gestures** modelled on the YouTube app — tap for controls, double-tap either side
  to seek, swipe vertically for volume (right) and brightness (left), long-press for 2× speed.
- **Portrait and landscape**, with picture-in-picture and background audio.
- **Home, search, channels and playlists** as scrollable grids and rows, with voice search.

Not yet implemented: live chat, the comments sheet, and seek-preview thumbnails.

## Installing

Once releases are published, download the APK from the
[Releases page](https://github.com/systematiq-one/SmartTube-droid/releases) and open it on
your phone. Android will ask you to allow installing from unknown sources — this is normal
for apps distributed outside the Play Store.

Pick `arm64-v8a` for any phone from roughly 2016 onward. If unsure, `universal` works
everywhere but is larger.

To get updates automatically, use [Obtainium](https://github.com/ImranR98/Obtainium) and
point it at this repository.

**Requires Android 5.0 (API 21) or newer.**

## Building

Requires JDK 17 and the Android SDK (platform 34, build-tools 30.0.3, NDK 21.0.6113669).

```bash
git clone --recurse-submodules https://github.com/systematiq-one/SmartTube-droid.git
cd SmartTube-droid
./gradlew :smarttubedroid:assembleDebug
```

APKs land in `smarttubedroid/build/outputs/apk/debug/`.

The submodules matter — `MediaServiceCore` and `SharedModules` contain the YouTube API layer,
and the build fails without them.

## How the code is organised

| Module | Role |
|---|---|
| `smarttubedroid` | **The phone app.** Touch UI only — activities, layouts, gestures. |
| `common` | Shared logic: presenters, ad-free data flow, SponsorBlock, settings, player engine. |
| `MediaServiceCore` *(submodule)* | YouTube API layer. Upstream — do not fork lightly. |
| `SharedModules` *(submodule)* | Utilities, preferences, build constants. Upstream. |
| `smarttubetv` | The original Android TV app. Still builds; kept as reference. |

The phone app implements the view interfaces defined in `common`, so business logic is shared
rather than duplicated. Adding a feature usually means implementing a screen, not porting
logic. See `smarttubedroid/DESIGN.md` for the contract, including build constraints that are
easy to trip over.

## Staying current with upstream

YouTube regularly changes its internal API, which breaks third-party clients until a fix is
written. Those fixes come from upstream SmartTube. Keeping this repository a **fork** is what
makes them a merge rather than a rewrite:

```bash
git remote add upstream https://github.com/yuliskov/SmartTube.git
git fetch upstream && git merge upstream/master
git submodule update --remote    # MediaServiceCore / SharedModules
```

Conflicts should be rare — the phone UI lives in its own module and barely touches shared code.

## Credits and licence

SmartTube Droid is a fork of [**SmartTube** by Yuri Liskov](https://github.com/yuliskov/SmartTube),
who wrote essentially all of the hard parts. Released under the
[MIT Licence](LICENSE), as is the original.

Not affiliated with, endorsed by, or connected to YouTube or Google.
