/* 食刻点餐 · 页头与页脚
 *
 * 这个 Demo 有 11 个页面，页头页脚长得一样。这里生成一次，
 * 各页面只写 <header data-topbar></header> 和 <footer data-footer></footer>。
 * 接 Vue 之后这两块本来就会变成组件，所以没必要现在复制 11 份。
 */

const SKLayout = (() => {
  'use strict';

  const NAV = [
    { href: 'index.html',     text: '首页',     icon: 'mdi:home-outline' },
    { href: 'menu.html',      text: '点餐',     icon: 'mdi:silverware-fork-knife' },
    { href: 'recommend.html', text: '推荐实验室', icon: 'mdi:star' },
    { href: 'order.html',     text: '我的订单', icon: 'mdi:clipboard-list-outline' },
    { href: 'admin.html',     text: '商家后台', icon: 'mdi:view-dashboard-outline' }
  ];

  /* 页头页脚是 app.js 之外唯一往 HTML 里拼用户输入的地方（用户名、备注），
     自己带一份转义，免得依赖 SK 的加载顺序。 */
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /* 登录态由 app.js 的 loadProfile() 在脚本求值时写进 USER_PROFILE，
     layout 在 DOMContentLoaded 上才跑，所以这里读到的已经是合并过的画像。 */
  const loggedIn = () => window.USER_PROFILE?.loggedIn === true;
  const userName = () => window.USER_PROFILE?.user?.name || '我的';

  function topbar() {
    return `
    <a class="skip sr-only" href="#main">跳到主要内容</a>
    <div class="wrap-wide">
      <a class="brand" href="index.html">
        食<span class="brand-mark">刻</span>
        <small>SHI KE KITCHEN</small>
      </a>

      <nav class="mainnav" aria-label="主导航">
        ${NAV.map((n) => `<a href="${n.href}">${n.text}</a>`).join('')}
      </nav>

      <form class="topbar-search" role="search" data-search>
        <iconify-icon icon="mdi:magnify" width="16" height="16"></iconify-icon>
        <label class="sr-only" for="q">搜索菜品</label>
        <input id="q" name="q" type="search" placeholder="搜菜名，比如「小笼包」" autocomplete="off">
      </form>

      <div class="topbar-actions">
        <a class="icon-btn" href="${loggedIn() ? 'profile.html' : 'login.html'}"
           title="${loggedIn() ? esc(userName()) : '登录 / 注册'}">
          <iconify-icon icon="${loggedIn() ? 'mdi:account-circle-outline' : 'mdi:account-outline'}"
                        width="18" height="18"></iconify-icon>
          <span class="nav-text">${loggedIn() ? esc(userName()) : '登录'}</span>
        </a>
        <a class="icon-btn cart-badge" href="cart.html">
          <iconify-icon icon="mdi:receipt-text-outline" width="18" height="18"></iconify-icon>
          <span class="nav-text">点单</span>
          <span class="badge" data-count="0">0</span>
        </a>
      </div>
    </div>`;
  }

  function footer() {
    const year = new Date().getFullYear();
    return `
    <div class="wrap-wide">
      <div class="foot-grid">
        <div>
          <div class="foot-brand">食刻 · SHI KE</div>
          <p style="margin:0 0 10px;max-width:34ch">
            现做现出的中式小馆，蒸点、小炒、烧烤、火锅一日三餐都做。
          </p>
          <p style="margin:0">${window.SHOP.address}</p>
          <p style="margin:6px 0 0">${window.SHOP.hours}</p>
        </div>
        <div>
          <h2>点餐</h2>
          <ul>
            <li><a href="menu.html">全部菜单</a></li>
            <li><a href="menu.html#signature">招牌推荐</a></li>
            <li><a href="recommend.html">为我推荐</a></li>
            <li><a href="cart.html">我的点单</a></li>
          </ul>
        </div>
        <div>
          <h2>我的</h2>
          <ul>
            <li><a href="profile.html">个人中心</a></li>
            <li><a href="order.html">订单记录</a></li>
            <li><a href="login.html">登录 / 注册</a></li>
          </ul>
        </div>
        <div>
          <h2>门店</h2>
          <ul>
            <li><a href="kitchen.html">后厨出单屏</a></li>
            <li><a href="admin.html">商家后台</a></li>
            <li><a href="tel:${window.SHOP.phone.replace(/\s/g, '')}">${window.SHOP.phone}</a></li>
          </ul>
        </div>
      </div>
      <div class="foot-bottom">
        <span>© ${year} 食刻点餐 · 本科毕业设计演示作品</span>
        <span>图片来自 Pexels，插画来自 unDraw，图标来自 Iconify</span>
      </div>
    </div>`;
  }

  function mount() {
    const t = document.querySelector('[data-topbar]');
    if (t) { t.className = 'topbar'; t.innerHTML = topbar(); }

    const f = document.querySelector('[data-footer]');
    if (f) { f.className = 'site-foot'; f.innerHTML = footer(); }

    // 顶栏搜索统一跳到菜单页，把关键词带过去
    const form = document.querySelector('[data-search]');
    if (form) {
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        const q = form.querySelector('input').value.trim();
        location.href = q ? `menu.html?q=${encodeURIComponent(q)}` : 'menu.html';
      });
    }
  }

  document.addEventListener('DOMContentLoaded', mount);
  return { topbar, footer, mount };
})();

window.SKLayout = SKLayout;
