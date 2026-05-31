#!/usr/bin/env python3
"""Render static PNG previews from async-profiler HTML flamegraphs."""

from __future__ import annotations

import ast
import hashlib
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


@dataclass
class Frame:
    key: int
    level: int
    left: int
    width: int
    title: str


def unpack_cpool(cpool: list[str]) -> list[str]:
    for i in range(1, len(cpool)):
        shared = ord(cpool[i][0]) - 32
        cpool[i] = cpool[i - 1][:shared] + cpool[i][1:]
    return cpool


def parse_args(arg_text: str) -> list[int]:
    return [int(part.strip()) for part in arg_text.split(",") if part.strip()]


def parse_profile(path: Path) -> tuple[str, list[list[Frame]]]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    title = re.search(r"<h1>(.*?)</h1>", text, flags=re.S)
    title_text = re.sub(r"<.*?>", "", title.group(1)).strip() if title else path.stem

    cpool_match = re.search(r"const cpool = \[(.*?)\];\s*unpack\(cpool\);", text, flags=re.S)
    if not cpool_match:
        raise ValueError(f"cannot find cpool in {path}")
    cpool = ast.literal_eval("[" + cpool_match.group(1) + "]")
    cpool = unpack_cpool(cpool)

    level_match = re.search(r"const levels = Array\((\d+)\);", text)
    max_levels = int(level_match.group(1)) if level_match else 256
    levels: list[list[Frame]] = [[] for _ in range(max_levels)]

    data = text[cpool_match.end():]
    level0 = 0
    left0 = 0
    width0 = 0

    def add_frame(key: int, level: int, left_delta: int, width: int | None) -> None:
        nonlocal level0, left0, width0
        level0 = level
        left0 += left_delta
        if width:
            width0 = width
        if level0 >= len(levels):
            levels.extend([] for _ in range(level0 - len(levels) + 1))
        levels[level0].append(Frame(key, level0, left0, width0, cpool[key >> 3]))

    for kind, raw_args in re.findall(r"^([fun])\(([^)]*)\)", data, flags=re.M):
        args = parse_args(raw_args)
        if kind == "f":
            key, level, left = args[0], args[1], args[2]
            width = args[3] if len(args) >= 4 else None
            add_frame(key, level, left, width)
        elif kind == "u":
            key = args[0]
            width = args[1] if len(args) >= 2 else None
            add_frame(key, level0 + 1, 0, width)
        elif kind == "n":
            key = args[0]
            width = args[1] if len(args) >= 2 else None
            add_frame(key, level0, width0, width)

    return title_text, [level for level in levels if level]


def color_for(title: str) -> tuple[int, int, int]:
    digest = hashlib.blake2b(title.encode("utf-8"), digest_size=3).digest()
    if "ru.itmo.search" in title or "/itmo/search/" in title:
        base = (70, 180, 150)
    elif title.startswith("java") or title.startswith("jdk") or title.startswith("sun"):
        base = (90, 150, 215)
    elif title.startswith("[" ) or "::" in title:
        base = (220, 120, 80)
    else:
        base = (150, 190, 85)
    return tuple(min(245, max(45, base[i] + digest[i] % 45 - 22)) for i in range(3))


def render_profile(html_path: Path, out_path: Path, image_width: int = 1800) -> None:
    title, levels = parse_profile(html_path)
    total = levels[0][0].width
    scale = (image_width - 20) / total
    levels = [
        frames for frames in levels
        if any(int((frame.left + frame.width) * scale) > int(frame.left * scale) for frame in frames)
    ]
    frame_h = 16
    top_h = 46
    bottom = 14
    image_height = top_h + len(levels) * frame_h + bottom

    image = Image.new("RGB", (image_width, image_height), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default()

    draw.text((10, 8), title, fill=(20, 20, 20), font=font)
    draw.text((10, 25), f"{total:,} samples, {len(levels)} stack levels", fill=(80, 80, 80), font=font)

    for level_index, frames in enumerate(levels):
        y = top_h + level_index * frame_h
        for frame in frames:
            x0 = 10 + int(frame.left * scale)
            x1 = 10 + max(x0 + 1 - 10, int((frame.left + frame.width) * scale))
            if x1 <= x0:
                continue
            color = color_for(frame.title)
            draw.rectangle((x0, y, x1, y + frame_h - 2), fill=color, outline=(255, 255, 255))
            if x1 - x0 > 38:
                label = frame.title
                max_chars = max(1, (x1 - x0 - 6) // 6)
                if len(label) > max_chars:
                    label = label[: max_chars - 2] + ".." if max_chars > 2 else ""
                if label:
                    draw.text((x0 + 3, y + 2), label, fill=(20, 20, 20), font=font)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    image.save(out_path)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: render_profile_images.py INPUT.html OUTPUT.png", file=sys.stderr)
        return 2
    render_profile(Path(sys.argv[1]), Path(sys.argv[2]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
