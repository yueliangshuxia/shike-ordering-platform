/* 食刻点餐 · 公共逻辑
 *
 * 这个 Demo 是纯静态页，没有后端。购物车状态放在 localStorage，
 * 各页面之间靠它串起来。接后端时把 SK.cart 的读写换成接口调用即可，
 * 其余渲染逻辑不用动。
 */

const SK = (() => {
  'use strict';

  /* ---------- 1. 图标 ---------- */
  /* 图标数据是预先从 Iconify API 取回来的 mdi 集合，已存成本地 JS 全局，
     用 addCollection 注册后组件不再走网络，断网也能正常显示。
     这里不用 fetch 读 JSON，是为了让页面在 file:// 下双击打开也能用。 */
  function loadIcons() {
    const data = window.SK_ICON_BUNDLE;
    if (!data) {
      console.warn('图标包未加载，请检查 assets/icons/bundle.js 是否引入');
      return;
    }
    // iconify-icon 把 addCollection 挂在自定义元素类和原型上，没有全局 Iconify
    const el = window.customElements?.get('iconify-icon');
    if (el?.addCollection) el.addCollection(data);
    else if (window.IconifyIcon?.addCollection) window.IconifyIcon.addCollection(data);
    else console.warn('iconify-icon 组件未就绪，图标可能不显示');
  }

  /* 生成一个图标标签。用法：ic('mdi:fire', 18) */
  function ic(name, size = 18, cls = '') {
    return `<iconify-icon icon="${name}" width="${size}" height="${size}"${cls ? ` class="${cls}"` : ''}></iconify-icon>`;
  }

  /* ---------- 2. 工具 ---------- */
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const money = (n) => Number(n).toFixed(Number.isInteger(Number(n)) ? 0 : 2);

  const dishById = (id) => (window.DISHES || []).find((d) => d.id === id);

  /* 有些菜品暂时没有实拍图。与其塞一张随机风景照（一眼看穿是假的），
     不如生成一张带菜名的占位图块 —— 看着是有意为之的空白，
     也直接告诉后来接手的人这里要换哪道菜的图。 */
  function ph(name) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 450">
<rect width="600" height="450" fill="#E9E8E1"/>
<rect x="17" y="17" width="566" height="416" fill="none" stroke="#C7C6BD" stroke-width="2" stroke-dasharray="11 9"/>
<circle cx="300" cy="186" r="34" fill="none" stroke="#C7C6BD" stroke-width="2"/>
<path d="M288 186h24M300 174v24" stroke="#C7C6BD" stroke-width="2" stroke-linecap="round"/>
<text x="300" y="262" text-anchor="middle" font-family="Noto Serif SC, SimSun, serif" font-size="40" fill="#4A5B54">${name}</text>
<text x="300" y="300" text-anchor="middle" font-family="Noto Sans SC, Microsoft YaHei, sans-serif" font-size="18" fill="#8A9691">占位图 · 待换成实拍</text>
</svg>`;
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }

  /* 取菜品图：有实拍用实拍，没有就生成占位图块 */
  const img = (d) => d.img || ph(d.name);

  const catById = (id) => (window.CATEGORIES || []).find((c) => c.id === id);

  /* 取某个分类下的菜。注意「招牌推荐」是虚拟分类 —— data.js 里没有
     cat='signature' 的菜，招牌是打在菜品上的 tag。菜单页和首页都要按
     同一套规则取，所以放这儿，别各写一份各错一份。 */
  function dishesOf(cat, list) {
    const src = list || window.DISHES || [];
    return cat.id === 'signature'
      ? src.filter((d) => d.tags.includes('招牌'))
      : src.filter((d) => d.cat === cat.id);
  }

  /* 辣度：用辣椒个数表示，比写「中辣」直观 */
  function spiceTag(level) {
    if (!level) return '';
    const label = ['', '微辣', '中辣', '特辣'][level] || '';
    return `<span class="tag tag-hot">${ic('mdi:chili-hot', 12)}${label}</span>`;
  }

  function tagOf(t) {
    const map = { 招牌: 'tag-sign', 素食: 'tag-veg', 冰饮: 'tag-cold', 现做: 'tag' };
    const icon = { 招牌: 'mdi:star', 素食: 'mdi:leaf', 冰饮: 'mdi:snowflake', 现做: 'mdi:chef-hat' }[t] || 'mdi:check-circle';
    return `<span class="tag ${map[t] || 'tag'}">${ic(icon, 12)}${t}</span>`;
  }

  /* ---------- 3. 购物车 ---------- */
  const CART_KEY = 'sk_cart_v1';
  const TABLE_KEY = 'sk_table_v1';

  /* 购物车每一行：{ dishId, specIndex, qty }
     同一道菜选不同规格算两行，所以行的唯一键是 dishId + specIndex */
  let cart = { lines: [] };

  function lineKey(l) { return `${l.dishId}#${l.specIndex}`; }

  function unitPrice(line) {
    const d = dishById(line.dishId);
    if (!d) return 0;
    const spec = d.specs[line.specIndex] || d.specs[0];
    return d.price + (spec?.delta || 0);
  }

  function load() {
    try {
      const raw = localStorage.getItem(CART_KEY);
      if (raw) cart = JSON.parse(raw);
      if (!Array.isArray(cart.lines)) cart = { lines: [] };
    } catch { cart = { lines: [] }; }
  }

  function save() {
    try { localStorage.setItem(CART_KEY, JSON.stringify(cart)); } catch { /* 隐私模式忽略 */ }
    document.dispatchEvent(new CustomEvent('sk:cart-change'));
  }

  function add(dishId, specIndex = 0, qty = 1) {
    const d = dishById(dishId);
    if (!d || d.stock === 0) return false;
    const key = `${dishId}#${specIndex}`;
    const found = cart.lines.find((l) => lineKey(l) === key);
    if (found) found.qty += qty;
    else cart.lines.push({ dishId, specIndex, qty });
    save();
    return true;
  }

  function setQty(dishId, specIndex, qty) {
    const key = `${dishId}#${specIndex}`;
    const i = cart.lines.findIndex((l) => lineKey(l) === key);
    if (i < 0) return;
    if (qty <= 0) cart.lines.splice(i, 1);
    else cart.lines[i].qty = qty;
    save();
  }

  function removeLine(dishId, specIndex) { setQty(dishId, specIndex, 0); }

  function clear() { cart.lines = []; save(); }

  const count = () => cart.lines.reduce((s, l) => s + l.qty, 0);
  const subtotal = () => cart.lines.reduce((s, l) => s + unitPrice(l) * l.qty, 0);

  /* 桌号：下单页可以改，改完写回 localStorage */
  function getTable() { return localStorage.getItem(TABLE_KEY) || window.USER_PROFILE?.table || 'A12'; }
  function setTable(t) { localStorage.setItem(TABLE_KEY, t); renderMobileBar(); }

  /* ---------- 4. 提示条 ---------- */
  let toastEl = null;
  let toastTimer = null;
  function toast(msg, iconName = 'mdi:check-circle') {
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.className = 'toast';
      toastEl.setAttribute('role', 'status');
      document.body.appendChild(toastEl);
    }
    toastEl.innerHTML = `<span class="toast-icon">${ic(iconName, 18)}</span><span>${esc(msg)}</span>`;
    toastEl.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.remove('is-on'), 2000);
  }

  /* ---------- 5. 顶栏 ---------- */
  function renderTopbar() {
    // 高亮当前页
    const here = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.mainnav a').forEach((a) => {
      if (a.getAttribute('href') === here) a.setAttribute('aria-current', 'page');
    });
    updateBadge();
  }

  function updateBadge() {
    const n = count();
    document.querySelectorAll('.cart-badge .badge').forEach((b) => {
      b.dataset.count = String(n);
      b.textContent = n > 99 ? '99+' : String(n);
    });
  }

  /* ---------- 6. 菜品卡片 ---------- */
  function dishCard(d) {
    const sold = d.stock === 0;
    const flag = sold
      ? '<span class="dish-flag is-sold">今日售罄</span>'
      : (d.tags.includes('招牌') ? '<span class="dish-flag">招牌</span>' : '');
    return `
      <article class="dish" data-dish="${d.id}">
        <a class="dish-figure" href="dish.html?id=${d.id}" aria-label="查看 ${esc(d.name)} 详情">
          <img src="${img(d)}" alt="${esc(d.name)}" loading="lazy">
          ${flag}
        </a>
        <div class="dish-body">
          <h3 class="dish-name"><a href="dish.html?id=${d.id}">${esc(d.name)}</a></h3>
          <p class="dish-desc">${esc(d.desc)}</p>
          <div class="dish-tags">
            ${spiceTag(d.spice)}
            ${d.tags.filter((t) => t !== '招牌').map(tagOf).join('')}
          </div>
          <div class="dish-foot">
            <span class="price"><span class="cur">¥</span>${money(d.price)}</span>
            <span class="sold">月售 ${d.sold}</span>
            <button class="add-btn" type="button" data-add="${d.id}"
                    ${sold ? 'disabled' : ''}
                    aria-label="${sold ? '已售罄' : `加入点单：${esc(d.name)}`}">
              ${ic(sold ? 'mdi:close' : 'mdi:plus', 18)}
            </button>
          </div>
        </div>
      </article>`;
  }

  /* 卡片上的加号统一在这里代理，省得每个页面各写一遍 */
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-add]');
    if (!btn || btn.disabled) return;
    e.preventDefault();
    const id = btn.dataset.add;
    const d = dishById(id);
    if (add(id, 0, 1)) {
      pressStamp();
      toast(`已加入点单：${d.name}`, 'mdi:cart-outline');
    }
  });

  /* 点单栏盖章动效：只在加购这一个动作上做，别处不撒动效 */
  function pressStamp() {
    const s = document.querySelector('.stamp');
    if (!s) return;
    s.classList.remove('is-pressed');
    void s.offsetWidth;   // 强制重排，否则重复加购不会重播动画
    s.classList.add('is-pressed');
  }

  /* ---------- 7. 点单栏（点心单） ---------- */
  function renderOrderRail(mount) {
    const el = typeof mount === 'string' ? document.querySelector(mount) : mount;
    if (!el) return;

    const lines = cart.lines;

    if (!lines.length) {
      el.innerHTML = `
        <div class="order-rail-head">
          <h2>点 单</h2>
          <span class="table-no">${esc(getTable())} 桌</span>
        </div>
        <div class="order-empty">
          <img src="assets/illustrations/add_to_cart_vkjp.svg" alt="">
          <p>还没点菜。左侧菜单里点「+」就加进来了。</p>
        </div>`;
      return;
    }

    const rows = lines.map((l) => {
      const d = dishById(l.dishId);
      if (!d) return '';
      const spec = d.specs[l.specIndex] || d.specs[0];
      const unit = unitPrice(l);
      return `
        <div class="order-line">
          <div class="order-line-name">
            ${esc(d.name)}
            <span class="order-line-spec">${esc(spec?.name || '标准')}</span>
          </div>
          <span class="order-line-price">¥${money(unit * l.qty)}</span>
          <div class="qty-box">
            <button type="button" data-dec="${l.dishId}" data-spec="${l.specIndex}" aria-label="减少 ${esc(d.name)}">${ic('mdi:minus', 14)}</button>
            <span class="qty">${l.qty}</span>
            <button type="button" data-inc="${l.dishId}" data-spec="${l.specIndex}" aria-label="增加 ${esc(d.name)}">${ic('mdi:plus', 14)}</button>
          </div>
        </div>`;
    }).join('');

    const total = subtotal();

    el.innerHTML = `
      <div class="stamp" aria-hidden="true">
        已下单
        <span class="stamp-no">${esc(getTable())}</span>
      </div>
      <div class="order-rail-head">
        <h2>点 单</h2>
        <span class="table-no">${esc(getTable())} 桌</span>
      </div>
      <div class="order-rail-body">${rows}</div>
      <div class="order-rail-foot">
        <div class="sub-row"><span>共 ${count()} 件</span><span>餐位费 ¥0</span></div>
        <div class="total-row">
          <span class="total-label">合 计</span>
          <span class="total-value">¥${money(total)}</span>
        </div>
        <a class="btn btn-primary btn-block btn-lg" href="checkout.html" style="margin-top:12px">
          ${ic('mdi:receipt-text-outline', 19)}去结算
        </a>
      </div>`;
  }

  /* 点单栏里的加减代理 */
  document.addEventListener('click', (e) => {
    const inc = e.target.closest('[data-inc]');
    const dec = e.target.closest('[data-dec]');
    if (inc) {
      const d = dishById(inc.dataset.inc);
      const spec = Number(inc.dataset.spec);
      const line = cart.lines.find((l) => lineKey(l) === `${d.id}#${spec}`);
      if (line) { line.qty += 1; save(); }
    } else if (dec) {
      const d = dishById(dec.dataset.dec);
      const spec = Number(dec.dataset.spec);
      const line = cart.lines.find((l) => lineKey(l) === `${d.id}#${spec}`);
      if (line) setQty(d.id, spec, line.qty - 1);
    }
  });

  /* ---------- 8. 移动端底部结算条 ---------- */
  function renderMobileBar() {
    const bar = document.querySelector('.mobile-bar');
    if (!bar) return;
    bar.innerHTML = `
      <div class="mb-cart cart-badge">
        ${ic('mdi:cart-outline', 27)}
        <span class="badge" data-count="${count()}">${count()}</span>
      </div>
      <div class="mb-total">
        <span class="mb-label">合计</span>
        <div class="mb-value">¥${money(subtotal())}</div>
      </div>
      <a class="btn btn-primary" href="${count() ? 'checkout.html' : 'menu.html'}">
        ${count() ? '去结算' : '去点菜'}
      </a>`;
  }

  /* ---------- 9. 分类竖栏滚动联动 ---------- */
  /* 每次重画菜品区之后都要重来一遍：旧的 section 已经不在 DOM 里了，
     继续观察它们不会报错，只是再也不亮。所以先断开上一次的观察者。
     menu.html 筛选后会重建 section，必须再调一次这个函数。 */
  let catRailObs = null;
  function initCatRail() {
    if (catRailObs) { catRailObs.disconnect(); catRailObs = null; }
    if (!('IntersectionObserver' in window)) return;

    const links = [...document.querySelectorAll('.cat-rail a[href^="#"]')];
    const sections = links
      .map((a) => document.getElementById(a.getAttribute('href').slice(1)))
      .filter(Boolean);
    if (!sections.length) return;

    catRailObs = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting) return;
        links.forEach((a) => a.classList.toggle('is-active', a.getAttribute('href') === `#${en.target.id}`));
      });
    }, { rootMargin: '-120px 0px -70% 0px', threshold: 0 });

    sections.forEach((s) => catRailObs.observe(s));
  }

  /* ---------- 10. 用户画像：把本地存的偏好和登录态并进 USER_PROFILE ---------- */
  /* 个人中心改了口味偏好、登录页登录过，都要让别的页面立刻读到。
     USER_PROFILE 是各页面推荐的唯一输入，所以在这里合并一次，
     其余代码只管读 USER_PROFILE，不用各自去翻 localStorage。
     这份在脚本被求值时就跑（不等 DOMContentLoaded），
     因为 layout.js 挂页头时要读登录态决定显示「登录」还是用户名。 */
  const PREF_KEY = 'sk_pref_v1';
  const USER_KEY = 'sk_user_v1';

  function loadProfile() {
    const P = window.USER_PROFILE;
    if (!P) return;
    try {
      const pref = JSON.parse(localStorage.getItem(PREF_KEY) || 'null');
      if (pref) {
        if (Array.isArray(pref.preferCats)) P.preferCats = pref.preferCats;
        if (typeof pref.maxSpice === 'number') P.maxSpice = pref.maxSpice;
        if (Array.isArray(pref.avoid)) P.avoid = pref.avoid;
        P.avoidSpice = P.maxSpice === 0;
      }
      // 没登录过就用 data.js 里的演示画像
      const u = JSON.parse(localStorage.getItem(USER_KEY) || 'null');
      if (u?.name) {
        P.user = { ...P.user, name: u.name, phone: u.phone || P.user.phone };
        P.loggedIn = true;
      }
    } catch { /* 本地数据坏了就退回 data.js 的默认画像，不阻断页面 */ }
  }

  /* ---------- 11. 初始化 ---------- */

  /* 静态 Demo 没有后端，空购物车时很多页面只能看到空状态。
     带 ?demo=1 打开就塞一份示例点单，方便演示和看设计。 */
  function seedDemoCart() {
    if (!new URLSearchParams(location.search).has('demo')) return;
    if (cart.lines.length) return;
    [['D3001', 1, 4], ['D2001', 1, 1], ['D5001', 0, 2], ['D7001', 0, 2], ['D8001', 0, 1]]
      .forEach(([id, spec, qty]) => add(id, spec, qty));
  }

  function init() {
    load();
    seedDemoCart();
    renderTopbar();
    renderMobileBar();
    renderOrderRail('#order-rail');
    initCatRail();

    // 任何一处改动购物车，这几块都要跟着重画
    document.addEventListener('sk:cart-change', () => {
      updateBadge();
      renderOrderRail('#order-rail');
      renderMobileBar();
    });
  }

  loadProfile();

  document.addEventListener('DOMContentLoaded', () => {
    loadIcons();
    init();
  });

  return {
    ic, esc, money, ph, img, dishById, catById, dishesOf, spiceTag, tagOf, dishCard,
    add, setQty, removeLine, clear, count, subtotal, getTable, setTable,
    toast, renderOrderRail, pressStamp, loadIcons, loadProfile, initCatRail,
    get cart() { return cart; },
    unitPrice, lineKey
  };
})();
