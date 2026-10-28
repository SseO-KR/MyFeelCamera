#!/usr/bin/env python3
"""Generate frozen references with CPU trilinear interpolation, independent of GLSL.

Run explicitly after reviewing an intentional LUT change. CI uses --check, never
updates expectations. No Android device, renderer, or third-party package needed.
"""
import argparse
import hashlib
import itertools
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/androidTest/assets/film"
WIDTH, HEIGHT, SIZE = 96, 64, 32
COLORS = [
    (224, 45, 35), (40, 195, 70), (45, 75, 220), (225, 195, 40), (180, 45, 185), (35, 190, 195),
    (25, 25, 25), (65, 65, 65), (105, 105, 105), (145, 145, 145), (185, 185, 185), (225, 225, 225),
    (190, 135, 100), (90, 55, 40), (240, 190, 160), (70, 110, 160), (85, 120, 60), (180, 145, 75),
]


def fixture():
    pixels = []
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if y < 48:
                pixels.append(COLORS[(y // 16) * 6 + x // 16])
            else:
                v = round(x * 255 / (WIDTH - 1))
                pixels.append((v, v, v) if y < 56 else (v, 255 - v, (x * 37) % 256))
    return pixels


def apply_lut(rgb, lut):
    position = [value * (SIZE - 1) / 255 for value in rgb]
    lower = [int(value) for value in position]
    fraction = [value - base for value, base in zip(position, lower)]
    result = [0.0, 0.0, 0.0]
    for corner in itertools.product((0, 1), repeat=3):
        r, g, b = [min(base + offset, SIZE - 1) for base, offset in zip(lower, corner)]
        weight = 1.0
        for offset, value in zip(corner, fraction):
            weight *= value if offset else 1 - value
        index = ((b * SIZE + g) * SIZE + r) * 3
        for channel in range(3):
            result[channel] += lut[index + channel] * weight
    return tuple(round(value) for value in result)


def png(pixels):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\0" + bytes(itertools.chain.from_iterable(pixels[y * WIDTH:(y + 1) * WIDTH]))
                    for y in range(HEIGHT))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    source = fixture()
    files = {"input.png": png(source)}
    # 촬영 E2E는 센서 회전·해상도 차이에 영향받지 않도록 일정한 회색 입력을 사용한다.
    files["camera-input.png"] = png([(128, 128, 128)] * (WIDTH * HEIGHT))
    hashes = {}
    camera_expected = {}
    for film in ("warm", "dusk"):
        lut = (ROOT / f"app/src/main/assets/lut/film_{film}.bin").read_bytes()
        if len(lut) != SIZE ** 3 * 3:
            raise SystemExit(f"Invalid {film} LUT size")
        hashes[film] = hashlib.sha256(lut).hexdigest()
        camera_expected[film] = apply_lut((128, 128, 128), lut)
        files[f"{film}.png"] = png([apply_lut(rgb, lut) for rgb in source])
    files["references.json"] = (json.dumps({
        "generator": "CPU RGB8 LUT trilinear interpolation; R fastest, then G, B",
        "effects": "LUT strength 1; grain and vignette disabled (current production settings)",
        "lut_sha256": hashes,
        "camera_input_rgb": [128, 128, 128],
        "camera_expected_rgb": camera_expected,
        "png_sha256": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
    }, indent=2) + "\n").encode()
    for name, data in files.items():
        path = DEST / name
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise SystemExit(f"Reference differs: {path.relative_to(ROOT)}. Review before regenerating.")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print(f"{'Verified' if args.check else 'Generated'} {len(files)} film reference files")


if __name__ == "__main__":
    main()
