#!/usr/bin/env python3

import random
from pathlib import Path

IMAGES = Path(__file__).resolve().parent.parent / "src/main/resources/static/images"

WIDTH = 1600
HEIGHT = 1000
COLS = 8
ROWS = 5
MOBILE_WIDTH = 900
MOBILE_HEIGHT = 1800
MOBILE_COLS = 5
MOBILE_ROWS = 10
JITTER = 0.4
MARGIN = 0.1

FONT = "Apple Color Emoji, Segoe UI Emoji, Noto Color Emoji, sans-serif"

CATS = ["🐱", "🐈", "😺", "😸", "😻", "😽", "😼"]
DECOR = ["🌸", "🌺", "🌷", "✨", "🫧", "🦋"]


def scatter(name: str, count: int, fonts, opacities, rotations, mix_cats: float, keep_center: bool, seed: int,
            width: int = WIDTH, height: int = HEIGHT, cols: int = COLS, rows: int = ROWS) -> None:
    rng = random.Random(seed)
    step_x = width / cols
    step_y = height / rows
    items = []

    wanted = count * 2
    pool = [rng.choice(CATS) for _ in range(round(wanted * mix_cats))]
    pool += [rng.choice(DECOR) for _ in range(wanted - len(pool))]
    rng.shuffle(pool)

    candidates = list(rng.sample(range(cols * rows), cols * rows))
    while len(candidates) < wanted:
        candidates += rng.sample(range(cols * rows), cols * rows)

    for index, emoji in zip(candidates, pool):
        if len(items) == count:
            break
        row, col = divmod(index, cols)
        x = (col + 0.5) * step_x + rng.uniform(-JITTER, JITTER) * step_x
        y = (row + 0.5) * step_y + rng.uniform(-JITTER, JITTER) * step_y

        x = min(max(x, width * MARGIN), width * (1 - MARGIN))
        y = min(max(y, height * MARGIN), height * (1 - MARGIN))

        if not keep_center and 0.34 * width < x < 0.66 * width and 0.28 * height < y < 0.72 * height:
            continue

        font = round(rng.choice(fonts))
        angle = rng.choice(rotations)
        opacity = round(rng.uniform(*opacities), 3)
        items.append(
            f'  <text x="{x:.1f}" y="{y:.1f}" font-family="{FONT}" font-size="{font}"'
            f' opacity="{opacity}" text-anchor="middle" dominant-baseline="central"'
            f' transform="rotate({angle} {x:.1f} {y:.1f})">{emoji}</text>'
        )

    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}"'
        f' viewBox="0 0 {width} {height}">\n'
        + "\n".join(items)
        + "\n</svg>\n"
    )
    (IMAGES / name).write_text(svg, encoding="utf-8")
    print(f"{name}: {len(items)} шт., котиков ~{int(mix_cats * 100)}%")


def main() -> None:
    IMAGES.mkdir(parents=True, exist_ok=True)
    scatter("bg-scatter-far.svg", count=16, fonts=(60, 80, 110), opacities=(0.24, 0.36),
            rotations=(-10, 0, 10), mix_cats=1.0, keep_center=True, seed=7)
    scatter("bg-scatter.svg", count=30, fonts=(34, 44, 64), opacities=(0.40, 0.60),
            rotations=(-12, -5, 0, 7, 12), mix_cats=0.55, keep_center=False, seed=21)
    scatter("bg-sparkles.svg", count=46, fonts=(12, 18, 26), opacities=(0.45, 0.70),
            rotations=(-20, 0, 20), mix_cats=0.0, keep_center=False, seed=33)
    scatter("bg-scatter-mobile.svg", count=58, fonts=(26, 36, 52), opacities=(0.34, 0.55),
            rotations=(-14, -5, 0, 6, 14), mix_cats=0.45, keep_center=False, seed=55,
            width=MOBILE_WIDTH, height=MOBILE_HEIGHT, cols=MOBILE_COLS, rows=MOBILE_ROWS)


if __name__ == "__main__":
    main()
