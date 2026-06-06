#!/usr/bin/env python3
"""Inline the platform CSS + shared toggle JS into each mockup HTML so the files
open standalone in any browser (no sibling-file loading, which flatpak browsers block).

Idempotent: re-run after editing pc.css / android.css / theme-toggle.js to refresh
the inlined copies. Edit the source .css/.js, then run:  python3 inline.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JS = (ROOT / "theme-toggle.js").read_text(encoding="utf-8")
CSS = {
    "PC": (ROOT / "PC" / "pc.css").read_text(encoding="utf-8"),
    "Android": (ROOT / "Android" / "android.css").read_text(encoding="utf-8"),
}

LINK_RE = {
    "PC": re.compile(r'<link rel="stylesheet" href="pc\.css">'),
    "Android": re.compile(r'<link rel="stylesheet" href="android\.css">'),
}
STYLE_RE = re.compile(r'<style data-inlined="css">.*?</style>', re.DOTALL)
SCRIPT_LINK_RE = re.compile(r'<script src="\.\./theme-toggle\.js"></script>')
SCRIPT_INLINED_RE = re.compile(r'<script data-inlined="toggle">.*?</script>', re.DOTALL)

count = 0
for platform in ("PC", "Android"):
    style_block = f'<style data-inlined="css">\n{CSS[platform]}\n</style>'
    script_block = f'<script data-inlined="toggle">\n{JS}\n</script>'
    for html in sorted((ROOT / platform).glob("*.html")):
        text = html.read_text(encoding="utf-8")
        if STYLE_RE.search(text):
            text = STYLE_RE.sub(lambda m: style_block, text)
        else:
            text = LINK_RE[platform].sub(lambda m: style_block, text)
        if SCRIPT_INLINED_RE.search(text):
            text = SCRIPT_INLINED_RE.sub(lambda m: script_block, text)
        else:
            text = SCRIPT_LINK_RE.sub(lambda m: script_block, text)
        html.write_text(text, encoding="utf-8")
        count += 1
        print(f"inlined: {platform}/{html.name}")

print(f"done — {count} files")
