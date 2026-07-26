# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Evolve** — an Android "away from your phone" app. You set a duration and pick a scene; the
animation starts near-empty and evolves toward completion over that time, so coming back to the
phone feels like arriving somewhere.

- **[evolve.html](evolve.html)** — the product. Single file, ~1800 lines, six scenes, no build, no
  dependencies. This is what you edit.
- **[android/](android)** — thin Kotlin WebView wrapper that packages the HTML as an APK
  (`org.tzoororg.metaballs`). Rarely needs touching.
- **[index.html](index.html)** — the original metaballs-only screensaver, kept for the GitHub Pages
  URL and as the DreamService payload. Legacy; new work goes in `evolve.html`.

Repo is `tzoororg/metaballs` (public; the name predates the pivot). Pages serves
https://tzoororg.github.io/metaballs/ and rebuilds ~1 min after a push to `master`.

## Architecture: everything is a function of `p`

One number drives the whole app. `praw = (now - startedAt) / targetMs`, set every frame from the
**wall clock** (not frame count, so a backgrounded WebView can't fall behind). `setP()`
([evolve.html:171](evolve.html:171)) is the single place it becomes three scalars:

| scalar | range | meaning |
|---|---|---|
| `p` | `min(praw,1)` | **growth** — every scene's structure is a pure function of this |
| `arrive` | 0..1, hits 1 exactly at `p=1` | the "arrived" look, ramped over the last `SETTLE` (10%) of the session. Completion is a *state you settle into*, never an event that fires — walk in at 100% or an hour later, same picture |
| `over` | 0..1 per extra period | slow additive drift so "just done" reads different from "very overdue", without anything dying or dimming |

**The invariant: scenes are pure functions of `(p, t)`.** No per-scene state machines, no phase
enums, no "has this fired yet" flags. `t` is the motion clock and may only affect motion, never
structure. This is what makes the scrubber trustworthy — if scrubbing to 0.6 doesn't reproduce what
a real session looks like at 60%, a scene has broken the rule.

Helpers built on it: `ease` (smoothstep), `late` (biased-late — most change in the final third),
and `stage(a,b)`, which remaps a sub-range of `p` to 0..1. `stage` is how a scene layers several
overlapping arcs instead of hard-cutting between phases.

### Scenes

Dispatched by index in `frame()` ([evolve.html:1374](evolve.html:1374)) and declared as tab buttons
(`data-scene`). Adding one means both places.

| # | scene | surface |
|---|---|---|
| 0 | Metaballs | WebGL — one fragment pass, `r²/dist²` field summed per pixel |
| 1 | Galaxy | WebGL — density-wave spiral |
| 2 | Tide | 2D canvas (the only one), with a sunrise arc |

Metaballs and Galaxy share the single GL context; Tide owns the 2D canvas. `setScene()` keeps the
two canvases mutually exclusive — don't add a second GL context.

Crystal was removed in `eb569c3` (it stopped evolving past ~60% and never recovered). Murmuration,
Silk, and Rain were mocked but are **not** in the tree.

### Query-string knobs (how you iterate fast)

```
?dev=1              scrubber + play sweep; double-tap toggles chrome (desktop iteration)
?sweep=15           sweep the whole arc in 15s instead of 60
?mins=1             1-minute session, for an end-to-end phone test
?scene=2            boot straight into a scene (0 Metaballs, 1 Galaxy, 2 Tide)
?fps=30&scale=0.5   phone thermal relief — quarter the fragment work
```

`setP(1.35)` in the console jumps to 35% overdue.

## Working rules

- **Commit at each verified checkpoint, not at the end of the session.** Sessions have died with
  finished, reviewed, unpushed work in the tree, forcing the next one to reverse-engineer whether it
  was good. A scene that renders correctly on the phone is a commit.
- **One scene per session.** Sessions that chase two scenes run out of context mid-flight.
- **Iterate on desktop, verify on the phone before the commit.** Intermediate aesthetic rounds don't
  each need the full phone loop — but nothing is pushed without one.
- **Never reintroduce state into a scene.** See the `(p, t)` invariant above.
- Design decisions live in this file and in `.claude/agents/` — never send an agent to read another
  session's transcript for context.

## Commands

```bash
python -m http.server 8791             # WebGL needs a real origin; file:// renders a stripped snapshot
~/phone-connect.sh 8791                # adb reverse tunnel + CDP forward to the phone's Chrome
cd android && ./gradlew installDebug   # copyIndexHtml pulls the root HTML into assets, then builds
adb shell am start -n org.tzoororg.metaballs/.MetaballsActivity
adb shell dumpsys thermalservice       # ship gate: Thermal Status must be 0
```

`/phone` and `/ship` wrap these — prefer them.

## Testing on the phone (REQUIRED before every push)

**Every change must be validated by the agent on the physical phone, not just reasoned about or
checked on desktop.** Desktop and mobile Chrome diverge in ways that render fine on one and break on
the other — e.g. a top-level `const chrome = …` is a *parse-time* `SyntaxError` on Android Chrome
(`window.chrome` is a non-configurable global there) but works on desktop, killing the whole script
before it can even show its own error overlay. Only the phone catches this class of bug.

Setup (adb, tunnels, the two MCP servers) lives in [PHONE-TESTING.md](PHONE-TESTING.md). The loop is
`/phone`: serve the **local** edit over the reverse tunnel — no deploy needed to iterate. Testing the
live URL requires pushing first; prefer localhost.

### Common mistakes (learned the hard way)

- **Judge the render from a screenshot, NOT from `gl.readPixels`.** The canvas is
  `preserveDrawingBuffer:false`, so reading pixels in a separate task returns all-zero
  (`[0,0,0,0]`) between frames even when it's rendering perfectly. `readPixels` all-zero is *not*
  proof of a black screen; the compositor screenshot is the truth.
- **Capture load-time errors by reloading WITH devtools attached.** `list_console_messages` is empty
  if you attach after load — a parse-time `SyntaxError` already came and went. `navigate_page
  {type:"reload"}` (optionally with an `initScript` error listener), then read the console.
- **Dead-script tells:** canvas stuck at **300×150** (the WebGL default — `resize()` never ran) and
  `CURRENT_PROGRAM === null` mean the script threw *before* rendering. With no error overlay,
  suspect a parse-time error, not a shader/GL problem.
- **Rule out GL red herrings with a probe context.** Compiling the exact shaders in a throwaway
  canvas on the phone (`getShaderInfoLog`, `MAX_FRAGMENT_UNIFORM_VECTORS`, `HIGH_FLOAT`) confirms
  whether it's really precision/uniforms vs. something upstream. On this phone (Mali-G57) highp and
  1024 uniform vectors are fine — it's rarely the shader.
- **Escape adb shell args in Git Bash:** prefix with `export MSYS_NO_PATHCONV=1
  MSYS2_ARG_CONV_EXCL='*'` and quote URLs — the device shell eats a bare `&`, so `?fps=30&scale=0.5`
  silently loses everything after the ampersand.
- **mobile-mcp often enumerates zero devices even when `adb devices` sees the phone.** Its process
  env is fixed at session start, so a mid-session fix won't take — fall back to `adb` +
  chrome-devtools-mcp, enough to open tabs, screenshot, and read state. There are two adb copies on
  this machine (WinGet on `PATH` vs. the SDK copy under `ANDROID_HOME`), possibly different versions.
- **The CDP tunnel drops when the phone locks or Chrome backgrounds** (`connection refused` / empty
  page list). Re-run `~/phone-connect.sh 8791`.
- **Kill your `http.server` when done — they pile up.** Git Bash has no `pkill`:
  ```powershell
  Get-CimInstance Win32_Process -Filter "Name='python.exe'" | ? { $_.CommandLine -like '*http.server*8791*' } | % { Stop-Process -Id $_.ProcessId -Force }
  ```

## Design intent

Scenes should be **beautiful at any `p`, awe-inspiring past 50%**, and read as one system. Growth
should be legible without being literal — a glance tells you roughly how far in you are, with no
progress bar. Full brief in [.claude/agents/scene-reviewer.md](.claude/agents/scene-reviewer.md).

**Thermal budget is a hard gate,** not a nice-to-have — an early uncapped render loop cooked the GPU
overnight (60°C idle, 41.6°C skin the next morning). Ship criteria after a sustained run:
`Thermal Status: 0`, GPU ≲55°C, skin ≲46°C. Use `?fps=30&scale=0.5` if a scene can't hold that at
full rate.
