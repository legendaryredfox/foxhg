#!/usr/bin/env bash
# Live click test: runs the real app under Xvfb and drives it with real X mouse and keyboard events (xdotool), the way a
# person would. The Compose UI tests in app/src/jvmTest go through the semantics tree; this goes through the window.
#
# Flow: open the ROM, edit an item's price in the Items screen, save as a project through the dialog, edit again and let
# autosave write it, quit, reopen the project and check the edits are there. Checks use the window title, files on
# disk and pixel comparisons of the price field; screenshots of every step are kept.
#
# Usage: scripts/live-click-test.sh [rom.nds] [out-dir]
# Needs: xvfb-run, xdotool, ImageMagick (import, convert, compare), the Gradle wrapper. Exit code 0 = every check passed.
# The coordinates below are for the 1180x760 window and the current layout; if the UI moves, look at the screenshots.
# The app runs with a temporary user.home, so no project or recents file is written outside the output directory.
set -uo pipefail

cd "$(dirname "$0")/.."
rom="$(realpath "${1:-roms/heartgold.nds}")"
out="$(realpath -m "${2:-build/live-click}")"
[ -e "$rom" ] || { echo "SKIPPED: no ROM at $rom"; exit 0; }
rm -rf "$out"; mkdir -p "$out/home"
export LIVE_ROM="$rom" LIVE_OUT="$out"

# Java's GTK look and feel throws an NPE under a bare Xvfb when it thinks it is on GNOME without a shell.
env -u XDG_CURRENT_DESKTOP -u DESKTOP_SESSION -u GNOME_DESKTOP_SESSION_ID \
xvfb-run -a -s "-screen 0 1280x800x24" bash -c '
  set -uo pipefail
  fails=0
  check() { if [ "$1" = ok ]; then echo "  ok    $2"; else echo "  FAIL  $2"; fails=$((fails + 1)); fi; }
  rom_stat() { stat -c "%s %Y" "$LIVE_ROM"; }
  before=$(rom_stat)

  launch() {   # launch <args...>: starts the app and waits for its window
    FOXHG_JVM_ARGS="-Duser.home=$LIVE_OUT/home" XDG_CONFIG_HOME="$LIVE_OUT/home/.config" \
      setsid timeout 300 ./gradlew :app:run --quiet --console=plain --args="$*" >"$LIVE_OUT/app.log" 2>&1 &
    app_pid=$!
    win=""
    for _ in $(seq 1 90); do
      win=$(xdotool search --name "^foxhg" 2>/dev/null | head -1 || true)
      [ -n "$win" ] && break
      sleep 2
    done
    [ -n "$win" ] || { echo "window never appeared (see $LIVE_OUT/app.log)"; exit 1; }
    # the window exists before the ROM has loaded; wait until it shows more than a blank background
    for _ in $(seq 1 60); do
      import -window "$win" "$LIVE_OUT/probe.png" 2>/dev/null
      [ "$(convert "$LIVE_OUT/probe.png" -format %k info: 2>/dev/null || echo 0)" -gt 100 ] && break
      sleep 1
    done
    rm -f "$LIVE_OUT/probe.png"; sleep 2
    xdotool windowfocus "$win"; sleep 1
  }
  quit() {
    kill -- -"$app_pid" 2>/dev/null; pkill -f "io.github.legendaryredfox.foxhg.app.Main[K]t" 2>/dev/null
    for _ in $(seq 1 20); do xdotool search --name "^foxhg" >/dev/null 2>&1 || break; sleep 1; done
  }
  click() { xdotool mousemove --window "$win" "$1" "$2" click 1; sleep 0.7; }
  typeover() { click "$1" "$2"; xdotool key ctrl+a; xdotool type --delay 40 "$3"; sleep 1; }
  shot() { import -window "$win" "$LIVE_OUT/$1.png"; }
  crop() { convert "$LIVE_OUT/$1.png" -crop "$5x$6+$3+$4" +repage "$LIVE_OUT/$2.png"; }
  same() { [ "$(compare -metric AE "$LIVE_OUT/$1.png" "$LIVE_OUT/$2.png" null: 2>&1 || true)" = 0 ]; }
  title() { xdotool getwindowname "$win"; }

  PRICE="557 195 96 34"          # the Price field of the Items screen: x y width height
  ITEMS="60 368"; POKEBALL="360 250"; PRICE_FIELD="605 212"

  echo "session 1: bare ROM"
  launch "$LIVE_ROM"
  shot s1-start
  [ "$(title)" = "foxhg" ] && check ok "title is plain before any edit" || check no "title is plain before any edit ($(title))"
  click $ITEMS; click $POKEBALL
  shot s1-items; crop s1-items price-vanilla $PRICE
  typeover $PRICE_FIELD 321
  shot s1-edited; crop s1-edited price-321 $PRICE
  same price-vanilla price-321 && check no "typing changed the price field" || check ok "typing changed the price field"
  case "$(title)" in *"•") check ok "title shows unsaved work";; *) check no "title shows unsaved work ($(title))";; esac

  echo "save as project"
  xdotool key ctrl+s; sleep 2; shot s1-dialog
  typeover 470 335 live-hack          # Name field of the Save as project dialog
  shot s1-named
  click 800 520                        # Save project
  sleep 2; shot s1-saved
  proj="$LIVE_OUT/home/foxhg-projects/live-hack"
  [ -f "$proj/project.properties" ] && check ok "project folder created" || check no "project folder created ($proj)"
  case "$(title)" in *live-hack*) check ok "title names the project ($(title))";; *) check no "title names the project ($(title))";; esac

  echo "autosave"
  click $POKEBALL
  typeover $PRICE_FIELD 654
  sleep 3; shot s1-autosaved; crop s1-autosaved price-654 $PRICE
  case "$(title)" in *"•") check no "autosave cleared the unsaved mark ($(title))";; *) check ok "autosave cleared the unsaved mark";; esac
  quit

  echo "session 2: reopen the project"
  launch "$proj" --editor=ITEMS
  click $POKEBALL
  shot s2-items; crop s2-items price-reopened $PRICE
  same price-654 price-reopened && check ok "reopened project shows the autosaved price" || check no "reopened project shows the autosaved price"
  same price-321 price-reopened && check no "reopened price is not the older edit" || check ok "reopened price is not the older edit"
  quit

  [ "$(rom_stat)" = "$before" ] && check ok "the ROM file was not touched" || check no "the ROM file was not touched"
  echo; [ "$fails" = 0 ] && echo "live click test: all checks passed" || echo "live click test: $fails check(s) failed; screenshots in $LIVE_OUT"
  exit "$fails"
'
