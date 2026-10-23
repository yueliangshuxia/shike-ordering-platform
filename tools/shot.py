"""用本机 Chrome 无头模式给 Demo 页面截图，用来自己检查渲染效果。

用法：
    python tools/shot.py index.html
    python tools/shot.py menu.html --w 1440 --h 2200
    python tools/shot.py menu.html --w 414 --h 1400 --out mobile.png

截图落在 tools/_shots/ 下。

已知限制：Chrome 无头模式在 Windows 上有个约 500px 的最小窗口宽度，
传 --w 414 实际会按 500px 排版再把图裁到 414，看起来像右侧被切掉。
要判断移动端有没有真的横向溢出，别只看截图 —— 用下面的办法测真实的
clientWidth 和 scrollWidth 是否相等。--w 500 及以上是准的。
"""

import argparse
import pathlib
import shutil
import subprocess
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEMO = ROOT / "demo"
SHOTS = ROOT / "tools" / "_shots"

CHROME_CANDIDATES = [
    pathlib.Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
    pathlib.Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
    pathlib.Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    pathlib.Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
]


def find_browser() -> pathlib.Path:
    for p in CHROME_CANDIDATES:
        if p.exists():
            return p
    raise SystemExit("没找到 Chrome 或 Edge")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("page", help="demo/ 下的页面文件名，如 index.html")
    ap.add_argument("--w", type=int, default=1440, help="视口宽度")
    ap.add_argument("--h", type=int, default=2000, help="视口高度")
    ap.add_argument("--out", default=None, help="输出文件名，默认跟页面同名")
    ap.add_argument("--wait", type=int, default=3500, help="虚拟时间预算（毫秒），等 JS 跑完")
    args = ap.parse_args()

    # 允许带查询串，如 cart.html?demo=1，文件系统那一份要去掉 ? 之后的部分
    file_part = args.page.split("?", 1)[0]
    target = DEMO / file_part
    if not target.exists():
        raise SystemExit(f"页面不存在：{target}")

    SHOTS.mkdir(parents=True, exist_ok=True)
    out = SHOTS / (args.out or (target.stem + ".png"))

    # 路径含中文，必须转成百分号编码 Chrome 才认。
    # 查询串里的 ? = & 要保留原样，所以单独拼回去。
    path_enc = urllib.parse.quote(str(target).replace("\\", "/"), safe="/:")
    query = "?" + args.page.split("?", 1)[1] if "?" in args.page else ""
    url = f"file:///{path_enc}{query}"

    profile = ROOT / "tools" / "_chrome_profile"
    cmd = [
        str(find_browser()),
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--force-device-scale-factor=1",
        f"--window-size={args.w},{args.h}",
        f"--virtual-time-budget={args.wait}",
        f"--user-data-dir={profile}",
        f"--screenshot={out}",
        url,
    ]
    # Chrome 的 stderr 里混着非 UTF-8 字节，不加 errors 会在读输出时炸掉
    res = subprocess.run(cmd, capture_output=True, text=True,
                         encoding="utf-8", errors="replace", timeout=120)

    if not out.exists():
        print("截图失败，浏览器输出：", file=sys.stderr)
        print(res.stdout[-2000:], res.stderr[-2000:], file=sys.stderr)
        return 1

    print(f"OK  {out}  ({out.stat().st_size} bytes, {args.w}x{args.h})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
