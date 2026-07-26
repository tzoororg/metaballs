---
description: Full ship gate — build the APK, install to the phone, run a real session, check thermals, then commit and push.
---

Ship the current working tree. `$ARGUMENTS` may carry a commit message and/or a scene to exercise.

Every gate below must pass before the push. If one fails, stop and report — do not push a failing
gate and do not "fix it quickly" without telling me.

1. **Show me what's shipping** — `git status --short` and `git diff --stat`. If the tree contains
   changes you didn't make this session, ask before including them.
2. **Build + install:**
   ```bash
   cd android && ./gradlew installDebug
   ```
   The `copyIndexHtml` Gradle task copies the root HTML into `assets/` on every build, so the APK
   always carries the current working tree. If install fails with `INSTALL_FAILED_USER_RESTRICTED`,
   "Install via USB" is off in MIUI Developer options — that needs my hands.
3. **Launch and run a real session:**
   ```bash
   adb shell am start -n org.tzoororg.metaballs/.MetaballsActivity
   ```
   Pick 1 min, pick the scene, hit Start. Let it run the full minute — this is the end-to-end check
   that the wall-clock `p` actually advances in the WebView, which desktop cannot prove.
4. **Screenshot the final frame** and confirm it's the arrived look, not a stall or a black screen.
5. **Thermal gate** — `adb shell dumpsys thermalservice`, parse the block after
   `Current temperatures from HAL:`. Required: `Thermal Status: 0`, GPU ≲55°C, skin ≲46°C. This is a
   hard gate; an uncapped loop once cooked the phone overnight. Report the actual numbers, never
   "looks fine".
6. **Commit and push** to `master`. Pages rebuilds in ~1 min.
7. **Clean up** the `http.server` instances on 8791 (PowerShell one-liner in CLAUDE.md).

Report the numbers from steps 3–5 plainly, with the commit hash. If any gate failed, say which and
leave the tree uncommitted.
