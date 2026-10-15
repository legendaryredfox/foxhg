#!/usr/bin/env bash
# Boot a ROM headless in RetroArch (DeSmuME core) for N frames and save a screenshot.
# Usage: scripts/boot-test.sh <rom.nds> <out.png> [frames=2400]
# Needs: retroarch, the desmume libretro core, xvfb-run. Work files go to a temp dir that is removed afterwards.
set -euo pipefail

rom="$1"; out="$2"; frames="${3:-2400}"
core="${DESMUME_CORE:-/usr/lib/x86_64-linux-gnu/libretro/desmume_libretro.so}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cat > "$work/ra.cfg" <<CFG
video_driver = "gl"
audio_driver = "null"
audio_enable = "false"
video_fullscreen = "false"
savefile_directory = "$work"
savestate_directory = "$work"
system_directory = "$work"
config_save_on_exit = "false"
pause_nonactive = "false"
CFG

timeout 300 xvfb-run -a -s "-screen 0 800x600x24" retroarch --config "$work/ra.cfg" -L "$core" "$rom" \
  --max-frames="$frames" --max-frames-ss --max-frames-ss-path="$out" >"$work/log.txt" 2>&1 || {
  echo "retroarch failed:"; tail -20 "$work/log.txt"; exit 1; }
test -s "$out" && echo "ok: $out ($(stat -c %s "$out") bytes)"
