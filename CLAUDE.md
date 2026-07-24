# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A single-file WebGL metaballs animation, intended to become a phone screensaver. Currently a visuals-only mock. No build system, no dependencies — everything lives in `index.html`.

## Running & deploying

WebGL needs a real HTTP origin — `file://` will not run (the in-app browser preview renders `file://` as a static snapshot with scripts stripped). Always serve it:

```bash
python -m http.server 8791    # then open http://localhost:8791/
```

Deployed to GitHub Pages (public repo `tzoororg/metaballs`). Redeploy by pushing to `master`; Pages rebuilds in ~1 min:

```bash
git add -A && git commit -m "..." && git push
```

Live URL: https://tzoororg.github.io/metaballs/

## Testing on the phone (REQUIRED for every change)

**Every change must be validated by the agent on the physical phone, not just reasoned about or checked on desktop.** Desktop and mobile Chrome diverge in ways that render fine on one and break on the other — e.g. a top-level `const chrome = …` is a *parse-time* `SyntaxError` on Android Chrome (`window.chrome` is a non-configurable global there) but works on desktop, killing the whole script before it can even show its own error overlay. Only the phone catches this class of bug.

Setup (adb, tunnels, the two MCP servers) lives in `PHONE-TESTING.md` — read it. The per-change loop:

1. Serve the **local** edit and load it on the phone over the reverse tunnel — no deploy needed to test:
   ```bash
   python -m http.server 8791
   ~/phone-connect.sh 8791
   ```
   Testing the *live* URL instead requires pushing first; prefer localhost for iteration.
2. Ensure the phone's Chrome has a tab on the page. If it's on the launcher, open one via adb (mobile-mcp isn't required):
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "http://localhost:8791/" com.android.chrome
   ```
3. Drive it with **chrome-devtools-mcp**: `list_pages` → confirm the tab, `take_screenshot` to judge the render, `list_console_messages` for errors.

### Common mistakes (learned the hard way)

- **Judge the render from a `take_screenshot`, NOT from `gl.readPixels`.** The canvas is `preserveDrawingBuffer:false`, so reading pixels in a separate task returns all-zero (`[0,0,0,0]`) between frames even when it's rendering perfectly. `readPixels` all-zero is *not* proof of a black screen; the compositor screenshot is the truth.
- **Capture load-time errors by reloading WITH devtools attached.** `list_console_messages` is empty if you attach after load — a `SyntaxError` at parse time already came and went. `navigate_page {type:"reload"}` (optionally with an `initScript` error listener), then read the console.
- **Dead-script tells:** canvas stuck at **300×150** (the WebGL default — `resize()` never ran) and `CURRENT_PROGRAM === null` (`useProgram` never ran) mean the script threw *before* rendering. Combined with no error overlay, suspect a parse-time error, not a shader/GL problem.
- **Rule out GL red herrings with a probe context.** Compiling the exact shaders in a throwaway canvas on the phone (checking `getShaderInfoLog`, `MAX_FRAGMENT_UNIFORM_VECTORS`, `HIGH_FLOAT` precision) confirms whether it's really precision/uniforms/shader vs. something upstream. On this phone (Mali-G57) highp and 1024 uniform vectors are fine — it's rarely the shader.
- **mobile-mcp often enumerates zero devices even when `adb devices` sees the phone.** Its process env is fixed at session start, so a mid-session fix won't take — just fall back to `adb` + chrome-devtools-mcp, which is enough to open tabs, screenshot, and read state. Note there are two adb copies on this machine (WinGet on `PATH` vs. the SDK copy under `ANDROID_HOME`) and they can be different versions.
- **The CDP tunnel drops when the phone locks or Chrome backgrounds** (`connection refused` / empty page list). Re-run `~/phone-connect.sh 8791`.
- **Kill your `http.server` when done — they pile up.** Windows Git Bash has no `pkill`; instances from prior sessions accumulate on 8791. Clear them:
  ```powershell
  Get-CimInstance Win32_Process -Filter "Name='python.exe'" | ? { $_.CommandLine -like '*http.server*8791*' } | % { Stop-Process -Id $_.ProcessId -Force }
  ```

## Architecture

The metaball field is computed entirely on the GPU in one fragment-shader pass (`fsrc` in `index.html`) — this is what keeps it phone-friendly. JavaScript does almost nothing per frame: it only updates ball positions and uploads them as a uniform.

- **Field**: for each pixel, sum `r²/dist²` over all balls; `f > ~1` is inside the surface. `smoothstep` on `f` gives the soft/gooey edge. There is no per-ball geometry — the merged blobs are an emergent property of the summed field.
- **Styles**: `uStyle` (0–3) selects the palette *inside the shader* (neon / lava lamp / ink / oil slick). Switching styles is just a uniform change — the field math is shared. Neon glow (style 0) is the current default.
- **Coordinate spaces** (easy to trip on): ball positions are stored in `0..1`, but passed to the shader in **aspect-corrected space** (`x * aspect`, y unchanged) so blobs stay round. In-shader color gradients use `n` = the `0..1` space, not the aspect space.
- **Motion invariant**: velocities are **unit vectors** scaled by `SPEED`. Organic wander comes from *rotating* the velocity each frame (via `spin`); walls *reflect* a component. Both operations preserve magnitude, so speed is constant and wall bounces are perfectly elastic — do not reintroduce additive positional wobble (an earlier version did, and it fought the wall bounce, making balls appear to stall at edges).

## Tuning knobs (top of the `<script>`)

- `COUNT` — number of balls. `MAX` is the shader's uniform-array size and **must be ≥ COUNT** (it's interpolated into the shader source as `uBalls[${MAX}]`).
- `SPEED` — drift speed. Ball radii and the count of "big" balls are set in the init loop (`i < 2` are large).
- Screensaver behaviors: the control chrome auto-hides after ~3.5s idle; `prefers-reduced-motion` freezes `uTime` (animation stops but the field still renders).

## Current design intent

Per the user: **neon glow** palette, medium mix (~10 balls, 2 large + 8 small), slow-but-visibly-moving pace with clearly visible merge/split action. The other three palettes are kept selectable for comparison.
