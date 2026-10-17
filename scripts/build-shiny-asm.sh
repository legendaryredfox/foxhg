#!/usr/bin/env bash
# Assembles docs/asm/shiny_rates.s for the synthetic overlay and prints the bytes and entry points.
# Usage: scripts/build-shiny-asm.sh [code-address]   (default 0x023D9D20, the address the patch writes it to)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
code="${1:-0x023D9D20}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
arm-none-eabi-as -mthumb -march=armv5t -o "$tmp/s.o" "$here/docs/asm/shiny_rates.s"
arm-none-eabi-ld -Ttext="$code" -e wild_entry -o "$tmp/s.elf" "$tmp/s.o"
arm-none-eabi-objcopy -O binary "$tmp/s.elf" "$tmp/s.bin"
arm-none-eabi-nm "$tmp/s.elf" | grep -E ' (wild_entry|static_entry|party_entry)$'
echo "size: $(stat -c %s "$tmp/s.bin")"
xxd -p -c 32 "$tmp/s.bin"
