# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

本科毕业设计：基于 Web 的点餐系统网页平台。
拟定题目「基于 AHP-TOPSIS 与历史订单数据的个性化菜品推荐方法及系统实现」，
选题论证、算法推导、七章大纲见 `毕设选题与论文大纲.md`。

**这家店是港式茶餐厅**（厦门，店名「食刻」）。业态决定了菜单长什么样：
早茶点心、粥粉面、碟头小炒、糖水、丝袜奶茶 —— 茶餐厅的菜单本来就是杂的，
所以它同时卖虾饺和麻婆豆腐并不违和。**但不卖烧烤和火锅**，
这两样在真实世界里不属于茶餐厅，也是之前那版菜单最「编」的地方。
2026-10-17 从「什么都做的小馆」收敛成这个业态，原因见 `设计确认清单.md` 第 4 条。

仓库里有四个并存的目录，各自是什么别搞混：

| 目录 | 是什么 | 状态 |
|---|---|---|
| `demo/` | 纯静态前端 Demo，给用户确认设计用的 | 11 个页面已完工，回归 + 无障碍 + 响应式三个脚本全绿 |
| `server/` | 正式后端（Spring Boot 3.5 + Java 21 + MySQL 8） | **接口已全部打通**，31 单测 + 59 项端到端自测全绿 |
| `web/` | 正式前端（Vue 3 + Vite + Pinia + Element Plus + ECharts） | **13 个路由全部打通**，页面自测全绿 |
| `release/` | 发布包（免安装版 / 演示版 / 源码 + 说明） | 2026-10-22 生成并实测可跑，见「发布包」 |

`web/` 不是把 `demo/` 重写一遍 —— 它**复用 demo 的 `style.css` 和类名**，
所以两边长得一样。改视觉请改 `style.css`，然后**同步到 `web/src/assets/style.css`**（那是一份拷贝）。

`server/` 和 `demo/` 不是"新旧两版"，是同一套东西的两个阶段：
`demo/assets/js/recommend.js` 与 `server/.../algorithm/` 是同一套算法的两种语言实现，
**数值必须逐位一致**，改动时两边要一起改（有测试盯着，见「后端架构」）。

用户的工作方式是**先给推荐项、再问要不要改**，不是列一堆选项让他挑。详见 `设计确认清单.md`。

## 项目现在做到哪了

**代码这条线已经收尾了**（2026-10-17）。前后端全通、六层验证全绿、
文档和术语表齐了。**接下来是论文，不是更多的功能。**

2026-10-22 又补了两件收尾的事：**20 道菜全部有了实拍图**，
以及**发布了三个包**（免安装版 / 演示版 / 源码），实测解压双击就能跑。
这两件都是交付物层面的，代码逻辑没动。

用户的情况：物联网工程专业（上过 Java 课）、导师没有硬性要求、
**答辩在 2027 年，时间充裕**。所以他**不着急**，别催进度，
也别自作主张加需求 —— 加功能前先问。

按价值排序的待办：

1. **论文第 6 章的实验**（权重敏感性分析、与等权重 TOPSIS 的对比、冷启动效果）——
   这是现在最该动手的，而且**只有用户自己做得了**，别替他编数据
2. **论文第 5 章的截图** —— 系统现在的样子就是最终的样子，趁早截
3. **接口文档** —— 第 5 章可以直接照着写，目前还没有

`CONTEXT.md` 是术语表，和这份文件的分工不同：
**这份讲「怎么改代码」，那份讲「这些词是什么意思」**（写论文和答辩时用）。

## 两份数据源的关系（改菜单前必读）

`demo/assets/js/data.js` 是**唯一**的菜品数据源。后端演示数据是它的派生物：

```
demo/assets/js/data.js  ──(node tools/gen_seed.mjs)──►  server/.../db/data.sql
        │
        └── 静态 Demo 直接读它

demo/assets/js/recommend.js  ◄──数值必须逐位一致──►  server/.../algorithm/
```

改菜单、改价格、改食材的正确做法是：

```
改 data.js → node tools/gen_seed.mjs → 删库 → 重启后端
```

**「删库」那一步不能省**，即使只是改了菜名。种子 SQL 全是 `INSERT IGNORE`，
它只负责「没有就插」，**不会删掉你已经从 data.js 里去掉的菜** ——
不删库的话旧菜会一直留在菜单上，而且看上去很正常。
删库命令见下面「改 schema 或换菜单之后必须重建库」。

不要手改 `data.sql`（文件头也写了不要手改），否则 demo 和后端会漂成两份数据，
推荐结果对不上，论文里的实验数据也就说不清是哪一份。

同理，`recommend.js` 和后端算法包是同一套算法的两种语言实现，
`AhpCalculatorTest` 锁死了 λmax=5.084 / CI=0.0210 / CR=0.0188，
改一边不另一边测试立刻红。**忌口的别名表和形近食材名单两边各有一份，
必须一起改**（`CriterionScorer.AVOID_ALIASES` ↔ `recommend.js` 的 `AVOID_ALIAS`）。

### 换菜单时别漏掉的那个常量

`CriterionScorer.SOLD_BENCHMARK`（当前 **1700**）是热度准则里销量归一化的基准，
**取值必须跟着菜单里最高那道菜的销量走**，否则热度分整体偏低或者集体顶格，
这个准则就失去区分度了。

