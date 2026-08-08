# SmartTube Droid — touch UI module design contract

Phone (touch-first) UI for SmartTube. This module implements the view interfaces defined in
`common` with Material touch screens; ALL business logic (ad-free data, SponsorBlock, accounts,
settings) stays in `common` + `MediaServiceCore` and must not be duplicated.

Detailed subsystem maps (READ THE ONE FOR YOUR SCREEN before coding) live in
`C:\Users\Systematiq\.claude\jobs\c966b4e5\tmp\reports\`:
- `result0.md` app wiring/boot, `result6.md` view interfaces, `result12.md` playback stack,
- `result8.md` dialog/settings system, `result10.md` browse/search/channel data flow,
- `result9.md` sign-in/accounts, `result11.md` touch support, `result7.md` module graph,
- `result13.md` verified corrections (READ ALWAYS — traps live here).

## Hard rules (violating any of these breaks the build or the app)

1. **Java 8 only.** No Kotlin in this module, no lambdas requiring newer desugaring than what
   AGP 7.4.2/Java 8 provides (plain lambdas/method refs are fine). Match the code style of
   `smarttubetv` (this is a fork; upstream-similar style preferred).
2. **No AppCompatActivity.** All activities extend
   `com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity` (which extends common's
   `MotherActivity` → `androidx.fragment.app.FragmentActivity`). Consequence: Material widgets
   must be written with EXPLICIT tags in layouts (`com.google.android.material.button.MaterialButton`,
   not `<Button>`); auto-inflation does not happen.
3. **Material Components 1.5.0** — do not use APIs added after 1.5.0. Theme is
   `Theme.SmartTubeDroid` (MaterialComponents.DayNight.NoActionBar), already defined.
   **Do not use `MaterialCardView`** (or any `app:card*` attribute): androidx.cardview's
   attributes do not reach the resource linker in this build. Use a plain container with
   a rounded-shape background drawable plus `android:elevation` instead.
4. **Stock `androidx.fragment` and `androidx.leanback` are globally excluded** in the root
   build.gradle. Fragments come from the local `:fragment-1.1.0` module (already a dependency).
   Never add a dependency that needs a newer androidx.fragment. Never import `androidx.leanback.*`.
5. **ExoPlayer is the local patched 2.10.6 fork** (`com.google.android.exoplayer2.*`). Never
   reference Media3 or newer ExoPlayer APIs.
6. **Do not edit files outside `smarttubedroid/`** unless your prompt explicitly says so.
   Do not edit files owned by other agents (see ownership below).
7. **Resource naming:** every layout/drawable/id you create is prefixed with your screen name
   (`browse_`, `playback_`, `dialog_`, `search_`, `channel_`, `signin_`, `misc_`). Shared
   resources (owned by the shared-ui agent only) use `shared_`. Strings you add go in
   YOUR OWN file `res/values/strings_<screen>.xml`. This prevents parallel-agent collisions.
   Reuse common's existing strings (`com.liskovsoft.smartyoutubetv2.common.R.string.*`) for
   user-facing text wherever one exists — they are translated already.
8. **Presenter lifecycle wiring** (identical for every screen):
   `Presenter.instance(ctx).setView(this)` in onCreate; `presenter.onViewInitialized()` when UI
   is ready; `onViewResumed()/onViewPaused()` in onResume/onPause; `onViewDestroyed()` in
   onDestroy. Never call presenters' internal methods reflectively; never instantiate presenters.
9. **Package layout:** `com.liskovsoft.smartyoutubetv2.droid.ui.<screen>`. Activities are already
   declared in `src/main/AndroidManifest.xml` with exact names — match them.
10. **Images:** plain Glide, mirroring TV usage:
    `Glide.with(ctx).load(video.getCardImageUrl()).apply(ViewUtil-like options).into(view)` with
    placeholder/error fallback. No GlideApp/AppGlideModule.

## Shared UI components (owned by the shared-ui agent; everyone else codes against this API)

Package `com.liskovsoft.smartyoutubetv2.droid.ui.shared`:

```java
// RecyclerView adapter for one VideoGroup stream. Implements ALL VideoGroup delta actions:
// ACTION_REPLACE (clear+add), ACTION_APPEND (same group instance re-sent: append only
// videos.subList(oldSize, newSize)), ACTION_PREPEND, ACTION_REMOVE, ACTION_REMOVE_AUTHOR,
// ACTION_SYNC (rebind changed items in place, match by equals()).
public class VideoGroupAdapter extends RecyclerView.Adapter<VideoCardHolder> {
    public interface Listener {
        void onVideoClicked(Video item);
        void onVideoLongClicked(Video item);   // context menu
        void onScrollEnd(Video lastItem);      // pagination: called near list end
    }
    public VideoGroupAdapter(Listener listener);                 // grid card style
    public VideoGroupAdapter(Listener listener, boolean isRow);  // horizontal row card style
    public void update(VideoGroup group);   // dispatches on group.getAction()
    public void clear();
    public boolean isEmpty();
    public Video getLastItem();
}

