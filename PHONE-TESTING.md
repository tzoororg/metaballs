# Phone testing loop

Lets Claude drive a real Android phone to test PWAs / native apps — read the page's
DOM/console/network via Chrome DevTools Protocol, and drive taps/gestures/native UI.
Two MCP servers, installed **globally (user scope)**, so every project can use them.

## Global install (one-time, already done)

- **adb**: Google Platform Tools at `%LOCALAPPDATA%\Android\Sdk\platform-tools`, on the
  User `PATH`, with `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`. `adb` works in any terminal.
- **MCP servers** (user config `~/.claude.json`, available in all projects):
  - `chrome-devtools` → `npx -y chrome-devtools-mcp@latest --browser-url=http://127.0.0.1:9222`
  - `mobile` → `npx -y @mobilenext/mobile-mcp@latest` (env `ANDROID_HOME` set so it finds adb)
- **connect script**: `~/phone-connect.sh` (global) and a copy in this repo. Runs from any dir.

## One-time phone setup

- Settings → About phone → tap "Build number" 7× to unlock Developer options.
- Developer options → enable **USB debugging** (and, on this Xiaomi, **Stay awake**).
- First plug-in: accept **"Allow USB debugging?"** and tick "always allow from this computer".

## Per-session ritual (any project)

1. Plug the phone in via USB.
2. Start your dev server (this app: `python -m http.server 8791`; a vite PWA: `npm run dev`).
3. Run the connect script with your dev port:
   ```bash
   ~/phone-connect.sh 5173        # or 8791, or whatever port your PWA uses
   ```
   Fails loudly if the phone is missing/unauthorized. On success it prints the phone
   model + Chrome's `/json/version`.
4. **Open Chrome on the phone** (CDP needs ≥1 open tab) → `http://localhost:<port>`.
   The reverse-forward makes that reach this machine.
5. Ask Claude to test — the MCP tools attach automatically.

## Which tool for what

- **chrome-devtools-mcp** — *what the page is doing*: console errors, network requests,
  DOM state, service-worker/cache, JS eval, install prompts. Attaches to the phone's Chrome
  over the forwarded `127.0.0.1:9222` socket.
- **mobile-mcp** — *interacting*: tap, swipe, type, hardware keys (BACK/HOME/RECENT),
  launch/switch apps, screenshot, read the UI hierarchy. This is the one for native apps too.

## Gotchas hit during setup

- **This is Windows.** adb is `adb.exe`, the port-conflict check uses `netstat` (not `lsof`),
  and `.sh` runs under Git Bash.
- **MIUI hangs on `svc power stayon usb`** (this phone, 2201117SG). The script time-boxes and
  skips it — use Developer options → "Stay awake" if the screen locks mid-test.
- **`--browser-url` is load-bearing.** Without it, chrome-devtools-mcp launches its own
  *desktop* Chrome and everything looks fine while testing the wrong browser. If it won't
  attach, use `--ws-endpoint <webSocketDebuggerUrl>` from `/json/version`.
- **MCP tools register at session start.** Adding/changing servers needs a fresh Claude
  session before the tools are callable (a mid-session change won't expose them).
- **adb forwards can drop** if a second adb of a different version touches the server (it
  kills+restarts, clearing forwards) — symptom: chrome-devtools-mcp "connection refused".
  Fix: re-run `~/phone-connect.sh <port>`. Keep other adb copies (Android Studio, scrcpy)
  matched to 37.0.0 or off PATH.
- **9222 self-conflict:** the script clears its own stale forward before checking for a
  foreign owner, so re-runs stay on 9222 (matching the MCP config) instead of drifting to 9223.

## WebView variant (for native apps, later)

A native app's WebView exposes `webview_devtools_remote_<pid>`, not `chrome_devtools_remote`.
Open the app's WebView screen, then:
```bash
~/phone-connect.sh --webview      # discovers the socket, forwards it to 9222
```
chrome-devtools-mcp then attaches the same way.
