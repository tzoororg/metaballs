# Native Android App Plan — Screensaver + Live Wallpaper

Goal: turn the metaballs animation (currently a GitHub Pages web page) into a
**sideloaded Android app** that registers as a real Android **screen saver
(DreamService)** and an animated **live wallpaper**, with room to add more
animations and a photo carousel later. No Play Store — `adb install` only.

Decisions (from the user): **Both** mechanisms (DreamService + Live Wallpaper),
**no launcher icon** (configure from Android's own Screen-saver / Wallpaper
settings screens).

---

## 1. How — the shape of the thing

A real Android screensaver *must* be a native app: only a `DreamService` can be
selected under **Settings → Screen saver**, and only a `WallpaperService` can be
selected as a live wallpaper. A PWA / "install to home screen" web app cannot do
either. So we need a thin native (Kotlin) shell. The trick is keeping it thin.

Two rendering paths, because the two Android surfaces have different constraints:

| Surface | Host | Reuses | Notes |
|---|---|---|---|
| **DreamService** | `WebView` | `index.html` **as-is** (and every future HTML animation + the photo carousel) | Runs while charging/docked. Web-friendly. |
| **Live Wallpaper** | native GLES2 on the wallpaper `Surface` | the **GLSL shader** (`fsrc`) almost verbatim; the small JS motion loop → Kotlin | Always-on. No WebView here — WallpaperService gives a raw Surface. |

Why this split is the lazy-correct one:
- The **fragment shader is already GLSL ES** — it drops into a native GLES2
  wallpaper with near-zero changes. The only rewrite is the ~100 lines of JS
  that move balls and upload uniforms → straightforward Kotlin.
- The **DreamService reuses the whole HTML page**, so any web animation we add
  later (and the photo carousel) becomes a screensaver for free — just another
  asset URL. Only the *wallpaper* needs per-animation native work.

### Serving the HTML inside the app
Bundle `index.html` into `assets/` and serve it through **`WebViewAssetLoader`**
at `https://appassets.androidplatform.net/…`. This gives a real `https` origin
(WebGL's origin requirement — same reason `file://` fails in Chrome — is
satisfied cleanly) without shipping a web server.

### Single source of truth
Keep **one `index.html` at the repo root** (still deployable to GitHub Pages).
A Gradle `Copy` task (or a 2-line build script) copies it into
`android/app/src/main/assets/` at build time. Do **not** fork the animation.

### Repo layout
```
/index.html            ← unchanged, still the source; Pages still works
/android/              ← new: Gradle project (thin Kotlin shell)
   app/src/main/
      assets/index.html        (copied at build time — gitignored)
      java/.../MetaballsDream.kt         (WebView DreamService)
      java/.../MetaballsWallpaper.kt     (GLES2 WallpaperService, phase 2)
      java/.../DreamSettingsActivity.kt  (animation picker, no launcher icon)
      res/xml/dream.xml, wallpaper.xml   (service descriptors)
      AndroidManifest.xml
```

---

## 2. Do we need to modify the MCPs / testing setup?

**Mostly no — the current chrome-devtools-mcp workflow carries over for the
DreamService**, because the animation still runs in a WebView and WebViews speak
the same CDP protocol as Chrome:

- In the app, call `WebView.setWebContentsDebuggingEnabled(true)` (debug builds).
  The WebView then exposes a devtools socket
  (`localabstract:webview_devtools_remote_<pid>`). `chrome-devtools-mcp` /
  `chrome://inspect` attach to it exactly like a Chrome tab → **`take_screenshot`,
  `list_console_messages`, reload-with-devtools all keep working** for judging the
  render and catching load-time errors. This is the big win: the hard-won phone
  testing loop from `PHONE-TESTING.md` survives the transition.

**New pieces needed** (native surfaces have no CDP):
- **`adb install -r android/app/build/outputs/apk/debug/app-debug.apk`** to deploy.
- **Native screenshots** for the Dream preview and the live wallpaper (no WebView
  in the wallpaper): `adb exec-out screencap -p > shot.png` then read the PNG. Or
  **mobile-mcp** (`mobile_take_screenshot`, `mobile_install_app`,
  `mobile_launch_app`) — it enumerated the device this session, so it's usable;
  keep `adb screencap` as the reliable fallback (mobile-mcp's env is fixed at
  session start and sometimes sees zero devices — documented in CLAUDE.md).
- **`adb logcat`** for native GLES / service errors (replaces the console for the
  wallpaper path).
- **Dream preview without waiting for charging**:
  `adb shell am start -n com.android.systemui/.dreams.SomnambulatorAlias`
  (or trigger via `dumpsys`), and/or set it active:
  `adb shell settings put secure screensaver_components <pkg>/.MetaballsDream`.
- **Reverse tunnel (`phone-connect.sh`) is only needed for the old live-URL flow.**
  Once the HTML is a bundled asset, iteration is: edit → `gradlew installDebug` →
  screenshot. For pure HTML/shader tweaks you can *still* iterate the old fast
  way (localhost + reverse tunnel + Chrome tab) and only rebuild the APK to
  validate it inside the real Dream/wallpaper.

**No brand-new MCP server required.** The gap is native screenshots + logcat +
install, all covered by `adb` (already on PATH) and the existing mobile-mcp.
Optional nicety: a tiny `phone-app.sh` helper mirroring `phone-connect.sh`
(build → install → screencap) to make the loop one command.