**漏改过一回**：菜单从「什么都做的小馆」（最高月销 4216）收敛成茶餐厅
（最高 1673）时，基准值还停在 4300，于是所有菜的热度分被压到 60 分上下，
这个准则就失去了区分度 —— 而排序看起来仍然「正常」，不特意检查发现不了。

还有一个容易踩的边界：**销量超过基准值的菜会被 `min(100, ...)` 封顶**，
封顶之后开方压缩的比值就不再是 2 倍了（实测 4000/1000 会得到 1.57 而不是 2.0）。
所以 `CriterionScorerTest` 里那对测试数据**必须落在基准值以下**，
否则测的就不是开方压缩了。`aboveBenchmarkIsCappedNotInverted` 另外盯着
封顶只能「打平」、不能出现倒挂。

### 改 schema 或换菜单之后必须重建库

`schema.sql` 全是 `IF NOT EXISTS`、`data.sql` 全是 `INSERT IGNORE`，
所以**加了一列不会自动加上，删掉的菜也不会自动消失**。改完这两样要：

```bash
mysql -u root -p123456 -e "DROP DATABASE IF EXISTS shike_ordering;"
```

重启即可，会自动重建并灌数据。演示数据全是种子生成的，删了不心疼 ——
但**真跑起来录了演示数据之后再删就丢了**，那之前先导一份。

## 命令

### 静态 Demo

没有构建步骤。`demo/` 是纯静态页面，**双击 `index.html` 即可运行**。

`tools/` 下的脚本都从仓库根目录运行：

```bash
python tools/shot.py index.html                      # 给页面截图到 tools/_shots/
python tools/shot.py "cart.html?demo=1" --w 1440 --h 1200   # 可带查询串
python tools/fetch_icons.py                          # 扫描全站图标引用，重新生成图标包
python tools/recolor_illustrations.py                # 按调色板给 unDraw 插画重新配色
python tools/contact_sheet.py                        # 把菜品照片拼成校对图，核对内容
python tools/_verify.py                              # 逻辑回归自测：见下
python tools/_audit.py                               # 无障碍与结构巡检（alt/可访问名/重复id/标题层级/对比度）
python tools/_resp.py                                # 响应式巡检（6 个宽度 × 10 个页面的横向溢出）
node tools/gen_seed.mjs                              # 由 demo 的假数据重生成后端种子 SQL：见下
```

前端没有测试框架，这三个脚本就是全部的防线。`_verify.py` 做逻辑回归 ——
往页面副本里注入探针，用无头 Chrome 跑，把结果写进 `document.title` 再 `--dump-dom` 读回来。
覆盖 cart 加减步长、分类竖栏观察者、权重滑杆、偏好/登录态跨页、忌口是否生效、
结算页与订单页的金额一致性，最后对 14 个页面变体做一遍 JS 报错和坏图的巡检。
`_audit.py` 查无障碍与结构，`_resp.py` 查响应式溢出。**三个都要保持全绿。**

**写这类探针时的坑**（都踩过了）：
- 页面里的 `<script>` 一律用绝对路径拼 `file:///`，相对路径会得到 `ERR_FILE_NOT_FOUND`。
- 想要探针写的 `localStorage` 被读到，得把种子脚本插在第一个 `<script src>` **之前** ——
  `loadProfile()` 在脚本求值时就读完了，插在 `</body>` 前已经晚了。
- 无头 Chrome + `--virtual-time-budget` 下 **`requestAnimationFrame` 一次都不触发**，
  `IntersectionObserver` 的回调也时有时无。所以别拿"滚动后有没有高亮"当判据 ——
  会假阳性。改成用 `IntersectionObserver` 包一层，直接断言"观察者有没有被挂到 section 上"，
  这个不依赖出帧，结果是确定的。
- 探针用的 Chrome profile 要和 `shot.py` 分开（`tools/_verify_profile`），
  否则探针塞进 `localStorage` 的登录态会出现在截图里。
- 控制台是 GBK，跑之前设 `PYTHONIOENCODING=utf-8`，并且别往 print 里放 ✓/✗。

### 后端（`server/`，Spring Boot 3.5.16 + Java 21 + MySQL 8）

**这台机器没装 Maven**，仓库里自带了一份，用它：

```bash
cd server && ../.tooling/apache-maven-3.9.9/bin/mvn -B clean test   # 全部单测（31 个，当前全绿）
cd server && ../.tooling/apache-maven-3.9.9/bin/mvn -B test -Dtest=AhpCalculatorTest
```

**启动后端一律用仓库里的脚本，不要用 `mvn spring-boot:run`：**

```bash
bash tools/run_server.sh                    # 编译 + 解析依赖 + 起服务
bash tools/run_server.sh --server.port=9090 # 透传给 Spring Boot 的参数

PYTHONIOENCODING=utf-8 python tools/smoke_api.py   # 端到端自测（59 项）
node tools/ws_check.mjs                            # 下单 → 后厨推送是否通
```

