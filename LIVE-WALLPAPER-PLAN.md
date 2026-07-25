# Plan — Metaballs as a Live Wallpaper (native GLES2)

Why: MIUI won't let a third-party `DreamService` actually start (needs the
`appop 10021` grant, and even then it's fragile). A **live wallpaper** is the
only third-party surface Android gives you that shows on **both the home screen
and the lock screen**, with no vendor workaround. There is no separate
"lock screen app" slot — the lock screen wallpaper *is* this.

Shipped meanwhile: `MetaballsActivity` (launcher icon → fullscreen WebView).
That stays; it's the manual-launch path and costs nothing.

## Why not WebView

`WallpaperService.Engine` hands you a raw `SurfaceHolder`. A `WebView` cannot
draw into it — it needs a `View` hierarchy in a window. So the wallpaper is a
second renderer of the same field: **native EGL + GLES2**.

Cost is bounded because **`fsrc` is already GLSL ES 1.00** — it pastes in
verbatim. Only the ~40 lines of JS motion loop become Kotlin.

## Scope

One new file, `MetaballsWallpaper.kt` (~250 lines), plus `res/xml/wallpaper.xml`
and a manifest `<service>`. No new dependencies — EGL14 and GLES20 are in the
framework.

### 1. EGL boilerplate (~90 lines, the only real "new" code)

`GLSurfaceView` is unusable here (it's a View). Do it by hand on the engine's
surface, on a dedicated `HandlerThread`:

- `eglGetDisplay(EGL_DEFAULT_DISPLAY)` → `eglInitialize` → `eglChooseConfig`
  (`RENDERABLE_TYPE = EGL_OPENGL_ES2_BIT`, RGB565 or RGB888, no depth/stencil —
  the shader is a single fullscreen quad).
- `eglCreateWindowSurface(display, config, surfaceHolder, null)`.
- `eglCreateContext` with `EGL_CONTEXT_CLIENT_VERSION = 2`.
- Recreate the window surface on `onSurfaceChanged`; destroy context+surface in
  `onSurfaceDestroyed` and `onDestroy`. Leaking an EGL context across a
  wallpaper restart is the classic crash here.

### 2. Shader + geometry (mechanical)

- Copy `vsrc`/`fsrc` out of `index.html` **unmodified** into a Kotlin raw string,
  with `${MAX}` substituted at build time the same way JS does it (string
  interpolation → `"uniform vec3 uBalls[$MAX];"`). Same `MAX = 12`, `COUNT = 10`.
- One `ARRAY_BUFFER` with the 4-vertex triangle-strip quad; `pos` attribute.
- Uniform handles: `uRes`, `uTime`, `uStyle`, `uBalls`, `uCount` — identical set.
- Check `getShaderInfoLog` on failure and `Log.e` it; there's no error overlay
  on a wallpaper surface, logcat is the only channel.

### 3. Motion loop port (mechanical, but preserve the invariants)

Port the `for(let i=0;i<COUNT;i++)` block 1:1. **Do not "improve" it** — from
CLAUDE.md: velocities are unit vectors scaled by `SPEED`, organic wander comes
from *rotating* the velocity (`spin`), walls *reflect* a component. Both
preserve magnitude. No additive positional wobble.

Positions live in `0..1`; upload as aspect-corrected (`x * aspect`, y as-is)
into a `FloatArray(MAX*3)` → `glUniform3fv`. Same coordinate-space split as the
web version, and the same place to get it wrong.

Drive frames with a `Handler.postDelayed` at the FPS cap (below), not
`Choreographer` — the wallpaper shouldn't run at display rate.

### 4. Battery / thermals — the actual footgun

The wallpaper renders *always*, unlike the Dream which only ran while charging.
Non-negotiable:

- **`onVisibilityChanged(false)` stops the frame loop** (remove pending
  callbacks); `true` resumes. Wallpaper hidden behind an app = zero GPU.
- **FPS cap 30** and **half-resolution** rendering, matching the `?fps=30&scale=0.5`
  the Dream already uses (uncapped full-res measured 60 C idle / thermal
  throttle on this phone). Render into the surface at half size by scaling the
  EGL window surface via `surfaceHolder.setFixedSize(w/2, h/2)` — one line,
  the compositor upscales for free.
- Ignore `onOffsetsChanged` (home-screen paging parallax). Redrawing on swipe is
  pure cost for a field that has no parallax to give.

### 5. Manifest + descriptor

```xml
<service android:name=".MetaballsWallpaper"
         android:exported="true"
         android:permission="android.permission.BIND_WALLPAPER">
    <intent-filter>
        <action android:name="android.service.wallpaper.WallpaperService" />
    </intent-filter>
    <meta-data android:name="android.service.wallpaper"
               android:resource="@xml/wallpaper" />
</service>
```
`res/xml/wallpaper.xml`: `<wallpaper android:thumbnail=… android:description=… />`.
Settings activity (style picker): skip for v1 — hardcode neon glow, the current
design intent. Add `android:settingsActivity` when a second palette is actually
wanted.

### 6. Validation on the phone (required, per CLAUDE.md)

1. `./gradlew installDebug`
2. Set it without hunting menus:
   `adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER \
    -e android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT \
    org.tzoororg.metaballs/.MetaballsWallpaper` — then confirm in the picker.
3. `adb exec-out screencap -p > shot.png` on the **home screen** and on the
   **lock screen** (`adb shell input keyevent 26` to lock). Judge from the
   screenshot, never from `readPixels`.
4. `adb logcat -s MetaballsWallpaper:* GLConsumer:* SurfaceFlinger:E` for EGL /
   shader errors — logcat replaces the console here.
5. Battery check: leave it on the home screen ~15 min, `adb shell dumpsys
   batterystats | grep metaballs`, and confirm the loop is idle when an app is
   foregrounded (log a counter in `onVisibilityChanged`).

## Estimate & order

| Step | Effort |
|---|---|
| EGL boilerplate + blank-clear wallpaper renders | ~1 session, most of the risk |
| Paste shader + quad, static field | small |
| Port motion loop | small |
| Visibility pause / FPS cap / half-res | small, do not defer |
| Phone validation incl. lock screen + battery | ~1 session |

Ship the EGL shell clearing to a solid color **first** and verify it appears on
the lock screen before touching the shader. If that doesn't work on MIUI,
nothing after it matters.

## Skipped deliberately

- Style picker settings activity — add when a second palette is wanted.
- Parallax on home-screen paging — no visual gain, constant cost.
- Sharing shader source between web and native at build time (a Gradle task
  extracting `fsrc` from `index.html`) — two copies of a stable shader is
  cheaper than the extraction machinery. Revisit if the shader starts churning.
