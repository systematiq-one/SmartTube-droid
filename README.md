<img src="logo.png" width="110" alt="logo">

# SmartTube Droid

**A free and open-source media client for Android phones.** It lets you browse and play content
from various public sources in an interface built for a touchscreen.

A fork of [SmartTube](https://github.com/yuliskov/SmartTube), which targets Android TV and is
driven with a remote. This version keeps the same engine and replaces the interface with one
designed for a phone screen and your thumbs.

> ⚠️ **Early development.** The app works, but it is young and rough in places. Expect bugs,
> and please report them.

### ✅ Features

- No ads and no sponsored interruptions
- Sponsor segments, intros and self-promotion skipped automatically, marked on the seek bar
- Touch gestures: double-tap to seek, swipe to scrub, swipe for volume and brightness,
  pinch to fit or fill the screen, long-press for double speed
- Background playback and picture-in-picture
- Adjustable playback speed, quality and subtitles
- Sign in with a code to get your subscriptions, playlists and history
- Portrait and landscape, with a full-screen player
- Works without any proprietary vendor services
- Not published on any app store, so nothing to track you

### ❌ Limitations

- Phones only — for TVs and TV boxes use [SmartTube](https://github.com/yuliskov/SmartTube)
- Live chat and comments are not implemented yet
- Seek-preview thumbnails are not implemented yet
- Voice search quality depends on the recogniser your device provides

## Installation

Not available in any app store, and not planned. Download it here:

**[⬇ Latest release](https://github.com/systematiq-one/SmartTube-droid/releases/latest)**

Pick **`arm64-v8a`** for any phone from roughly 2016 onward. If it refuses to install, use the
**`universal`** build, which works everywhere but is larger.

Open the downloaded file on your phone and allow installing from unknown sources when prompted.
This warning is normal for anything installed outside an app store.

**Requires Android 5.0 or newer.**

> Only download releases from this repository. Copies on APK sites and blogs are uploaded by
> other people and may carry malware or ads.

### Automatic updates

Add this repository to [Obtainium](https://github.com/ImranR98/Obtainium), a free app that
watches releases and installs new versions for you.

> Updating from a build signed with a different key? Uninstall the old app first — Android
> blocks updates when the signature changes.

## Build

Requires JDK 17 and the Android SDK (platform 34, build-tools 30.0.3, NDK 21.0.6113669).

```bash
git clone --recurse-submodules https://github.com/systematiq-one/SmartTube-droid.git
cd SmartTube-droid
./gradlew :smarttubedroid:assembleDebug
```

APKs land in `smarttubedroid/build/outputs/apk/debug/`.

The submodules are not optional — they contain the service layer the app is built on.

Releases are produced by CI: pushing a tag such as `v0.2.0` builds, signs and publishes the
APKs automatically (see `.github/workflows/release.yml`).

## How the code is organised

| Module | Role |
|---|---|
| `smarttubedroid` | **The phone app.** Touch interface only — screens, layouts, gestures. |
| `common` | Shared logic: presenters, data flow, segment skipping, settings, player engine. |
| `MediaServiceCore` *(submodule)* | The media service layer. Upstream — do not fork lightly. |
| `SharedModules` *(submodule)* | Utilities, preferences, build constants. Upstream. |
| `smarttubetv` | The original TV app. Still builds; kept as a reference. |

The phone app implements the view interfaces declared in `common`, so logic is shared rather
than duplicated — adding a feature usually means writing a screen, not porting logic.
`smarttubedroid/DESIGN.md` documents the contract and the build constraints worth knowing.

## Staying current

The services this app reads from change often, and those changes can break clients until
someone adapts to them. That work happens upstream, and keeping this repository a fork is what
turns those fixes into a merge rather than a rewrite:

```bash
git remote add upstream https://github.com/yuliskov/SmartTube.git
git fetch upstream && git merge upstream/master
git submodule update --remote
```

Conflicts should be rare — the phone interface lives in its own module and barely touches
shared code.

## Credits

A fork of [**SmartTube** by Yuri Liskov](https://github.com/yuliskov/SmartTube), who wrote
essentially all of the hard parts. Segment skipping is powered by the community-maintained
[SponsorBlock](https://sponsor.ajay.app) database.

Released under the [MIT Licence](LICENSE), as is the original.

## Liability

This is an independent, unofficial client, not affiliated with or endorsed by any service it
can access. It is provided as is, without warranty of any kind. It hosts and distributes no
content itself; it only presents what publicly accessible sources return. You are responsible
for how you use it and for complying with the terms of any service you connect to.