**`mvn spring-boot:run` 在这个仓库里是坏的**，实测报
`ClassNotFoundException: com.shike.ordering.OrderingApplication` —— 类明明编译出来了，
`java -cp` 直接跑也好好的，只有走这个插件不行。原因是仓库路径里有中文
（`E:\桌面\毕业设计点餐系统`），而插件给子进程构造 classpath 时走的是
MANIFEST 的 `Class-Path` 那一套，那份规范只允许 ASCII。
`run_server.sh` 绕开了插件，自己拼 classpath 用 `java` 起，顺带还省掉了每次重打 50 多 MB 的 fat jar
（打好的发布 jar 是 54MB，因为里面还塞了前端构建产物）。

**数据库**：MySQL 8.4 已安装，root 密码 `123456`（用户 2026-10-17 确认过）。
连接串里带了 `createDatabaseIfNotExist=true`，所以换台机器只要 MySQL 在跑就能自举，
不用手动建库。密码可以用 `DB_PASSWORD` 环境变量覆盖。

**建表和灌数据是自动的**：`schema.sql` 全是 `IF NOT EXISTS`，`data.sql` 全是 `INSERT IGNORE`，
每次启动重跑都安全 —— 表不会重建，管理员改过的库存和价格也不会被覆盖回去。
`data.sql` **不要手改**，它由 `node tools/gen_seed.mjs` 从 `demo/assets/js/data.js` 生成
（见「两份数据源的关系」）。

**演示账号**（`DataInitializer` 启动时补，密码用 BCrypt 现算所以不写在 SQL 里）：

| 账号 | 密码 | 角色 |
|---|---|---|
| `13800000621` | `123456` | 顾客（陈同学，带历史订单和口味画像） |
| `13900000001` | `admin123` | 管理员 |
| `13900000002` | `kitchen123` | 后厨 |

`.tooling/apache-maven-3.9.9/` 是解压出来的 Maven（约 11MB），**不属于交付物**，
但要留着 —— 换台机器没装 Maven 的话这就是唯一的构建入口。

### 前端（`web/`，Vue 3 + Vite）

```bash
cd web && npm install      # 首次
cd web && npm run dev      # 开发服务器，5173，已配好 /api 和 /ws 的代理
cd web && npm run build    # 产出 dist/
PYTHONIOENCODING=utf-8 python tools/_verify_web.py   # 13 个路由的渲染自测
```

**开发时必须前后端一起起**：Vite 把 `/api` 和 `/ws` 代理到 8080，
所以前端代码里写的都是同源路径，不用配 baseURL，也没有 CORS 问题。

**5173 被占用时先确认占着的是不是这个项目**。这台机器上另外两个毕设项目
（阅读平台、住房系统）也是 Vite 5173，而且它们会一直挂在后台。
`_verify_web.py` 全红、但每个页面都只差文字时，多半就是它在给别的站出页面 ——
`curl -s http://localhost:5173/ | grep title` 一眼就能看出来。
Vite 自己端口被占会顺延到 5174，但代理脚本写死了 5173，所以别让它顺延。

`tools/_verify_web.py` 会临时往 `web/public/` 写一张 `_devlogin.html`
（用来给无头浏览器种登录态），跑完自己删掉。**它不能跟着构建产物发布出去** ——
那张页面能从查询串里接一个令牌塞进 localStorage。要是你手动建了类似的页面，
记得用完删掉。

**环境实测**（省得重复排查）：

| 工具 | 状态 |
|---|---|
| Java | ✅ 21.0.2 |
| Node / npm | ✅ v24.14.0 / 11.9.0 |
| MySQL | ✅ 8.4.9，**已安装**，能真连 |
| Maven / Gradle | ❌ 都没有 → 用 `.tooling/` 里那份 |
| Redis | ❌ 没装 |
| Docker | ❌ 没装 |

**Redis 没装**，所以后端必须能在没有 Redis 的情况下起来（缓存走 Spring 的
`ConcurrentMapCacheManager` 兜底）。别写死 `RedisTemplate` 而不给降级方案，
否则换台机器启动就报错。

Maven Central 和 npm registry 都实测可达（HTTP 200），所以依赖能正常拉。

改完页面**建议跑一次 `shot.py` 自己看一眼**，无头 Chrome 渲染是可信的。
但注意 Windows 上无头 Chrome 有约 500px 的最小窗口宽度，`-w 414` 会按 500px 排版再裁图，
**看着像横向溢出其实没有** —— 要判断真伪得比较 `clientWidth` 和 `scrollWidth`，别只看截图。

### 发布包（`release/`）

```bash
bash tools/build_demo_package.sh     # 构建三个包，约 3-5 分钟
```

产出（结构照抄隔壁 `毕业设计阅读网站/release/`）：
`食刻点餐-免安装版.zip`（jpackage 自带运行时，不装 Java 也不装 MySQL）、
`食刻点餐-演示.zip`（可执行 jar，需要 Java 21）、
`食刻点餐-源码.zip`，外加 `说明.txt` 和一份 `论文选题与大纲.md`。

**免安装版靠的是 `application-demo.yml`**：切到内嵌 H2 文件库（`MODE=MySQL`），
所以 `schema.sql` / `data.sql` 原样沿用，不用维护第二套建表脚本。
开发时仍然走 MySQL，两个库的数据不互通。

三个容易踩的点，都写进脚本注释了：

