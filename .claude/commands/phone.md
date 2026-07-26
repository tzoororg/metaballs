---
description: Serve the local edit, tunnel it to the phone, open it in the phone's Chrome, and screenshot the render.
---

Get the current working-tree `evolve.html` running on the physical phone and report what it looks
like. Arguments (all optional): `$ARGUMENTS` may name a scene (`galaxy`, `tide`, …) and/or extra
query params like `mins=1` or `fps=30`.

Run this yourself, in order. Don't ask permission between steps.

1. **Serve** — check port 8791 first; reuse a live server rather than stacking another:
   ```bash
   curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8791/ || (cd "T:/programming/claude/ScreenSaver" && python -m http.server 8791 &)
   ```
2. **Tunnel** — `~/phone-connect.sh 8791`. It must print the phone model and a `/json/version`
   whose `Android-Package` is `com.android.chrome` (that's the *phone's* Chrome, not desktop). On
   `connection refused`, the phone locked or Chrome backgrounded — wake it and re-run.
3. **Open the page.** Quote the URL and disable Git Bash path mangling, or the device shell eats the
   `&` and you silently lose every param after the first:
   ```bash
   export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
   adb shell am start -a android.intent.action.VIEW -d "http://localhost:8791/evolve.html?dev=1&sweep=15" com.android.chrome
   ```
   Add `&scene=N` / `&mins=1` / `&fps=30&scale=0.5` per `$ARGUMENTS`. Scene indices: 0 Metaballs,
   1 Galaxy, 2 Tide, 3 Murmuration, 4 Silk, 5 Rain.
4. **Reload with devtools attached** (`navigate_page {type:"reload"}`) — otherwise parse-time errors
   are already gone by the time you read the console.
5. **Judge from `take_screenshot`.** Never from `gl.readPixels` — the canvas is
   `preserveDrawingBuffer:false` and reads all-zero between frames even when rendering perfectly.
   Use `evaluate_script` with `setP(x)` to sample specific points in the arc.
6. **Read the console** for GL errors, and grab the measured fps.

Report: the screenshot(s), fps, console errors, and a one-line verdict. If the render is black,
check the dead-script tells in CLAUDE.md (canvas stuck at 300×150, `CURRENT_PROGRAM === null`)
before you go hunting for a shader bug.

Leave the server running if more iteration is coming; kill it when the session's phone work is done
(PowerShell one-liner in CLAUDE.md — Git Bash has no `pkill`).
