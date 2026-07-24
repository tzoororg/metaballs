#!/usr/bin/env bash
# Run once per session after plugging the phone in (USB debugging authorized).
# Usage:
#   ./phone-connect.sh [DEV_PORT]     # default DEV_PORT=8791 (metaballs app)
#   ./phone-connect.sh --webview      # discover a native-app WebView socket instead
#
# After this: http://localhost:DEV_PORT on the phone hits this machine,
# and 127.0.0.1:9222 (or next free port) is the phone's Chrome DevTools socket.
set -euo pipefail

DEV_PORT="${1:-8791}"
CDP_PORT=9222

# adb is installed globally (User PATH + ANDROID_HOME). Prefer PATH; fall back to
# the standard SDK path in case this shell hasn't picked up the new PATH yet.
ADB="$(command -v adb || true)"
[ -x "$ADB" ] || ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
[ -x "$ADB" ] || ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
[ -x "$ADB" ] || { echo "FATAL: adb not found on PATH or in the SDK platform-tools dir."; exit 1; }

die() { echo "FATAL: $*" >&2; exit 1; }

# --- require exactly one authorized device ---
STATE="$("$ADB" get-state 2>/dev/null || true)"
if [ "$STATE" != "device" ]; then
  echo "adb devices:"; "$ADB" devices
  case "$("$ADB" devices | awk 'NR==2{print $2}')" in
    unauthorized) die "Phone is UNAUTHORIZED. Tap 'Allow USB debugging' on the phone, then re-run." ;;
    offline)      die "Phone is OFFLINE. Unplug/replug the cable, then re-run." ;;
    *)            die "No device in 'device' state. Plug in the phone with USB debugging enabled." ;;
  esac
fi
echo "Device: $("$ADB" shell getprop ro.product.model | tr -d '\r') (Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r'))"

# --- WebView discovery mode (for native apps later) ---
if [ "${1:-}" = "--webview" ]; then
  echo "Searching for WebView DevTools sockets on the device..."
  SOCKS="$("$ADB" shell cat /proc/net/unix | grep -o 'webview_devtools_remote[_0-9]*' | sort -u || true)"
  [ -n "$SOCKS" ] || die "No webview_devtools_remote socket found. Open the app's WebView screen first."
  echo "Found:"; echo "$SOCKS"
  SOCK="$(echo "$SOCKS" | head -1)"
  "$ADB" forward tcp:$CDP_PORT localabstract:"$SOCK"
  echo "Forwarded tcp:$CDP_PORT -> $SOCK"
  curl -s "http://127.0.0.1:$CDP_PORT/json/version" || die "WebView CDP not answering."
  echo; exit 0
fi

# --- keep screen awake ---
# ponytail: some MIUI builds hang on this shell call; time-box it, non-fatal.
if timeout 8 "$ADB" shell svc power stayon usb 2>/dev/null; then
  echo "Screen stay-on (USB): enabled"
else
  echo "Screen stay-on: SKIPPED (device hung/refused). Set Developer options > 'Stay awake' by hand if the screen keeps locking."
fi

# --- reverse-forward dev server ---
"$ADB" reverse --remove-all >/dev/null 2>&1 || true
"$ADB" reverse tcp:$DEV_PORT tcp:$DEV_PORT
echo "Reverse: phone localhost:$DEV_PORT -> this machine :$DEV_PORT"

# --- make sure nothing FOREIGN owns 9222 (desktop Chrome would hijack CDP) ---
# Drop any stale adb forward we left on 9222 first, so we don't flag our own tunnel.
"$ADB" forward --remove tcp:$CDP_PORT >/dev/null 2>&1 || true
# Windows: use netstat; a LISTENING owner remaining now is something else (e.g. desktop Chrome).
if netstat -ano 2>/dev/null | grep -E "[:.]$CDP_PORT[[:space:]]" | grep -qi LISTENING; then
  echo "WARNING: port $CDP_PORT is held by another process (likely desktop Chrome remote-debugging)."
  CDP_PORT=9223
  echo "         Using $CDP_PORT instead. NOTE: .mcp.json points at 9222 — either free 9222"
  echo "         and re-run, or update chrome-devtools-mcp --browser-url to :$CDP_PORT."
fi

# --- forward phone Chrome DevTools socket ---
"$ADB" forward tcp:$CDP_PORT localabstract:chrome_devtools_remote
echo "Forward: 127.0.0.1:$CDP_PORT -> phone chrome_devtools_remote"

# --- verify the tunnel is really the phone's Chrome ---
echo "--- /json/version ---"
if ! curl -s --max-time 5 "http://127.0.0.1:$CDP_PORT/json/version"; then
  die "CDP not answering. Open Chrome on the PHONE (needs at least one tab), then re-run."
fi
echo
echo "OK. Phone Chrome DevTools reachable at http://127.0.0.1:$CDP_PORT"