- **`server/src/main/resources/static` 是构建产物**，脚本拷完 `web/dist` 打完 jar 后会删掉它。
  留着它下次打包会带上上一版前端，改了 Vue 却没重新拷 —— 构建日志完全看不出来。
- **jpackage 产物改名在 Windows 上会被杀毒软件短暂占用**，`mv` 报 `Permission denied`。
  脚本里重试 5 次，这不是权限配置问题，别去改 ACL。
- **H2 里 `day` 是保留字**，`OrderMapper.revenueByDay` 的别名必须写成 `` AS `day` ``。
  反引号 MySQL 和 H2 都认，这是两边唯一需要特殊处理的一处 SQL。

打进 jar 的 Vue 用 history 路由，`SpaController` 把不带点的路径 forward 到 `index.html`；
`/api` 和 `/ws` 排除在外，否则调错接口会收到一张 HTML 而不是 404。
安全配置里放行「`/api` 以外的所有路径」，页面公开、接口才受保护。

## 前端 Demo 架构（`demo/`）

### 无框架、无构建

11 个独立的 HTML 文件，各自内联 `<script>` 写本页逻辑；跨页共用的部分放在 `assets/js/` 下，
以**全局变量**而非模块方式导出：`data.js` 挂 `window.SHOP/CATEGORIES/DISHES/ORDERS/...`，
`app.js` 定义 `SK`，`recommend.js` 定义 `SKR`，`layout.js` 定义 `SKLayout`。
没有 import/export，也没有打包器。

### 脚本顺序是硬的

每个页面的 `<script>` 必须按这个顺序：

```
iconify-icon.min.js → icons/bundle.js → data.js → layout.js → [recommend.js] → app.js
```

`layout.js` 必须在 `app.js` 之前：两者都挂 `DOMContentLoaded`，layout 负责把页头页脚注入
`[data-topbar]` / `[data-footer]`，而 `app.js` 的 `init()` 要往刚注入的顶栏里渲染购物车角标。
顺序反了角标就是空的。

`kitchen.html` 是例外 —— 后厨屏是挂在墙上的独立显示，**故意不引 `layout.js`**，没有站点页头页脚。

### 状态与事件

购物车状态在 `localStorage`，键名 `sk_cart_v1` / `sk_orders_v1` / `sk_pref_v1` /
`sk_table_v1` / `sk_user_v1`。购物车结构是 `{lines: [{dishId, specIndex, qty}]}`，
**一行的身份是 `dishId#specIndex`**，同一道菜选不同规格算两行 —— 用 `SK.lineKey(l)` 生成这个键，
不要自己拼。

`SK.save()` 往 `document` 派发 `sk:cart-change` 自定义事件，`app.js` 收到后重画点单栏、
顶栏角标和移动端结算条。需要跟着变的页面（如 `cart.html`）订阅同一个事件即可。

### 用户画像只认 `USER_PROFILE`

`USER_PROFILE` 是推荐算法的**唯一输入**。个人中心存的偏好（`sk_pref_v1`）和登录态
（`sk_user_v1`）都由 `app.js` 的 `loadProfile()` 合并进去，其余代码只管读 `USER_PROFILE`，
不要自己去翻 `localStorage`。改画像的页面（如 `profile.html`）**存完要顺手改内存里的
`USER_PROFILE`**，否则同一个页面上改了没反应。

`loadProfile()` 在 `app.js` **被求值时**就跑（不是 `DOMContentLoaded`）——
`layout.js` 在 `mount()` 时要读登录态决定页头显示「登录」还是用户名，
而 layout 的监听器注册在 app 之前，挂在 `DOMContentLoaded` 里就来不及了。

### `SK.dishesOf(cat, list)` 才认「招牌推荐」

`CATEGORIES` 里的 `signature` 是**虚拟分类**：`data.js` 里没有任何 `cat:'signature'` 的菜，
招牌是打在菜品上的 `tags` 里的一个值。取某分类的菜一律走 `SK.dishesOf()`，别自己写
`list.filter(d => d.cat === c.id)` —— 那样「招牌推荐」会永远是空的，而页脚和首页
都链到 `menu.html#signature`。

### 重画 DOM 之后要重挂观察者

`SK.initCatRail()` 会把上一轮的 `IntersectionObserver` 断掉再重建。
**凡是重建了 `.cat-section` 的页面，渲染完必须再调一次** —— 旧 section 已经不在 DOM 里，
观察者不会报错，只是再也不触发，分类竖栏就再也不会跟着滚动高亮。
`app.js` 的 `init()` 里那次调用只对静态分类栏有用，动态渲染的页面得自己补。

### 两处全局代理，别在页面里重复绑定

- **加购**：`app.js` 里一个监听器处理所有 `[data-add]`。页面里写卡片时别自己绑 click。
- **点单栏加减**：同理处理 `[data-inc]` / `[data-dec]`。

页面里再绑一次**不会覆盖**全局那次，而是两次都跑：气泡从页面自己的监听器传到 `document`，
数量就被改了两遍，用户点一下加 2。`cart.html` 踩过这个坑 —— 它只要处理自己独有的
`[data-del]`，加减交给全局代理，然后靠 `sk:cart-change` 重画就行。

### 取菜品图一律走 `SK.img(d)`

