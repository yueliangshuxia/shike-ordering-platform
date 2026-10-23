"""把下载的 Pexels 照片拼成一张校对图，用来肉眼确认每张图的实际内容。

图片是按搜索结果的文字描述选的，文字可能不准；拼成一张图一次性看完，
比逐张打开快，也方便决定哪张配哪道菜。
"""

import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "demo" / "assets" / "img" / "dishes"
OUT = ROOT / "tools" / "_contact_sheet.jpg"

COLS = 5
CELL_W, CELL_H = 300, 240
LABEL_H = 26


def main() -> None:
    files = sorted(IMG_DIR.glob("*.jpg"), key=lambda p: p.name)
    if not files:
        raise SystemExit(f"没有找到图片：{IMG_DIR}")

    rows = (len(files) + COLS - 1) // COLS
    sheet = Image.new("RGB", (COLS * CELL_W, rows * (CELL_H + LABEL_H)), "white")
    draw = ImageDraw.Draw(sheet)

    for i, path in enumerate(files):
        col, row = i % COLS, i // COLS
        x = col * CELL_W
        y = row * (CELL_H + LABEL_H)
        try:
            im = Image.open(path).convert("RGB")
            im.thumbnail((CELL_W - 8, CELL_H - 8))
            sheet.paste(im, (x + (CELL_W - im.width) // 2, y + (CELL_H - im.height) // 2))
        except Exception as exc:  # 坏图直接标出来，不要静默跳过
            draw.text((x + 8, y + 8), f"FAILED: {exc}", fill="red")
        draw.text((x + 6, y + CELL_H + 6), path.stem.replace("pexels-", "id "), fill="black")

    sheet.save(OUT, quality=88)
    print(f"输出 {OUT}  ({len(files)} 张, {sheet.width}x{sheet.height})")


if __name__ == "__main__":
    sys.exit(main())
