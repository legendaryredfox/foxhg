#!/usr/bin/env bash
# Shallow-clone the reference projects into references/ (gitignored).
# Check `df -h ~` first: pokeheartgold is large.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p references

clone() {
  local name="$1" url="$2"
  if [ -d "references/$name/.git" ]; then
    echo "skip $name (already cloned)"
  else
    git clone --depth 1 "$url" "references/$name"
  fi
}

clone DSPRE https://github.com/DS-Pokemon-Rom-Editor/DSPRE.git
clone scrcmd-database https://github.com/DS-Pokemon-Rom-Editor/scrcmd-database.git
clone VSMaker2 https://github.com/Chvlkie/VSMaker2.git
clone Pokemon-DS-Map-Studio https://github.com/Trifindo/Pokemon-DS-Map-Studio.git
clone pokeheartgold https://github.com/pret/pokeheartgold.git
clone ds-pokemon-hacking-docs https://github.com/ds-pokemon-hacking/ds-pokemon-hacking.github.io.git
clone ndspy https://github.com/RoadrunnerWMC/ndspy.git
clone ndstool https://github.com/devkitPro/ndstool.git
clone hg-engine https://github.com/BluRosie/hg-engine.git

# Kotlin / JVM NDS code (closest to what foxhg builds)
clone kotlinds https://github.com/kotlinds/kotlinds.git
clone pokemon-map-randomizer https://github.com/kotlinds/pokemon-map-randomizer.git
clone pokemon-client https://github.com/kotlinds/pokemon-client.git
clone libretro-kmp https://github.com/kotlinds/libretro-kmp.git
clone Nds4j https://github.com/turtleisaac/Nds4j.git

# Other Gen 4 editors and NARC tooling
clone Pokemon_Sinjoh_Editor https://github.com/GreyLily3/Pokemon_Sinjoh_Editor.git
clone pokewarpdump https://gitlab.com/Lino5000/pokewarpdump.git
