#!/usr/bin/env bash
# Launch foxhg under a virtual display, wait for the window, and save a screenshot of it.
# Usage: scripts/screenshot.sh <out.png> [rom.nds] [editor] [settle-seconds=6] [xdotool-args...]
# Example: scripts/screenshot.sh /tmp/trainers.png roms/heartgold.nds TRAINERS
# The window is polled for up to 3 minutes (the first run compiles), then the UI gets `settle` seconds to load the ROM.
# Extra arguments are run as one `xdotool` command line once settled (clicks, keys), followed by a 2 s pause.
# FOXHG_THEME=dark|light forces a theme.
set -euo pipefail

out="$(realpath -m "$1")"; rom="${2:-}"; editor="${3:-}"; settle="${4:-6}"; shift $(($# < 4 ? $# : 4))
cd "$(dirname "$0")/.."
args=()
[ -n "$rom" ] && args+=("$PWD/$rom")
[ -n "$editor" ] && args+=("--editor=$editor")

xvfb-run -a -s "-screen 0 1280x800x24" bash -c '
  out="$1"; settle="$2"; nargs="$3"; shift 3
  app=("${@:1:nargs}"); xd=("${@:nargs+1}")
  # Gradle rejects an empty --args, so only pass it when there is something to pass.
  run=(./gradlew :app:run --quiet --console=plain)
  [ ${#app[@]} -gt 0 ] && run+=(--args="${app[*]}")
  timeout 300 "${run[@]}" >/dev/null 2>&1 &
  win=""
  for _ in $(seq 1 90); do
    win=$(xdotool search --name "^foxhg$" 2>/dev/null | head -1 || true)
    [ -n "$win" ] && break
    sleep 2
  done
  [ -z "$win" ] && { echo "window never appeared" >&2; exit 1; }
  sleep "$settle"
  if [ ${#xd[@]} -gt 0 ]; then xdotool "${xd[@]}"; sleep 2; fi
  import -window "$win" "$out"
  exit 0
' _ "$out" "$settle" "${#args[@]}" "${args[@]}" "$@"
test -s "$out" && echo "saved $out"
