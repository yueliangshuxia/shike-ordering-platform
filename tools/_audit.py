# 全站结构与无障碍巡检：这是一次性补充检查，覆盖面见下。
import pathlib, re, subprocess, sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEMO = ROOT / "demo"
CHROME = pathlib.Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")
PROFILE = ROOT / "tools" / "_verify_profile"

A11Y = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(() => {
    const out = [];
    const lum = (c) => {
      const m = String(c).match(/[\d.]+/g); if (!m || m.length < 3) return null;
      const [r, g, b] = m.slice(0, 3).map(Number).map(v => {
        v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
      });
      return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    };
    const ratio = (a, b) => {
      const A = lum(a), B = lum(b); if (A === null || B === null) return null;
      const hi = Math.max(A, B), lo = Math.min(A, B);
      return (hi + 0.05) / (lo + 0.05);
    };

    // 1. 图片没 alt / 没宽度
    const noAlt = [...document.images].filter(i => !i.hasAttribute('alt')).length;

    // 2. 可点元素没有可访问名
    const named = (el) => (el.getAttribute('aria-label') || el.textContent || '').trim()
      || el.querySelector('img[alt]')?.getAttribute('alt') || '';
    const unnamed = [...document.querySelectorAll('button, a[href]')].filter(el => !named(el)).length;

    // 3. 重复 id
    const ids = [...document.querySelectorAll('[id]')].map(e => e.id);
    const dup = [...new Set(ids.filter((v, i) => ids.indexOf(v) !== i))];

    // 4. 标题层级
    const hs = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')].map(h => +h.tagName[1]);
    const h1 = hs.filter(n => n === 1).length;
    const skips = hs.filter((n, i) => i && n > hs[i - 1] + 1).length;

    // 5. 表单控件没有标签
    const unlabeled = [...document.querySelectorAll('input,textarea,select')].filter(el => {
      if (['hidden','checkbox','radio','submit'].includes(el.type)) {
        return !el.closest('label') && !el.getAttribute('aria-label') && !el.id;
      }
      if (el.getAttribute('aria-label') || el.getAttribute('title')) return false;
      if (el.id && document.querySelector(`label[for="${el.id}"]`)) return false;
      return !el.closest('label');
    }).length;

    // 6. 正文字号下的对比度（挑几个有代表性的可见文本节点）
    const bad = [];
    [...document.querySelectorAll('p, span, a, li, td, div')].slice(0, 400).forEach(el => {
      if (!el.textContent.trim() || el.children.length) return;
      const cs = getComputedStyle(el);
      if (cs.visibility === 'hidden' || cs.display === 'none' || +cs.opacity < 0.5) return;
      const size = parseFloat(cs.fontSize);
      // 从元素往上找到第一个不透明的背景
      let bg = cs.backgroundColor, node = el;
      while (node && (!bg || bg === 'rgba(0, 0, 0, 0)' || /rgba\(.*, 0\)$/.test(bg))) {
        node = node.parentElement; if (!node) break;
        bg = getComputedStyle(node).backgroundColor;
      }
      const r = ratio(cs.color, bg);
      const need = size >= 24 ? 3 : (size >= 18.66 && +cs.fontWeight >= 700 ? 3 : 4.5);
      if (r !== null && r < need) {
        bad.push((el.textContent.trim().slice(0, 12)) + '=' + r.toFixed(2) + '(需' + need + ')');
      }
    });

    document.title = 'RESULT|'
      + '图缺alt=' + noAlt + ' 控件无名=' + unnamed + ' 重复id=' + dup.length
      + (dup.length ? JSON.stringify(dup.slice(0, 5)) : '')
      + ' | h1数=' + h1 + ' 标题跳级=' + skips
      + ' 控件缺标签=' + unlabeled
      + ' | 对比度不足=' + bad.length + (bad.length ? ' ' + JSON.stringify([...new Set(bad)].slice(0, 6)) : '');
  }, 700);
});
</script>
</body>"""

PAGES = ["index.html", "menu.html", "menu.html?q=小笼包", "dish.html?id=D1001",
         "cart.html?demo=1", "checkout.html?demo=1", "order.html", "profile.html",
         "login.html", "recommend.html", "admin.html"]


def audit(page):
    file_part, _, query = page.partition("?")
    src = (DEMO / file_part).read_text(encoding="utf-8")
    tmp = DEMO / "_probe_tmp.html"
    tmp.write_text(src.replace("</body>", A11Y), encoding="utf-8")
    try:
        res = subprocess.run(
            [str(CHROME), "--headless=new", "--disable-gpu", "--window-size=1440,900",
             f"--user-data-dir={PROFILE}", "--virtual-time-budget=20000", "--dump-dom",
             f"file:///{str(tmp).replace(chr(92), '/')}" + (f"?{query}" if query else "")],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        m = re.search(r"<title>RESULT\|(.*?)</title>", res.stdout, re.S)
        return m.group(1) if m else "（没拿到结果）"
    finally:
        tmp.unlink(missing_ok=True)


for p in PAGES:
    print(f"[{p}]\n    {audit(p)}\n")
