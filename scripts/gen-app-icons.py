#!/usr/bin/env python3
"""Render the app icon at the sizes the window and top bar need.

Usage: python3 -I scripts/gen-app-icons.py
Scaling one big PNG down at run time looks blurry (Compose uses bilinear filtering without mipmaps), so every size is
pre-rendered here with Lanczos from app/icons/foxhg-512.png into app/src/jvmMain/resources/icons/icon-<size>.png.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent
src = Image.open(root / "app/icons/foxhg-512.png").convert("RGBA")
out = root / "app/src/jvmMain/resources/icons"
out.mkdir(parents=True, exist_ok=True)
for size in (16, 24, 32, 48, 64, 96, 128, 256):
    src.resize((size, size), Image.LANCZOS).save(out / f"icon-{size}.png", optimize=True)
print("done")
