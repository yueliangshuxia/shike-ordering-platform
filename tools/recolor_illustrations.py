"""把 unDraw 插画的默认配色（主色 #6c63ff）重映射到本站设计色板。

unDraw 的 SVG 里主色和中性灰都是硬编码的十六进制，直接在文本层替换即可，
不需要解析 SVG 结构。运行后原地覆盖 assets/illustrations/*.svg。
"""

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SVG_DIR = ROOT / "demo" / "assets" / "illustrations"

# unDraw 默认色 -> 本站色板
# 主色做成朱砂红（点餐场景最贴切的强调色），中性灰统一压到墨绿灰系
PALETTE = {
    # 主强调色
    "6c63ff": "C4321F",
    "6C63FF": "C4321F",
    # 深色中性（人物/结构主色）
    "3f3d56": "26332E",
    "444053": "26332E",
    "464353": "26332E",
    "5a5773": "34443D",
    # 中灰
    "65617d": "4A5B54",
    "565987": "4A5B54",
    "636780": "5C6B64",
    "575988": "5C6B64",
    "42312e": "2B3630",
    # 次级强调
    "ffd037": "D9A03C",
    # 肤色留暖，不要变菜色
    "fbbebe": "E8B9A8",
    "efb7b9": "E8B9A8",
    # 浅底
    "dce0ed": "E4E6E0",
    "f3f3f3": "F2F1EC",
    "f2f2f2": "F2F1EC",
}


def main() -> None:
    files = sorted(SVG_DIR.glob("*.svg"))
    if not files:
        raise SystemExit(f"没有找到 SVG：{SVG_DIR}")

    for path in files:
        text = path.read_text(encoding="utf-8")
        original = text
        hits = 0
        for old, new in PALETTE.items():
            text, n = re.subn(f"#{old}\\b", f"#{new}", text, flags=re.IGNORECASE)
            hits += n
        if text != original:
            path.write_text(text, encoding="utf-8")
        print(f"{path.name:38s} 替换 {hits:4d} 处")

    # 报告残留的色值，确认没有漏网
    leftovers: dict[str, int] = {}
    for path in files:
        for c in re.findall(r"#[0-9a-fA-F]{6}", path.read_text(encoding="utf-8")):
            leftovers[c.lower()] = leftovers.get(c.lower(), 0) + 1
    print("\n替换后残留色值：")
    for c, n in sorted(leftovers.items(), key=lambda kv: -kv[1]):
        print(f"  {c}  x{n}")


if __name__ == "__main__":
    main()
