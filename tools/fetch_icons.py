"""扫描 demo 里所有页面和脚本引用到的 Iconify 图标，从官方 API 重新取一遍，
生成本地的 assets/icons/bundle.js。

为什么要本地化：
  1. iconify-icon 默认运行时向 api.iconify.design 逐个请求图标，断网就全空；
  2. 答辩现场网络不可控，图标不能是外部依赖。

用 addCollection 注册后组件不再走网络。改完页面加了新图标，重跑一次这个脚本即可。

用法：
    python tools/fetch_icons.py
"""

import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEMO = ROOT / "demo"
OUT_JS = DEMO / "assets" / "icons" / "bundle.js"
OUT_JSON = DEMO / "assets" / "icons" / "bundle.json"

# 页面里用 icon="mdi:xxx" 写死的，和脚本里 ic('mdi:xxx') 动态拼的，都要扫到
PATTERN = re.compile(r"mdi:[a-z0-9-]+")


def collect() -> list[str]:
    found: set[str] = set()
    for path in list(DEMO.glob("*.html")) + list((DEMO / "assets" / "js").glob("*.js")):
        found.update(PATTERN.findall(path.read_text(encoding="utf-8")))
    return sorted(found)


def fetch(names: list[str]) -> dict:
    icons = ",".join(n.split(":", 1)[1] for n in names)
    url = "https://api.iconify.design/mdi.json?icons=" + urllib.parse.quote(icons)
    # urllib 默认的 User-Agent 会被 Iconify 挡下来返回 403，得显式带一个
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "Accept": "application/json",
    })
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    names = collect()
    if not names:
        print("没扫到任何图标引用", file=sys.stderr)
        return 1

    print(f"扫到 {len(names)} 个图标，正在从 api.iconify.design 取…")
    data = fetch(names)

    have = set(data.get("icons", {})) | set(data.get("aliases", {}))
    wanted = {n.split(":", 1)[1] for n in names}
    missing = sorted(wanted - have)
    if missing:
        # 缺图标必须报出来，不能让它悄悄变成一个加载不出来的空标签
        print(f"警告：以下图标在 mdi 集合里不存在，页面里会显示空白：", file=sys.stderr)
        for m in missing:
            print(f"  mdi:{m}", file=sys.stderr)

    OUT_JSON.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_JS.write_text(
        "/* Iconify 图标数据（mdi 集合），由 tools/fetch_icons.py 自动生成，请勿手改。\n"
        "   存成 JS 全局而不是 JSON，是为了让页面用 file:// 直接打开时也能加载 ——\n"
        "   file:// 下 fetch 本地 JSON 会被 CORS 拦掉。 */\n"
        "window.SK_ICON_BUNDLE = "
        + json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        + ";\n",
        encoding="utf-8",
    )

    print(f"命中 {len(have)} 个，缺失 {len(missing)} 个")
    print(f"已写出 {OUT_JS.relative_to(ROOT)}  ({OUT_JS.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
