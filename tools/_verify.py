# 一次性验证脚本：往页面副本里注入探针，跑无头 Chrome，从 title 里读回结果。
# 探针脚本放在第一个 <script src> 之前，这样它写的 localStorage
# 能赶上 app.js 在脚本求值时调用的 loadProfile()。
import pathlib
import re
import subprocess
import sys

# Windows 控制台默认 GBK，探针结果里有 ¥ 和 ✓ 之类的字符会直接把脚本打断
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEMO = ROOT / "demo"
CHROME = pathlib.Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")
# 用自己的 profile，别跟 shot.py 共用 —— 探针会往 localStorage 里塞登录态和偏好，
# 共用一个目录的话截图里会出现上一轮探针留下的用户名。
PROFILE = ROOT / "tools" / "_verify_profile"
ANCHOR = '<script src="assets/vendor/iconify-icon.min.js"></script>'

BODY = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  const report = (s) => { document.title = 'RESULT|' + s; };
  setTimeout(() => {
    try {
      const r = (function () { %s })();
      if (typeof r === 'string') report(r);
    } catch (e) { report('探针异常: ' + e.message); }
  }, 200);
});
</script>
</body>"""


def run(page_html, probe_js, seed_js=""):
    # page_html 可以带查询串（如 cart.html?demo=1），文件系统那一份要去掉 ? 之后的部分
    file_part, _, query = page_html.partition("?")
    src = (DEMO / file_part).read_text(encoding="utf-8")
    # 每个用例都从干净的 localStorage 起跑。profile 是复用的，
    # 不清的话前一条用例留下的购物车/偏好会串到这一条里。
    seed = f"<script>localStorage.clear(); {seed_js}</script>\n"
    html = src.replace("</body>", BODY % probe_js).replace(ANCHOR, seed + ANCHOR)

    tmp = DEMO / "_probe_tmp.html"
    tmp.write_text(html, encoding="utf-8")
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


CHECKS = [
    ("1 cart 加减每次只动 1", "cart.html?demo=1", """
      const b = document.querySelector('[data-inc]');
      const k = b.dataset.inc + '#' + b.dataset.spec;
      const q0 = SK.cart.lines.find(l => SK.lineKey(l) === k).qty;
      b.click();
      const q1 = SK.cart.lines.find(l => SK.lineKey(l) === k).qty;
      document.querySelector('[data-dec]').click();
      const q2 = SK.cart.lines.find(l => SK.lineKey(l) === k).qty;
      return `加 ${q0}->${q1}（期望 +1）　减 ${q1}->${q2}（期望 -1）`;
    """, ""),

    # 观察者回调要等浏览器出帧才派发，而无头 + 虚拟时间下出帧时机不稳定
    # （实测 rAF 一次都不触发），拿"滚动后有没有高亮"当判据会误报。
    # 改成直接问：观察者到底有没有被挂到那 8 个 section 上。
    # 这正是修复前失效的点 —— 那时 render() 还没建出 section，
    # initCatRail() 拿到空数组就 return 了，观察者根本没创建。
    ("2 menu 分类竖栏的滚动观察者已挂上", "menu.html", """
      const io = window.__io;
      const links = document.querySelectorAll('.cat-rail a').length;
      document.querySelector('[data-filter="素食"]').click();
      const off = document.querySelectorAll('.cat-rail .cat-off').length;
      return `观察到的 section=${JSON.stringify(io.observed)}（期望先是 8 个全分类，筛素食后剩 5 个）　`
           + `竖栏链接=${links}　筛素食后置灰分类=${off}（期望 >0）`;
    """, """
      // 在 app.js / render() 之前把 IntersectionObserver 包一层，记录它被怎么用的。
      // iconify-icon 自己也在用 IntersectionObserver（懒加载图标），
      // 所以只记 class 里带 cat-section 的那些，免得被噪音淹没。
      window.__io = { observed: [] };
      const OrigIO = window.IntersectionObserver;
      window.IntersectionObserver = function (cb, opts) {
        const inst = new OrigIO(cb, opts);
        const observe = inst.observe.bind(inst);
        inst.observe = (el) => {
          if (el.classList && el.classList.contains('cat-section')) window.__io.observed.push(el.id);
          return observe(el);
        };
        return inst;
      };
    """),

    ("3 recommend 滑杆拖动不重建", "recommend.html", """
      const before = document.querySelector('[data-w="taste"]');
      before.value = 44;
      before.dispatchEvent(new Event('input', { bubbles: true }));
      const after = document.querySelector('[data-w="taste"]');
      return `节点被替换=${before !== after}（期望 false）　滑杆值=${after.value}（期望 44）　`
           + `百分比=${document.querySelector('[data-sv="taste"]').textContent}`;
    """, ""),

    ("4 个人中心存的偏好在别的页面生效", "index.html", """
      return `maxSpice=${USER_PROFILE.maxSpice}（期望 0）　preferCats=[${USER_PROFILE.preferCats}]（期望只有 drink）　`
           + `avoid=[${USER_PROFILE.avoid}]（期望含 葱）`;
    """, "localStorage.setItem('sk_pref_v1', JSON.stringify({preferCats:['drink'], maxSpice:0, avoid:['葱']}));"),

    ("5 登录态在别的页面生效", "index.html", """
      const nav = [...document.querySelectorAll('.topbar-actions a')]
        .map(a => a.textContent.trim().replace(/\\s+/g, ' '));
      return `顶栏账号位=${JSON.stringify(nav)}（期望含 林同学）　USER_PROFILE.user.name=${USER_PROFILE.user.name}`;
    """, "localStorage.setItem('sk_user_v1', JSON.stringify({phone:'13900001111', name:'林同学'}));"),

    ("6 忌口真的影响推荐", "recommend.html", """
      const plain = { ...USER_PROFILE, avoid: [] };
      const picky = { ...USER_PROFILE, avoid: ['葱'] };
      const hit = DISHES.find(d => (d.ingredients || []).some(i => i.includes('葱')));
      if (!hit) return '数据里没有含葱的菜，这条测不了';
      const a = SKR.tasteScore(hit, plain), b = SKR.tasteScore(hit, picky);
      const same = SKR.recommend(DISHES, plain).map(s => s.dish.id).join()
                === SKR.recommend(DISHES, picky).map(s => s.dish.id).join();
      return `${hit.name} 口味分 ${a} -> ${b}（期望下降）　整榜顺序没变=${same}`;
    """, ""),

    ("7 不动忌口时推荐结果不变", "recommend.html", """
      const none = { ...USER_PROFILE, avoid: [] };
      const undef = { ...USER_PROFILE }; delete undef.avoid;
      const a = SKR.recommend(DISHES, none).map(s => s.dish.id).join();
      const b = SKR.recommend(DISHES, undef).map(s => s.dish.id).join();
      return `空数组与不传字段的结果一致=${a === b}（期望 true）`;
    """, ""),

    ("8 kitchen 头部数字跟得上队列", "kitchen.html", """
      const kb = () => [...document.querySelectorAll('.kds-bar .kds-stat b')].map(b => b.textContent);
      const before = kb();
      // 把每一张单子一路推到「已出单」（待接单→制作中→待上菜→已出单，要三步）
      for (let i = 0; i < 8; i += 1) {
        document.querySelectorAll('[data-act="next"]').forEach(b => b.click());
      }
      const after = kb();
      return `清空前 [待处理, 今日已出, 最长等待]=${JSON.stringify(before)}`
           + `　清空后=${JSON.stringify(after)}`
           + `　队列清空态=${!!document.querySelector('.kds-empty')}（期望 true，且最长等待应归 0、今日已出应增加）`;
    """, ""),

    ("9 admin 菜品管理计数跟着上下架变", "admin.html", """
      const txt = () => document.getElementById('dish-count').textContent;
      const before = txt();
      const onSale = DISHES.find(d => d.stock > 0);
      document.querySelector(`[data-toggle="${onSale.id}"]`).click();
      const after = txt();
      const opt = echarts.getInstanceByDom(document.getElementById('chart-revenue')).getOption();
      const label = opt.series[0].markLine.label.formatter;
      const line = opt.series[0].markLine.data[0].yAxis;
      const avg = Math.round(REVENUE_14D.reduce((s, d) => s + d.v, 0) / REVENUE_14D.length);
      return `下架前「${before}」　下架后「${after}」（期望跟着变）　`
           + `折线日均：线画在 ${line}，标签写「${label}」，实际均值 ${avg}`;
    """, ""),

    ("10 kitchen 深色底上的 toast 看得见吗", "kitchen.html", """
      SK.toast('测试提示条', 'mdi:check-circle');
      const t = document.querySelector('.toast');
      const cs = getComputedStyle(t);
      const lum = (c) => {
        const [r, g, b] = c.match(/\d+/g).map(Number).map(v => {
          v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
      };
      const a = lum(cs.backgroundColor), b = lum(getComputedStyle(document.body).backgroundColor);
      const hi = Math.max(a, b), lo = Math.min(a, b);
      return `toast 底色=${cs.backgroundColor} 页面底色=${getComputedStyle(document.body).backgroundColor}`
           + `　对比度=${((hi + 0.05) / (lo + 0.05)).toFixed(2)}:1（正文至少要 4.5:1，1.0 就是完全看不见）`;
    """, ""),
]

for name, page, probe, seed in CHECKS:
    print(f"[{name}]\n    {run(page, probe, seed)}\n")


# ---------- 全站回归：每个页面有没有 JS 报错、有没有加载失败的图 ----------
AUDIT_HEAD = """<script>
window.__err = [];
window.addEventListener('error', (e) => {
  window.__err.push((e.target && e.target.tagName === 'IMG' ? 'IMG ' + e.target.getAttribute('src')
                   : (e.message || 'resource')) );
}, true);
</script>"""

AUDIT_BODY = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(() => {
    const bad = [...document.images]
      .filter(im => im.complete && im.naturalWidth === 0)
      .map(im => im.getAttribute('src'));
    document.title = 'RESULT|ERR=' + window.__err.length
      + ' 坏图=' + bad.length + (bad.length ? ' ' + JSON.stringify(bad) : '')
      + ' | ' + JSON.stringify(window.__err.slice(0, 4));
  }, 600);
});
</script>
</body>"""

def first_dish_id():
    """从 data.js 里取第一道菜的编码，不要写死。

    写死过一次教训：`_verify_web.py` 里断言的是「D1001 就是蟹黄小笼包」，
    结果换菜单后 D1001 变成了水晶虾饺，用例红了 —— 但红的是这份测试清单，
    页面本身好好的。凡是能从数据里问出来的事实，就不要在测试里复述一遍。
    """
    src = (DEMO / "assets/js/data.js").read_text(encoding="utf-8")
    # 只在 DISHES 数组里找。整份文件里 `id: 'Dxxxx'` 还出现在历史订单的
    # 明细里（那些也是合法的菜品编码，所以不至于出错，但会把范围搞糊）
    block = src.split("const DISHES = [", 1)[-1].split("\nconst ", 1)[0]
    ids = re.findall(r"id:\s*'(D\d+)'", block)
    if not ids:
        raise SystemExit("在 data.js 的 DISHES 里没找到菜品编码，测试清单没法生成")
    return ids[0]


DISH_ID = first_dish_id()

PAGES = [
    ("index.html", ""), ("menu.html", ""), ("menu.html", "?q=小笼包"), ("menu.html", "?q=没有这道菜"),
    ("dish.html", f"?id={DISH_ID}"), ("dish.html", "?id=不存在"), ("cart.html", "?demo=1"),
    ("checkout.html", "?demo=1"), ("order.html", ""), ("profile.html", ""),
    ("login.html", ""), ("recommend.html", ""), ("admin.html", ""), ("kitchen.html", ""),
]


def audit(page, query):
    file_part = page.split("?", 1)[0]
    src = (DEMO / file_part).read_text(encoding="utf-8")
    html = (src.replace("<head>", "<head>" + AUDIT_HEAD)
               .replace("</body>", AUDIT_BODY))
    tmp = DEMO / "_probe_tmp.html"
    tmp.write_text(html, encoding="utf-8")
    try:
        url = f"file:///{str(tmp).replace(chr(92), '/')}{query}"
        res = subprocess.run(
            [str(CHROME), "--headless=new", "--disable-gpu", "--window-size=1440,900",
             f"--user-data-dir={PROFILE}", "--virtual-time-budget=20000", "--dump-dom", url],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        m = re.search(r"<title>RESULT\|(.*?)</title>", res.stdout, re.S)
        return m.group(1) if m else "（没拿到结果）"
    finally:
        tmp.unlink(missing_ok=True)


print("---------- 全站回归 ----------")
worst = 0
for page, q in PAGES:
    out = audit(page, q)
    n = int(re.search(r"ERR=(\d+)", out).group(1)) if "ERR=" in out else -1
    worst = max(worst, n)
    print(f"{'[BAD]' if n else '[ok ]'} {page}{q}\n    {out}")
print(f"\n最差的一页错误数 = {worst}")


# ---------- 跨页金额一致性：结算页报的价，订单页必须一样 ----------
# 这一步要两次加载，用同一个 profile 传递 localStorage：
# 先在结算页选「到店自取」并提交，再看订单页显示多少。
WARM_SUBMIT = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(() => {
    const r = document.querySelector('input[name="mode"][value="pickup"]');
    r.checked = true;
    r.dispatchEvent(new Event('change', { bubbles: true }));
    // 结算页跳走之后 window 就换了，只能把数字留在 localStorage 里带过河
    localStorage.setItem('__probe_checkout_total',
      [...document.querySelectorAll('.summary-row.is-total .v')].pop().textContent);
    // submit() 同步写 localStorage，之后才跳转，所以 click() 返回时数据已经落盘
    document.getElementById('submit').click();
  }, 300);
});
</script>
</body>"""

GRAB_NO = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(() => {
    const o = JSON.parse(localStorage.getItem('sk_orders_v1') || '[]')[0];
    document.title = 'RESULT|' + (o ? o.no : '');
  }, 300);
});
</script>
</body>"""

READ_DETAIL = """
<script>
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(() => {
    // 结算页报价 —— 跨页面跳转只能靠 localStorage 带过来
    const quoted = localStorage.getItem('__probe_checkout_total');
    const o = JSON.parse(localStorage.getItem('sk_orders_v1') || '[]')[0];
    // 详情页右侧「实付」那一格
    const rows = [...document.querySelectorAll('.summary-card .summary-row')];
    const paidRow = rows.find(r => r.textContent.includes('实 付'));
    const paid = paidRow ? paidRow.querySelector('.v').textContent : '（没渲染出来）';
    const hasDiscount = [...document.querySelectorAll('.summary-card .summary-row')]
      .some(r => r.textContent.includes('自提立减'));
    const sub = o.items.reduce((s, it) => {
      const d = SK.dishById(it.id); if (!d) return s;
      const sp = d.specs[it.specIndex || 0] || d.specs[0];
      return s + (d.price + (sp?.delta || 0)) * it.qty;
    }, 0);
    document.title = 'RESULT|结算页报价=' + quoted + '　订单详情实付=' + paid
      + '　一致=' + (quoted === paid) + '　自提立减行=' + hasDiscount
      + '　（不收服务费，自取按小计减 2%、最多 4 元；'
      + '演示单 ¥' + sub + '，自提立减应为 ¥'
      + (Math.round(Math.min(4, sub * 0.02) * 100) / 100) + '）';
  }, 300);
});
</script>
</body>"""

print("---------- 跨页金额一致性 ----------")


def load(page, probe_body, query=""):
    src = (DEMO / page).read_text(encoding="utf-8")
    tmp = DEMO / "_probe_tmp.html"
    tmp.write_text(src.replace("</body>", probe_body), encoding="utf-8")
    try:
        res = subprocess.run(
            [str(CHROME), "--headless=new", "--disable-gpu", "--window-size=1440,900",
             f"--user-data-dir={PROFILE}", "--virtual-time-budget=20000", "--dump-dom",
             f"file:///{str(tmp).replace(chr(92), '/')}{query}"],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        m = re.search(r"<title>RESULT\|(.*?)</title>", res.stdout, re.S)
        return m.group(1) if m else "（没拿到结果）"
    finally:
        tmp.unlink(missing_ok=True)


# 结算页带 ?demo=1 才会填一份示例点单
load("checkout.html", WARM_SUBMIT, "?demo=1")
no = load("order.html", GRAB_NO)
print(f"    在结算页选「到店自取」并提交 → {load('order.html', READ_DETAIL, f'?no={no}')}")