**不要直接用 `d.img`**。`SK.img()` 在 `img` 为空时会退回 `SK.ph(d.name)`，
生成一张带菜名的虚线占位图块。现在 20 道菜都有实拍图，这条退路平时用不到，
但它是**故意留着的**：以后加新菜忘了配图，页面会显示虚线块而不是空白或随机照片，
一眼能看出是漏了。

### 别把同一个数抄两份

`data.js` 是唯一的假数据源，页面上的数字**能算就算**，别在 HTML 里再写一份。
`admin.html` 就吃过这个亏：「今日营业额 ¥8,610」在页面和 `REVENUE_14D` 里各存了一份，
后来折线的日均标签漂成了 `¥11,244`，而线按数据画在 `¥11,174` ——
标签和它自己标注的那条线自相矛盾。现在这几个数统一由脚本算，HTML 里只留 `—` 占位。

### 两个生成物

- `assets/icons/bundle.js` —— 由 `fetch_icons.py` **扫描全站 `mdi:` 引用**生成。
  页面里加了新图标必须重跑，否则图标显示为**空白且不报错**，很难查。
- `assets/illustrations/*.svg` —— unDraw 原图主色是 `#6c63ff`，已被重映射到本站色板。
  权威来源是 `recolor_illustrations.py` 里的 `PALETTE`，**改 `style.css` 的 `:root` 时要和它同步**。

### CSS

单个 `style.css`，用 CSS 自定义属性做令牌系统。

`.spec-opt` 是通用的「可选中胶囊」，被菜单筛选条、菜品规格选择、结算页桌号/就餐方式/支付方式、
个人中心口味偏好**四处复用** —— 改一次全站生效。

Grid 容器的子元素统一设了 `min-width: 0`（见 style.css 第 3 节注释）。
不设的话，默认的 `min-width: auto` 会让长内容顶宽整行，移动端直接横向溢出。

## 后端架构（`server/`）

`com.shike.ordering.algorithm` 是整篇论文的核心，它**不依赖 Spring、不依赖数据库**，
能被单独测（31 个单测）。其余包都是为它服务的脚手架：

| 包 | 职责 |
|---|---|
| `algorithm/` | AHP + TOPSIS。纯 JDK，`new` 出来就能跑 |
| `entity/` `mapper/` | 10 张表的实体与 MyBatis-Plus 持久层 |
| `service/` | 菜单组装、画像合成、推荐、下单、计费、看板 |
| `controller/` | REST 接口，只做参数校验和转发，不写业务 |
| `dto/` | 对外的视图对象 |
| `security/` `config/` | JWT、Spring Security、CORS、缓存、WebSocket |
| `ws/` | 后厨屏的 WebSocket 推送 |

### 10 张表

`t_user` `t_user_taste` `t_category` `t_dish` `t_dish_tag` `t_dish_ingredient`
`t_dish_spec` `t_dining_table` `t_order` `t_order_item`。DDL 在
`server/src/main/resources/db/schema.sql`，每张表都写了字段注释和设计理由。

两条容易忽略的取舍：**金额一律 `DECIMAL`**（浮点存钱迟早输出
`¥264.59999999999997`）；**订单明细里的菜名和单价是下单时的快照**，
不是外键回查 —— 菜名和价格以后会改，但历史小票必须原样还原得出来。

### 算法包不依赖 Spring，也不依赖 MyBatis

整个包只用 JDK：没有 `@Service`、没有 `@Autowired`、没有数据库实体。
输入用一个独立的值对象 `DishFeature`，由上层从实体映射过来。

这样做不是为了好看 —— 是为了**单元测试能直接 new 出来跑**。
算法是这个项目唯一"算错了论文就废了"的部分，必须能被独立验证，
不能每次跑测试都要先起一个 Spring 容器和数据库。

### 三个硬约束

1. **`Criterion` 枚举的顺序就是判断矩阵的行列顺序、也是决策矩阵的列顺序。**
   三处共用同一个 ordinal，所以不可能对不上。**改顺序会同时改 AHP 权重和 TOPSIS 的列**，
   定了就不要动。

2. **效益型/成本型是准则自身的属性**（`Criterion.benefit`），不是散在计算逻辑里的 `if`。
   价格和等待是成本型（越小越好），其余三项是效益型。方向搞反是这类论文最常见的错误。

3. **V⁺/V⁻ 必须在全部备选上取，不能只在展示用的前 k 名里取。**
   `TopsisRankerTest` 里有一条反面用例专门证明这件事：只在前几名里取会算出偏掉的理想解。

### 几个容易看漏的算法细节

- **热度准则用 √销量 而不是原始销量**：直接用量销的话，月销一千多份的爆款
  会把点心那几道全挤出榜单（量级差太大，一个准则就主导了整个排序）。
  这是论文创新点之二，`CriterionScorerTest` 里锁死了「销量 4 倍 → 销量项只到 2 倍」。
- **口味准则是唯一需要用户画像的**，其余四项都是菜品自身属性 ——
  正是这一点让新客第一次打开就能出结果。所以 `tasteScore` 对新客必须给基准分而不是 0。
- **忌口做子串 + 别名匹配**（勾「牛羊肉」能挡住「牛肉末」），每命中一项扣 30 分。
  不做同义词网 —— 那是后端菜谱知识库该干的事。
