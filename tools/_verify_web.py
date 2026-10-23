# -*- coding: utf-8 -*-
"""Vue 前端的页面自测。

跟 `_verify.py`（测静态 Demo）和 `smoke_api.py`（测后端接口）互补：
这个脚本回答的是「**页面真的渲染出来了吗**」——
构建通过只说明语法没错，不等于页面能打开、接口能接上。

做法：用无头 Chrome 打开每个路由，把渲染后的 DOM 抓回来，
断言里面出现了该页应有的内容。如果 Vue 在挂载时抛了异常，
DOM 里就只会剩下一个空的 `<div id="app">`，断言会立刻失败。

前提：后端在 8080、Vite 在 5173 都要起着。

    bash tools/run_server.sh &
    cd web && npm run dev &
    PYTHONIOENCODING=utf-8 python tools/_verify_web.py
"""
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

WEB = "http://localhost:5173"
API = "http://localhost:8080"

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"),
]

passed = 0
failed = 0


def find_chrome():
    for p in CHROME_CANDIDATES:
        if os.path.exists(p):
            return p
    raise SystemExit("找不到 Chrome")


CHROME = find_chrome()


def dump_dom(url, profile, wait_ms=9000):
    """打开一个页面，把渲染完的 DOM 抓回来。"""
    args = [
        CHROME,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--no-first-run",
        "--disable-extensions",
        f"--user-data-dir={profile}",
        f"--virtual-time-budget={wait_ms}",
        "--window-size=1440,1000",
        "--dump-dom",
        url,
    ]
    r = subprocess.run(args, capture_output=True, timeout=90)
    # Windows 下 Chrome 输出的编码不一定是 UTF-8，容错解一次
    for enc in ("utf-8", "gbk", "latin-1"):
        try:
            return r.stdout.decode(enc)
        except UnicodeDecodeError:
            continue
    return ""


def check(label, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  [ok ] {label}")
    else:
        failed += 1
        print(f"  [!! ] {label}   {detail}")


def api_post(path, body):
    req = urllib.request.Request(
        API + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode("utf-8"))["data"]


def section(t):
    print(f"\n---------- {t} ----------")


profile = os.path.join(tempfile.gettempdir(), "shike_web_verify_profile")
os.makedirs(profile, exist_ok=True)

# 登录拿令牌，供需要登录的页面用
customer = api_post("/api/auth/login", {"username": "13800000621", "password": "123456"})
admin = api_post("/api/auth/login", {"username": "13900000001", "password": "admin123"})
kitchen = api_post("/api/auth/login", {"username": "13900000002", "password": "kitchen123"})

# 菜品详情的断言要问后端要菜名，不能写死。
# 写死过一次，菜单换了之后这个用例就红了 —— 而页面本身是好的，
# 红的是这份测试清单。凡是能被后端问出来的事实，就不要在前端复述一遍。
def first_dish():
    with urllib.request.urlopen(API + "/api/dishes", timeout=15) as r:
        dishes = json.loads(r.read().decode("utf-8"))["data"]
    d = dishes[0]
    return d["code"], d["name"]


SAMPLE_CODE, SAMPLE_NAME = first_dish()

# 公开页面：直接打开
PUBLIC = [
    ("/", ["每道菜要做多久", "为你推荐"], "首页"),
    ("/menu", ["全部菜单", "分 类", "点 单"], "菜单页"),
    ("/recommend", ["推荐实验室", "准则权重", "TOPSIS 中间量"], "推荐实验室"),
    (f"/dish/{SAMPLE_CODE}", [SAMPLE_NAME, "出餐约"], "菜品详情"),
    ("/cart", ["我的点单"], "购物车"),
    ("/login", ["演示账号", "登录"], "登录页"),
    ("/no-such-page", ["走错路了"], "404 页"),
]

section("公开页面能否渲染")
for path, markers, name in PUBLIC:
    dom = dump_dom(WEB + path, profile)
    ok = all(m in dom for m in markers)
    detail = "缺少：" + "、".join(m for m in markers if m not in dom) if not ok else ""
    check(f"{name} {path}", ok, detail)
    if "vite-error-overlay" in dom or "Failed to resolve" in dom:
        check(f"{name} 无构建错误", False, "出现了 Vite 报错浮层")


# 需要登录的页面：先种一个 localStorage 令牌再跳过去。
# 用一张临时页面做这件事 —— Chrome 命令行没法直接往页面里注入脚本
section("需要登录的页面")
SEED = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "web", "public")
seed_path = os.path.join(SEED, "_devlogin.html")

AUTHED = [
    ("/orders", ["我的订单"], "我的订单", customer),
    ("/profile", ["个人中心", "口味偏好"], "个人中心", customer),
    ("/checkout", ["点单栏是空的"], "结算页（空车）", customer),
    ("/kitchen", ["后厨出单"], "后厨屏", kitchen),
    ("/admin", ["经营看板", "今日营业额"], "经营看板", admin),
]

try:
    with open(seed_path, "w", encoding="utf-8") as f:
        f.write(
            "<!doctype html><meta charset='utf-8'><title>seed</title><script>\n"
            "const q = new URLSearchParams(location.search);\n"
            "localStorage.setItem('sk_token', q.get('t') || '');\n"
            "location.replace(q.get('to') || '/');\n"
            "</script>"
        )
    for path, markers, name, session in AUTHED:
        seed_url = f"{WEB}/_devlogin.html?t={session['token']}&to={path}"
        dom = dump_dom(seed_url, profile)
        ok = all(m in dom for m in markers)
        detail = "缺少：" + "、".join(m for m in markers if m not in dom) if not ok else ""
        check(f"{name} {path}", ok, detail)
finally:
    # 这张页面只是测试用的，绝不该跟着构建产物发布出去
    if os.path.exists(seed_path):
        os.remove(seed_path)

section("未登录时的跳转")
dom = dump_dom(WEB + "/admin", profile)
check("未登录访问 /admin 会被挡到登录页", "演示账号" in dom or "登录" in dom)

print(f"\n{'=' * 46}")
print(f"通过 {passed} 项，失败 {failed} 项")
print("=" * 46)
sys.exit(1 if failed else 0)
