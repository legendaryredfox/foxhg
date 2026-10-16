# foxhg

## Project Overview

**foxhg** is a clone of [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) (DS Pokémon ROM Editor Reloaded)
rebuilt as a Kotlin Multiplatform app. DSPRE is a C# WinForms app on .NET Framework 4.8 that only runs on Windows
(or under wine). foxhg keeps the same job, editing Nintendo DS Pokémon ROMs, but runs natively on Linux, Windows and
macOS, and keeps the format code separate from the UI so it can be tested without a window.

Current focus: **Pokémon HeartGold (English)**. SoulSilver comes for free where the layout is identical. The long-term
goal is every DS Pokémon ROM (other HGSS languages, Platinum, Diamond, Pearl, later Gen V), then GB and GBA; all of that
is out of scope until HGSS English is solid, but write code so a second game is a new profile and layout, not a rewrite
(docs/spec.md 3.3 and 9, docs/plan.md M8).

A **save editor** for the games' `.sav` files is in scope too (requested 2026-10-16, docs/spec.md section 11 and docs/plan.md M10): it
is a second kind of file next to the ROM, never mixed with ROM data, and saves are never committed.

The ROM data model and the editors are ported from DSPRE; the file formats are documented by the
[DS Pokémon Hacking wiki](https://ds-pokemon-hacking.github.io/) and the
[pret/pokeheartgold](https://github.com/pret/pokeheartgold) decomp. Spec and plan:
[docs/spec.md](./docs/spec.md) and [docs/plan.md](./docs/plan.md).

## Tech Stack

| Category | Technology | Version / Notes |
|----------|------------|-----------------|
| Language | Kotlin Multiplatform | 2.4.20 |
| UI | Compose Multiplatform (desktop) | 1.12.1 |
| JDK | Azul Zulu 26 | `26.0.1-zulu` via sdkman (`.sdkmanrc`); always build and test with it |
| Build | Gradle (wrapper), version catalog | `gradle/libs.versions.toml` |
| Async / data | kotlinx-coroutines, kotlinx-serialization | project files and settings as JSON |
| Tests | `kotlin.test`, Compose UI test | UI tests run under `xvfb-run` |
| Test ROM | clean English HeartGold, `roms/heartgold.nds` | gitignored (symlink), never commit it |

## Setup Commands

| Command | Description |
|---------|-------------|
| `sdk env` | select Zulu 26 from `.sdkmanrc` (Zulu 26 is also the sdkman default, and `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to it) |
| `./gradlew :core:jvmTest` | format and model tests (no UI) |
| `./gradlew :app:run` | launch the desktop app |
| `./gradlew build` | compile and test everything; the UI tests need a display: `env -u XDG_CURRENT_DESKTOP -u DESKTOP_SESSION -u GNOME_DESKTOP_SESSION_ID xvfb-run -a ./gradlew build` |
| `FOXHG_REQUIRE_ROM=1` | make the ROM-based tests fail instead of skipping when `roms/heartgold.nds` is missing (they skip with a `SKIPPED` notice by default, so a fresh clone or CI builds without the ROM) |
| `./gradlew :app:packageDeb` | native Linux package |
| `scripts/setup-references.sh` | clone the reference projects into `references/` (shallow) |
| Settings file | `~/.config/foxhg/settings.properties` (theme, emulator command for Run, autosave); recents in `recent.txt` beside it. Tests give every model its own temporary settings file (`testSettings()`), never the real one |
| `scripts/screenshot.sh out.png [rom.nds] [editor]` | run the app under xvfb and screenshot it (clears the GNOME variables that crash Java's GTK look and feel under a bare Xvfb) |
| `scripts/live-click-test.sh [rom.nds] [out-dir]` | live click test: runs the real app under xvfb and drives it with real X mouse and keyboard events (xdotool): edit an item, save as a project, autosave, quit, reopen. Screenshots of each step go to `build/live-click/`; exit code 0 = all checks passed; skips without the ROM |
| `./gradlew :core:emulatorTest` | tests that run ROMs on the DeSmuME libretro core (`libretro-kmp`, no display needed; the core is `/usr/lib/x86_64-linux-gnu/libretro/desmume_libretro.so` or `DESMUME_CORE`; they skip with a notice without it). `emulatorSetup` plays the new game once to make the saved games in `core/build/emulator-saves` (about 3 minutes the first time). Pictures of what the game showed go to `core/build/emulator-shots`. `./gradlew build` runs them too |
| `scripts/boot-test.sh rom.nds out.png [frames]` | boot a ROM headless in RetroArch + DeSmuME and save a screenshot; compare with the vanilla frame (`BootFixtures` writes test ROMs when `FOXHG_BOOT_DIR` is set) |

## Layout

- `core/`: KMP library, **no UI**. NDS container, NARC, overlays, text, and one package per ROM data type. Code goes in `commonMain`; only file and OS access goes in `jvmMain`.
- `app/`: Compose Multiplatform desktop app. One screen per editor, all state in view models that depend only on `core`.
- `docs/`: [spec.md](./docs/spec.md), [plan.md](./docs/plan.md), notes on formats found while porting.
- `references/`: shallow clones of other projects (gitignored, recreate with `scripts/setup-references.sh`). `references/DSPRE` is the main source to port from.
- `roms/`: commercial ROMs (gitignored). `roms/heartgold.nds` links to the clean ROM.

## Before porting or fixing a format (mandatory)

Look at the reference projects first, in this order:

1. `references/DSPRE/DS_Map/ROMFiles/` has one class per file type (`PokemonPersonalData.cs`, `LearnsetData.cs`, `TrainerFile.cs`, `EncounterFile.cs`, ...). `DS_Map/RomInfo.cs` has the per-game NARC paths and offsets. `DS_Map/Narc.cs` and `DS_Map/DSUtils/` have NARC, overlay and text handling.
2. The [DS Pokémon Hacking wiki](https://ds-pokemon-hacking.github.io/) for the format description.
3. `references/pokeheartgold` (pret decomp) for struct layouts and constants.
4. Only then, your own hypothesis. Verify it against the real ROM.

Port the **behavior**, not the WinForms structure. Do not translate designer files or event handlers one-to-one.
Record every address, offset or quirk you confirm in `docs/formats.md`.

## Acceptance Criteria

- every commit is authored **and committed** by `legendaryredfox <legendaryredfox.dev@gmail.com>` (set in this repo's local git config; check `git config user.email` before committing and never use the global identity here). The GitHub accounts are `legendaryredfox` (this project) and `romulofer`; push as `legendaryredfox`. The repo is public on github.com/legendaryredfox/foxhg and codeberg.org/legendaryredfox/foxhg (the Codeberg token is in `~/.codeberg_token`: pass it with an `http.extraheader`, never put it in a remote URL)
- **no em dashes** (the long dash, U+2014) anywhere in the project: code, comments, strings, docs, README, commit messages. Use a regular dash ( - ) or reword. `git grep -nP '\x{2014}'` must print nothing
- the user is the sole author of all commits (github.com/legendaryredfox). **Do not** add `Co-Authored-By` or any Claude attribution line to commits or PRs in this project
- every format in `core` has a round-trip test: `parse(bytes)` then `serialize()` must reproduce the original bytes for every entry of the real HeartGold ROM
- a change is done only when `./gradlew build` passes; anything not built or run must be stated explicitly, never described as done
- an editor must not write to the ROM unless the user asked to save; saving must keep a backup of the original file
- never commit `*.nds`, `*.sav`, `roms/`, `references/`, or any commercial ROM, save file or extracted ROM data

## Do

- read DSPRE's matching class and the wiki page before writing a parser
- keep `core` free of Compose, AWT and `java.*` outside `jvmMain`
- write the test (round trip against the ROM) together with the parser
- follow the style of the surrounding code; one class per file type; immutable data classes for parsed records
- mark any step that was not built or run as **[UNVERIFIED]**
- keep `docs/plan.md` ticked as work lands and `docs/formats.md` updated with what you confirm

## Do Not

- do not run on a different JDK; Gradle toolchain is pinned to 26
- do not depend on DSPRE's external `.exe` tools (`ndstool`, `dsrom`, `chatot`, `blz`, `apicula`); everything is pure Kotlin, or a Linux-native tool called through one adapter in `jvmMain`
- do not copy code from the reference projects (DSPRE is AGPL-3.0, ndspy and Nds4j are GPL-3.0), except where the user decided otherwise: the ROM toolbox patches (ARM machine code and patch tables) are ported from DSPRE under AGPL-3.0 (decided 2026-10-15). Those files live in `core/.../patch/dspre/`, start with the AGPL header (`SPDX-License-Identifier: AGPL-3.0-only`, copyright of the DSPRE contributors, source link) and are listed in `THIRD_PARTY_NOTICES.md` and the README license section. Everything else is written from the format description (see how `Blz.kt` and `TextArchive.kt` were done). foxhg as a whole is GPL-3.0-or-later and, because of those files, can no longer be relicensed to a permissive license without removing them (see `LICENSE` and the README)
- do not reference `references/` paths from shipped files (README, build files); only from `CLAUDE.md` and `docs/`
- do not `git push`, open a PR, or switch the `gh` account without an explicit request; commit only when asked

## Disk and Build Hygiene

- the disk sits near 91% (`df -h ~`). Check before downloading a toolchain or cloning something large.
- `build/` and `.gradle/` regenerate; delete them when they pass ~1 GB (`du -sh build .gradle`). `./gradlew clean` first if the build misbehaves.
- unpacked ROM trees and other temporary output go in the session scratchpad, never inside the repository.

## Reference Documentation

- [docs/spec.md](./docs/spec.md), [docs/plan.md](./docs/plan.md)
- [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE), [DS Pokémon Hacking wiki](https://ds-pokemon-hacking.github.io/), [pret/pokeheartgold](https://github.com/pret/pokeheartgold)
- [Pokémon DS Map Studio](https://github.com/Trifindo/Pokemon-DS-Map-Studio) (map models), [VSMaker2](https://github.com/Chvlkie/VSMaker2) (trainer editing), [scrcmd-database](https://github.com/DS-Pokemon-Rom-Editor/scrcmd-database) (script commands)
- Kotlin and Compose conventions: `~/development/cross_platform_apps/foxtimer_kt` (version catalog, Compose Desktop packaging). Doc and guard-rail style: `~/development/foxdecompiles/CLAUDE.md`