- **但子串匹配要配一张「形近食材」豁免名单**（`AVOID_FALSE_FRIENDS`：
  红腰豆 / 腰果 / 牛肝菌）。这是实测踩出来的 —— 当初勾「忌口内脏」之后，
  含**红腰豆**的蔬菜沙拉和含**牛肝菌**的拌面都被扣了 30 分，
  一个是豆子、一个是菌菇，和内脏毫无关系。
  那张名单是照着**当时**的菜单挑的，**换菜单时要重新过一遍**，
  看有没有新的形近词被误伤。答辩时被问一句
  「我忌口内脏，为什么沙拉被降权了」会很难看。
- **分数要夹到 0–100**。不夹的话忌口扣分能把口味分压成负数，
  而负数进了 TOPSIS 的归一化会算出诡异的距离。

### 数值必须和前端一致

`AhpCalculatorTest` 锁死了 λmax=5.084、CI=0.0210、CR=0.0188。
这几个数同时出现在**论文表 3.3、`demo/recommend.html` 页面、单元测试**三处。
改任何一边而另一边没跟，测试立刻红 —— 这是防止论文和代码对不上的保险，不要绕开它。

### 后端里三处「改了一定会出事」的地方

1. **金额只在 `FeeCalculator` 里算一次。**
   演示版栽过：结算页按堂食算了一遍服务费，订单详情页又对已含服务费的合计乘了
   一次 1.05，同一个订单两个价。现在金额只在结算时算一次、存进订单表，
   之后所有页面只读不算。另有冒烟测试盯着「实付 = 小计 + 服务费 − 自提立减」。
   **收费口径**：不收服务费；自提立减 2%、最多 4 元。两个数都在
   `shike.order.*` 里配着，服务费的机制留着（比例配 0），改一行就能恢复。
   用户 2026-10-17 拍板，见 `设计确认清单.md` 第 2 条。

2. **扣库存必须用 `DishMapper.deductStock` 那条带 `AND stock >= #{qty}` 的 UPDATE。**
   写成「先查库存、再判断、再更新」，两人同时点最后一份时会双双通过检查，
   库存变成负数。判断写进 `WHERE` 才是一个原子操作。返回 0 就表示不够，必须回滚。
   另外**同一道菜选两种规格要在请求里合并成一行再扣** —— 分开扣会各自通过检查，
   合起来却超了。

3. **接口不返回实体，一律走 `dto/` 里的 VO。**
   `User` 实体带着 BCrypt 摘要，`UserVO` 从类型上就不可能有密码字段。
   同理 `UserVO.of()` 会把手机号中间四位打码。

### 鉴权只在一处声明

放行规则全部集中在 `SecurityConfig`，控制器上**不再挂 `@PreAuthorize`**。
两处都写，改了其中一处忘了另一处就会出现意外放行的接口。

几条刻意的放行，都有理由，不要"顺手收紧"：

- **菜单和推荐接口匿名可访问**。新客第一次打开就能拿到带理由的推荐，
  是本方案相对协同过滤的核心优势；加一道登录墙这个优势就演示不出来了。
- **`/api/recommend` 的 GET 和 POST 都要放行**。POST 是「推荐实验室」拖滑杆用的，
  只放行 GET 的话游客一进实验室就被弹去登录页 —— 而那恰恰是最能说明
  算法在干什么的页面。它没有副作用（覆盖项不落库），放行是安全的。
- **`/api/auth/login` 和 `/api/auth/register` 逐个列出，不能写 `/api/auth/**`**。
  写通配符会把 `/api/auth/me` 一起放行，而未登录时 `@AuthenticationPrincipal`
  注入的是 null，控制器里一句 `me.id()` 就变成 NPE，
  前端拿到 500 而不是 401，登录态过期时完全不知道该跳登录页。

### 后厨推送用事件，不用直接调用

`OrderService` 只发布 `OrderPlacedEvent`，由 `KitchenBroadcaster` 用
`@TransactionalEventListener(AFTER_COMMIT)` 接收后再推。
用普通 `@EventListener` 会在事务提交**之前**触发 —— 万一回滚，
后厨屏上已经多出一道根本不存在的菜，厨师照做，做完发现没这单。

推送的内容是「变了，请重新拉取」而不是完整订单数据：客户端收到后调一次
`/api/kitchen/orders` 就拿得到与数据库完全一致的状态。推送里少带一个字段、
客户端少解析一次，两边就会不一致，而那是最难排查的一类问题。

### 缓存：只缓存纯函数

`RecommendationService.defaultWeights()` 上有 `@Cacheable` —— 它只依赖写死在代码里的
判断矩阵，永远不会有「改了数据但缓存没刷新」的问题。

**菜品和推荐结果都刻意没加缓存**：管理员改一次库存却看到旧菜单，
比多跑几次微秒级计算严重得多。Redis 是可选的（本机没装），
默认走 `spring.cache.type=simple`，带上 `--spring.profiles.active=redis` 才切 Redis。

### 后端还没做的

真实支付网关（现在是模拟支付，只落库状态）；下单后的超时自动取消
（现在只能手动取消，占着的库存不会自己释放）。

## 前端架构（`web/`）

### 复用 demo 的设计，不要另起炉灶