// ViewHolder + card binding: title, secondTitle, thumbnail (Glide), duration badge,
// LIVE/SHORTS/NEW badges, percentWatched progress bar. Layout: res/layout/shared_video_card.xml
// (grid) and shared_video_card_row.xml (small row card).
public class VideoCardHolder extends RecyclerView.ViewHolder { ... }

// Vertical RecyclerView of row entries: each row = title + horizontal RecyclerView of cards.
// Feed with VideoGroup deltas keyed by group.getId() (one inner adapter per group id).
public class VideoRowsAdapter extends RecyclerView.Adapter<...> {
    public VideoRowsAdapter(VideoGroupAdapter.Listener listener);
    public void update(VideoGroup group);    // routes to the row with group.getId(), creates row if new
    public void clear();
    public boolean isEmpty();
}
```

Scroll-end detection: adapters call `Listener.onScrollEnd(getLastItem())` when binding a position
within 6 items of the end (mirrors TV's `GRID_SCROLL_CONTINUE_NUM` behavior). Presenters derive
the continuation from `item.getGroup()`, so the last ADAPTER item must be passed.

## Screen ownership and view contracts

| Agent | Owns package(s) | Implements |
|---|---|---|
| shared-ui | `ui.shared` | adapters/cards above + `shared_` resources |
| browse | `ui.browse` | `BrowseView` |
| playback | `ui.playback` | `PlaybackView` |
| dialogs | `ui.dialogs` | `AppDialogView` |
| search | `ui.search` | `SearchView` |
| channel | `ui.channel`, `ui.channeluploads` | `ChannelView`, `ChannelUploadsView` |
| misc | `ui.signin`, `ui.adddevice`, `ui.webbrowser` | `SignInView`, `AddDeviceView`, `WebBrowserView` |

Already done (do not recreate): `DroidApplication`, `ui.base.DroidActivity`, `ui.splash.SplashActivity`,
manifest, themes, build.gradle.

## UX blueprint (YouTube-app-inspired, adapted)

- **Browse**: top app bar (app logo, search icon → `SearchPresenter.instance(ctx).startSearch(null)`,
  account avatar → `AccountSelectionPresenter.instance(ctx).nextAccountOrDialog()` on tap /
  settings on long-press). Below: horizontally scrollable `TabLayout` of dynamic sections
  (driven by `addSection/removeSection/selectSection`). Content area swaps per section:
  grid sections → 2-column portrait grid (`VideoGroupAdapter`), row sections → `VideoRowsAdapter`,
  settings section → simple list of `SettingsItem` (icon+title) from the `SettingsGroup` payload.
  Pull-to-refresh (`SwipeRefreshLayout` if available at forced androidx versions — otherwise a
  refresh item in the app bar) → `BrowsePresenter.refresh()`.
- **Playback**: portrait = 16:9 video pinned top + metadata row (title, channel, action chips) +
  suggestions list below; landscape/fullscreen = video fills screen, controls overlay.
  Gestures: single tap toggles controls overlay; double-tap left/right = ±seek with
  `YouTubeOverlay` ripple (doubletapplayerview); vertical swipe on right half = volume,
  left half = brightness (fullscreen only); long-press = 2x speed while held (YouTube-style);
  seekbar scrubbing with SponsorBlock segment coloring (`setSeekBarSegments`).
  Buttons report `presenter.onButtonClicked(R.id.action_*, state)` using common's action ids.
- **Dialogs**: `BottomSheetDialog`-style stack (nested `show()` calls push screens); radio/check
  lists as RecyclerView rows; must honor isExpandable/isTransparent/isOverlay semantics and
  reimplement the `getRequired()`/`getRadio()` enforcement from TV's `AppPreferenceManager`.
- **Search**: text field in app bar (IME search action → `presenter.onSearch(text)`), tag chips,
  results grid, mic button → `startVoiceRecognition()` via system `RecognizerIntent`.
- **Channel**: rows UI like Browse rows + header. ChannelUploads: plain grid.
- **SignIn**: card with big user code + "Open yt.be/activate" button (Custom Tab / browser Intent)
  + hint text; auto-closes on success.

## Verification

The module must compile with:
`.\gradlew.bat :smarttubedroid:assembleDebug` (JAVA_HOME = `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`).
The build-fix phase owns cross-agent reconciliation; still, compile-check your own code mentally
against the real interfaces — read the actual `common` sources you call, never guess signatures.
