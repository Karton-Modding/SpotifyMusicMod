"""Generates the Spotify Widget icon.

The artwork is authored on a 16x16 grid and blown up with nearest-neighbour scaling, so every
output size stays crisp pixel art. Run with plain Python, no dependencies:

    python tools/make_icon.py

Writes icons/icon-<size>.png for each size below, plus icon.png (128) in the repo root and the
copy the mod ships at src/main/resources/assets/spotifywidget/icon.png.
"""

import math
import os
import struct
import zlib

GRID = 16
SIZES = (16, 32, 64, 128, 256, 512)

PALETTE = {
    '.': None,                      # transparent
    'K': (0x0E, 0x0E, 0x12, 255),   # outline
    'D': (0x1B, 0x1B, 0x22, 255),   # panel, shaded side
    'B': (0x2C, 0x2C, 0x39, 255),   # panel, lit side
    'O': (0x0D, 0x63, 0x2C, 255),   # disc rim
    'S': (0x15, 0x8E, 0x42, 255),   # disc shade
    'G': (0x1D, 0xB9, 0x54, 255),   # disc body
    'H': (0x4F, 0xE4, 0x88, 255),   # disc highlight
    'W': (0xFF, 0xFF, 0xFF, 255),   # wave mark
}

# Rows of the wave mark: (row, x positions)
ARCS = (
    (4, (5, 6, 7, 8, 9, 10)),
    (5, (3, 4, 11, 12)),
    (7, (6, 7, 8, 9)),
    (8, (5, 10)),
    (10, (7, 8)),
    (11, (6, 9)),
)


def build_grid():
    grid = [['.' for _ in range(GRID)] for _ in range(GRID)]

    # Panel: clipped corners, lit from the top left like a vanilla item
    for y in range(GRID):
        for x in range(GRID):
            clipped = (x < 1 and y < 1) or (x > GRID - 2 and y < 1) \
                or (x < 1 and y > GRID - 2) or (x > GRID - 2 and y > GRID - 2)
            if clipped:
                continue
            edge = x in (0, GRID - 1) or y in (0, GRID - 1) \
                or (x <= 1 and y <= 1) or (x >= GRID - 2 and y <= 1) \
                or (x <= 1 and y >= GRID - 2) or (x >= GRID - 2 and y >= GRID - 2)
            grid[y][x] = 'K' if edge else ('B' if (x + y) < 14 else 'D')

    # Disc with a dark rim and two shading steps
    centre = 7.5
    radius = 6.4
    for y in range(GRID):
        for x in range(GRID):
            distance = math.hypot(x - centre, y - centre)
            if distance > radius:
                continue
            if distance > radius - 1.0:
                grid[y][x] = 'O'
            elif distance > radius - 2.0:
                grid[y][x] = 'H' if (x - centre) + (y - centre) < 0 else 'S'
            else:
                grid[y][x] = 'G'

    for row, columns in ARCS:
        for x in columns:
            grid[row][x] = 'W'
    return grid


def write_png(grid, path, size):
    if size % GRID != 0:
        raise ValueError("size must be a multiple of " + str(GRID))
    scale = size // GRID
    rows = []
    for y in range(GRID):
        line = bytearray()
        for x in range(GRID):
            colour = PALETTE[grid[y][x]] or (0, 0, 0, 0)
            line.extend(struct.pack("BBBB", *colour) * scale)
        rows.extend([b'\x00' + bytes(line)] * scale)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data \
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b'\x89PNG\r\n\x1a\n' \
        + chunk(b'IHDR', struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) \
        + chunk(b'IEND', b'')

    os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)
    return len(png)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    grid = build_grid()

    for size in SIZES:
        target = os.path.join(root, "icons", "icon-{0}.png".format(size))
        write_png(grid, target, size)
        print("wrote", target)

    for target in (os.path.join(root, "icon.png"),
                   os.path.join(root, "src", "main", "resources", "assets", "spotifywidget", "icon.png")):
        write_png(grid, target, 128)
        print("wrote", target)

    print()
    for row in grid:
        print("".join(row))


if __name__ == "__main__":
    main()