`web/src/assets/style.css` 是从 `demo/assets/css/style.css` 拷过来的那一份，
`.spec-opt`、`.dish`、`.order-rail`、`.ticket`、`.steps` 这些类名和结构全部沿用。
**两个文件是两份拷贝，改一边要同步另一边。**

`app.css` 只放 Vue 版本才需要的：骨架屏、Element Plus 主题对齐、容器宽度。
`kitchen.css` 从 `demo/kitchen.html` 的内联样式抽出来（后厨屏是反色的一套，和站点不同）。

Element Plus 的默认蓝必须被覆盖成朱砂（见 `app.css` 里那段 `--el-color-*`），
否则它会在骨白/松墨绿/朱砂的页面里显得像从别的站抠来的。

### 三条硬规则

1. **金额只由后端算。** 结算页调 `POST /api/orders/quote` 拿服务费和自提立减，
   前端**不做任何加减**。演示版就是自己按「堂食加 5%」算了一遍，
   结果和订单详情页对不上。收费规则只有 `FeeCalculator` 一份。

2. **请求体里不带价格。** `cart.toRequestItems()` 只发 `{dishCode, specId, qty}`。
   单价一律以服务端数据库为准 —— 否则改一下请求体就能一分钱买走一桌菜。

3. **购物车里也不存价格。** 行只存 `{dishCode, specId, qty}`，
   展示时用 `menu.dishByCode()` 还原。存了价格就会和后台改价后的菜单对不上。

### 一行的身份是 `dishCode#specId`

不是菜品编码。同一道菜选了「标准 6 只」和「大份 10 只」是两行 ——
用菜品编码做键会把它们合并，数量对了但规格只剩一个。
`useCartStore` 里的 `lineKey()` 生成这个键，不要自己去拼。

### 扫码进店：`/?table=A12`

`main.js` 在挂载前读这个参数、写进 `cart.table`，然后**把它从地址栏抹掉**。
真实店里桌上贴的二维码就是这个链接，扫进来桌号已经填好了。

抹掉是有意的：留着一个 `?table=A12` 会让用户以为换桌要改地址栏，
而换桌其实在结算页选；另外刷新时也不会把用户已经改过的桌号又盖回去。

### 购物车和菜单是两个 store，但都要等 `menu.load()` 完成

`useCartStore` 只存身份和数量，**价格、规格名、图片全部来自 `useMenuStore`**。
任何要显示金额的页面（购物车、点单栏、结算）都必须先 `await menu.load()`，
否则 `dishByCode()` 返回 null，那一行会静默消失 —— 用户会看到购物车无缘无故少了一项。

### WebSocket 推送的是「变了」，不是数据

后厨屏收到任何一条消息就重新拉一遍 `/api/kitchen/orders`。
**不要在推送里带订单数据** —— 推送里少带一个字段、客户端少解析一次，
两边就会不一致，而这类问题最难查。断线会自动重连（4 秒一次）。

### ECharts

- 图表初始化必须等 `loading` 变成 `false` 之后。图表容器长在 `v-else-if="data"`
  分支里，`try` 块内执行的话那一刻页面上还是骨架屏，ref 是 null，
  `echarts.init(null)` **静默失败**，两个图永远是空的而且不报错。
  这个 bug 构建和接口测试都抓不到，只有截图看一眼才会发现。
- 分类销售额用**横向条形图**而不是饼图：饼图比不出相近的值（22% 和 18% 看着差不多），
  横条的长度能直接比。分类名已经写在轴上，所以条是单色 —— 按分类上色只是装饰。
- 色板存在 `assets/chart-theme.js`。**当前两个图都是单系列，用不到分类色板**；
  里面那组多色是用 dataviz 的校验脚本跑过的，要加多系列的图时可以直接用，
  但**顺序不能重排**（相邻色才是校验对象）。

## 设计约定

方向是**骨白纸 / 松墨绿 / 朱砂**，有意避开 AI 生成页面那几个一眼可辨的套路
（奶油底 + 衬线 + 陶土色、SaaS 圆角卡片套装、全大写小标签）。

- `--cinnabar` **只用于价格和主操作**，是功能色不是装饰色，不要拿去点缀普通元素
- 全站唯一的记忆点是点单栏的「点心单」样式（竖朱线 + 条目虚线 + 数量方框 + 双线合计 + 朱砂圆章）
- **动效只有一处**：加购时印章盖一下。不要加滚动淡入、不要给每张卡片加 hover 动画

## 素材

**全部素材必须本地化，不要引任何外链字体或 CDN** —— 答辩现场网络不可控。
用户在中国大陆，实测 Google Fonts、jsDelivr、staticfile、360 前端静态库全部不可达；
unpkg、fonts.font.im、api.iconify.design、images.pexels.com 可达，但为稳妥仍应下载到本地。
字体直接用本机字体栈（思源宋体 / 思源黑体，Windows 上已装），不要引 Web 字体。

**20 道菜现在都有实拍图**。最后 4 道（艇仔粥、杨枝甘露、冻柠茶、菊花茶）
是 2026-10-22 用户自己补的，文件名用的是英文菜名而不是 `pexels-<id>`，
**来源未标注** —— 论文里交代素材来源时，这 4 张按「用户提供」写，别归到 Pexels 那批。

