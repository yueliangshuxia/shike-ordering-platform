# 响应式 + 异常输入巡检
import pathlib, re, subprocess, sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = pathlib.Path(__file__).resolve().parent.parent
DEMO = ROOT/"demo"; CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
PROFILE = ROOT/"tools"/"_verify_profile"

OVERFLOW = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(() => {
    const de = document.documentElement;
    const over = de.scrollWidth - de.clientWidth;
    // 找出是谁把页面顶宽的
    let culprits = [];
    if (over > 1) {
      culprits = [...document.querySelectorAll('*')].filter(el => {
        const r = el.getBoundingClientRect();
        return r.right > de.clientWidth + 2 && r.width > 0;
      }).slice(0, 4).map(el => (el.tagName.toLowerCase()
        + (el.className && typeof el.className === 'string' ? '.' + el.className.trim().split(/\s+/).join('.') : '')));
    }
    document.title = 'RESULT|client=' + de.clientWidth + ' scroll=' + de.scrollWidth
      + ' 溢出=' + over + (over > 1 ? ' ' + JSON.stringify(culprits) : '');
  }, 600);
});
</script>
</body>"""

PAGES = ["index.html", "menu.html", "dish.html?id=D1001", "cart.html?demo=1",
         "checkout.html?demo=1", "order.html", "profile.html", "login.html",
         "recommend.html", "admin.html"]


def probe(page, body, w):
    file_part, _, query = page.partition("?")
    src = (DEMO/file_part).read_text(encoding="utf-8")
    tmp = DEMO/"_probe_tmp.html"; tmp.write_text(src.replace("</body>", body), encoding="utf-8")
    try:
        res = subprocess.run([CHROME, "--headless=new", "--disable-gpu",
            f"--window-size={w},1000", f"--user-data-dir={PROFILE}", "--virtual-time-budget=20000", "--dump-dom",
            f"file:///{str(tmp).replace(chr(92),'/')}" + (f"?{query}" if query else "")],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        m = re.search(r"<title>RESULT\|(.*?)</title>", res.stdout, re.S)
        return m.group(1) if m else "（没拿到结果）"
    finally:
        tmp.unlink(missing_ok=True)


# 无头 Chrome 在 Windows 上有约 500px 最小窗口宽度，所以 500 以下测不准
for w in (500, 760, 900, 1024, 1180, 1440):
    print(f"---------- 视口宽 {w}px ----------")
    for p in PAGES:
        out = probe(p, OVERFLOW, w)
        flag = "[溢出]" if "溢出=0" not in out else "      "
        print(f"  {flag} {p:22} {out}")
    print()