### Build environment — DONE (Phase 0, 2026-07-24)
Installed and verified on this machine:
- **JDK 21.0.11** (Temurin, full JDK — the pre-existing Adoptium install was a
  *JRE* with no `javac`): `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`
  → set `JAVA_HOME` to this for Gradle (NOT the `jre-…` sibling).
- **Android SDK** at `C:\Users\tzoor\AppData\Local\Android\Sdk` now has
  `cmdline-tools\latest` (sdkmanager 12.0), `platform-tools;37.0.0`,
  `platforms;android-35`, `build-tools;35.0.0` (aapt2 present).
- The **Gradle wrapper** (`gradlew`, added in Phase 1) fetches Gradle itself —
  no global Gradle install needed.

To re-run from scratch elsewhere:
```bash
export JAVA_HOME=".../Eclipse Adoptium/jdk-21.0.11.10-hotspot"   # a JDK, not a JRE
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

---

## 3. What to pay attention to

- **DreamService quirks:** `setInteractive(false)`, `setFullscreen(true)`,
  keep-screen-on flag, WebView hardware acceleration on. **Stop the WebView in
  `onDreamingStopped()`** (`onPause`/`loadUrl("about:blank")`) so the GL context
  and animation don't leak / drain battery after the dream ends.
- **Live wallpaper visibility = battery:** it renders *always*. Must **pause the
  GL render loop in `onVisibilityChanged(false)`** (wallpaper hidden behind an
  app) and resume on `true`. Honor `prefers-reduced-motion` intent by offering a
  "reduce motion / lower FPS" toggle in wallpaper settings. This is the single
  biggest footgun of the whole project.
- **Signing:** sideloading needs a signed APK; the **debug keystore is fine** for
  personal use. Keep a **stable keystore** so `adb install -r` upgrades in place
  instead of forcing uninstall (which would drop the screensaver selection).
- **No launcher icon, but still configurable:** with no `MAIN/LAUNCHER`
  activity, the animation picker lives in the **Dream's settings activity** (the
  gear next to the screensaver in Settings) and the **wallpaper's settings
  activity** — the Android-idiomatic place anyway.
- **Min SDK:** DreamService is API 17+, `WebViewAssetLoader` needs AndroidX.
  Target a modern API (34/35), min ~24 is safe.
- **Shader parity:** the wallpaper is a *second* renderer of the same field.
  Keep the GLSL identical to `fsrc`; port only the JS motion invariants (unit-
  vector velocities scaled by `SPEED`, rotate-don't-add — see CLAUDE.md) so the
  wallpaper and the web page look the same. Divergence here = two things to tune.
- **The old `const chrome` parse-time bug** is Chrome-specific and irrelevant in a
  WebView, but keep the fix — the same `index.html` still ships to Pages.
- **Thermals:** the field shader is GPU-heavy. Fine while charging (Dream). For
  the always-on wallpaper, cap FPS and pause when hidden (above).

---

## 4. Implementation phases

**Phase 0 — build env (one-time). ✅ DONE 2026-07-24.** JDK 21, cmdline-tools,
`platform-tools;37`, `platforms;android-35`, `build-tools;35.0.0` all installed
and verified (see "Build environment" above). Only remaining check —
`gradlew tasks` — happens once the wrapper is scaffolded in Phase 1.

**Phase 1 — DreamService screensaver (reuses `index.html`).** *Ships the core ask.*
1. Scaffold minimal Gradle project under `/android` (no launcher activity).
2. `MetaballsDream extends DreamService`: full-screen WebView, `WebViewAssetLoader`
   serving bundled `index.html`, `setWebContentsDebuggingEnabled(true)` in debug.
3. `res/xml/dream.xml` + manifest `<service>` with
   `android.permission.BIND_DREAM_SERVICE` and the `DREAM_SERVICE` intent filter;
   point its settings activity at a stub picker.
4. Gradle `Copy` task: root `index.html` → `assets/`.
5. Build, `adb install -r`, select under Settings → Screen saver, **validate on
   the phone** via chrome-devtools-mcp attached to the WebView (screenshot +
   console) and an `adb screencap` of the live Dream.

**Phase 2 — Live Wallpaper (native GLES metaballs).**
1. `MetaballsWallpaper extends WallpaperService`; GLES2 context on the engine
   Surface; paste `fsrc` as the fragment shader; port the JS motion loop to Kotlin.
2. `onVisibilityChanged` pause/resume; FPS cap; wallpaper settings activity.
3. `res/xml/wallpaper.xml` + manifest `<service>` with `BIND_WALLPAPER`.
4. Build, install, set as wallpaper, **validate on the phone** via `adb screencap`
   + `logcat`; compare against the web render for shader parity.

**Phase 3 — more screensavers + photo carousel.**
- Each new **web** animation = a new HTML asset; the Dream picker lists them and
  loads the chosen URL. The **photo carousel** is just another HTML page (bundled
  or pointed at a user photo folder via `MediaStore`) → screensaver for free.
- A new **wallpaper** animation needs a native GLES port (or is offered only as a
  Dream, not a wallpaper). Keep the picker honest about which surfaces each
  animation supports.

---

## 5. Open questions for later (not blocking)
- OK to run the one-time SDK component download (Phase 0) on this machine?
- Photo carousel source: bundled sample images, or read the phone's photo library
  (`MediaStore` + `READ_MEDIA_IMAGES` permission)?
- Keep GitHub Pages deployment alive alongside the app? (Assumed yes — it's free
  and shares the same `index.html`.)