`SK.img()` 的占位图机制仍然保留：以后新增菜忘了配 `img`，页面会显示带菜名的虚线块，
既不空白也不塞随机图 —— 一眼能看出是漏了。

`demo/assets/img/unused/` 里另有 7 张当前用不上的照片（收敛菜系时剔掉的烧烤和火锅，
外加一张混进来的非食物照片）。**别删** —— Pexels 的搜索页在这台机器上返回 403，
图片直链虽然能下但**搜不到新的图**，删了就找不回来。该目录有 README 说明。

**菜品图有两份拷贝**：`demo/assets/img/dishes/` 和 `web/public/img/dishes/`。
换图或加图时两边都要放 —— 后端存的是 `assets/img/dishes/xxx.jpg`，
Vue 的 `useMenuStore.imageUrl()` 会把它映射成 `/img/dishes/xxx.jpg`。
只改 demo 那边的话，静态稿是对的、正式前端那张图 404，而且页面不会报错，只是图没了。

**用了没出现过的 `mdi:` 图标之后要重跑 `python tools/fetch_icons.py`**，
并把图标包同步到 Vue：`cp -r demo/assets/icons/. web/public/icons/`。
图标缺了的表现同样是**空白且不报错**，很难查。

授权注意：unDraw 禁止用于 AI 训练和整包再分发；Pexels 禁止直接转售未修改原图。
详见 `demo/README.md`。

## 协作约定

- 控制台里的中文乱码是 Windows GBK 代码页问题，**不影响生成的文件**，不用去修。
  但有两个连带坑会真的让测试失败：跑 Python 脚本前设 `PYTHONIOENCODING=utf-8`；
  用 curl 发含中文的 JSON 时要**把 body 写成 UTF-8 文件再 `--data-binary @file`**，
  直接在命令行 `-d '{"a":"中文"}'` 发出去的是 GBK 字节，服务端会报
  `Invalid UTF-8 middle byte`（后端已经把这种情况归成 400 而不是 500 了）。
- **IDE 和 Maven 共用 `target/`。** VS Code 的 Java 扩展会在你编辑到一半时
  编译一版类文件进去，于是 `mvn test` 可能拿到一个带
  `Unresolved compilation problems` 的旧 class，报出与源码无关的错误
  （实测：改完 `CriterionScorer` 之后 `AhpCalculatorTest` 突然 7 个 error）。
  **遇到看不懂的编译错误先 `mvn clean test`**，基本就好了。
- `tools/_chrome_profile/`、`tools/_verify_profile/`、`tools/_shots/`、
  `tools/_contact_sheet.jpg` 都是过程产物，不属于交付物。
- **不要再问「要不要开始搭」什么了** —— 三个子项目和发布包都完工了，没有待建的模块。
  用户的下一个里程碑是论文，见开头的「项目现在做到哪了」。
- 改动**必须实际验证过再说完成**。用户明确说过"检查不要出 bug"，
  这个项目没有 CI，六个脚本 + 单测就是全部的防线：

  | 改了什么 | 跑什么 | 覆盖 |
  |---|---|---|
  | `demo/assets/**` | `_verify.py` + `_audit.py` + `_resp.py` | 逻辑、无障碍、响应式 |
  | `server/**` | `mvn clean test` + `smoke_api.py` | 算法单测、接口端到端 |
  | `web/src/**` | `_verify_web.py` | 13 个路由能不能渲染出来 |
  | 后厨推送相关 | `ws_check.mjs` | 下单 → 后厨真的收到 |
  | 发布相关（profile / 打包脚本） | `build_demo_package.sh` 后**实际解压启动一次** | 包能不能跑起来 |

  **构建通过不等于页面能用。** 已经吃过一次：后台看板的两个图因为
  `echarts.init` 拿到的 ref 是 null 而永远是空的，`npm run build` 毫无反应，
  接口自测也测不到，只有截图看一眼才发现。改完前端**务必截图看一眼**：
  `chrome.exe --headless=new --screenshot="C:/绝对路径/x.png" "http://localhost:5173/admin"`
  （**路径不能用中文**，Chrome 会静默不写文件，用 `%TEMP%` 下的英文路径）。
- 涉及设计或业务口径的改动（价格、收费项、菜品内容），先看 `设计确认清单.md`。
  **第二节那几条 2026-10-17 已经全部拍板并落地了**，别再重问一遍。

## Agent skills

### Issue tracker

任务与 PRD 以 markdown 文件存放在 `.scratch/<feature-slug>/` 下，不走 GitHub/GitLab，因此没有 PR triage 入口。详见 `docs/agents/issue-tracker.md`。

### Triage labels

采用默认五角色词表（needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix）。详见 `docs/agents/triage-labels.md`。

### Domain docs

单 context 布局：根目录 `CONTEXT.md` 术语表 + `docs/adr/` 架构决策记录。详见 `docs/agents/domain.md`。

`CONTEXT.md` 已经写了（算法术语、容易说错的词、业务口径），
但 **`docs/adr/` 这个目录还没建** —— 至今没有一条正式记录的架构决策。
真要做记录时再建目录，现在不要为了「目录结构完整」去补一个空文件夹。
事实上这个项目好几条决策都够格写 ADR（虚拟分类、金额只算一次、
推送「变了」而不是数据、拒收服务费），需要的话从 `CONTEXT.md` 里挑。
