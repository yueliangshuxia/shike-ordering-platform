/* 从 demo/assets/js/data.js 生成 server/src/main/resources/db/data.sql
 *
 * 为什么要生成而不是手抄一份 SQL：
 *   Demo 里的菜品数据就是论文里的实验数据。手抄一遍，两边迟早会漂 ——
 *   改了菜单只改一边，推荐结果对不上，等到答辩被问到就说不清了。
 *   这里让 Demo 保持唯一数据源，SQL 是它的派生物，改完 data.js 重跑一次即可。
 *
 * 用法： node tools/gen_seed.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = path.join(ROOT, 'demo', 'assets', 'js', 'data.js');
const OUT = path.join(ROOT, 'server', 'src', 'main', 'resources', 'db', 'data.sql');

/* ---- 1. 把 data.js 当脚本求值，取它挂到 window 上的那几个全局 ---- */
function loadDemoData() {
  const source = fs.readFileSync(SRC, 'utf8');
  const shim = {};
  // data.js 是给浏览器写的：顶层 const + 结尾挂到 window。
  // 给它一个假的 window 就能在 Node 里原样跑，不需要维护第二份数据。
  const read = new Function('window', `${source}\nreturn window;`);
  return read(shim);
}

const data = loadDemoData();
const { SHOP, CATEGORIES, DISHES } = data;

for (const key of ['SHOP', 'CATEGORIES', 'DISHES']) {
  if (!data[key]) throw new Error(`data.js 里没有 ${key}，生成中止`);
}

/* ---- 2. SQL 字面量输出 ---- */
const q = (v) => (v === null || v === undefined || v === '' ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);
const n = (v) => (v === null || v === undefined ? 'NULL' : String(v));
const money = (v) => Number(v).toFixed(2);

const lines = [];
const say = (s = '') => lines.push(s);

say('-- =====================================================================');
say('--  食刻点餐系统 · 演示数据（由 tools/gen_seed.mjs 自动生成，不要手改）');
say('--');
say('--  来源：demo/assets/js/data.js —— 那是唯一的数据源，改菜单请改那里再重跑：');
say('--      node tools/gen_seed.mjs');
say('--');
say('--  全部是 INSERT IGNORE，配合 schema.sql 的 IF NOT EXISTS，');
say('--  应用每次启动重跑本脚本都不会重复插入，也不会覆盖管理员改过的数据。');
say('--');
say('--  用户账号和订单<b>不在这里</b>：密码要用 BCrypt 现算，');
say('--  写死在 SQL 里既不好看也不安全，交给 DataInitializer 在启动时补。');
say('-- =====================================================================');
say();

/* ---- 3. 分类（跳过 signature —— 它是虚拟分类，不进表） ---- */
const realCategories = CATEGORIES.filter((c) => c.id !== 'signature');
say('-- 分类。signature（招牌推荐）是虚拟分类，故意不插进来，');
say('-- 它由 DishService 在返回分类列表时拼在最前面。');
say('INSERT IGNORE INTO t_category (code, name, icon, sort, enabled) VALUES');
say(
  realCategories
    .map((c, i) => `    (${q(c.id)}, ${q(c.name)}, ${q(c.icon)}, ${n(i)}, 1)`)
    .join(',\n') + ';'
);
say();

/* ---- 4. 餐桌 ---- */
say('-- 餐桌');
say('INSERT IGNORE INTO t_dining_table (code, seats, area, status, enabled) VALUES');
say(
  SHOP.tables
    .map((t) => `    (${q(t.id)}, ${n(t.seats)}, ${q(t.area)}, 'FREE', 1)`)
    .join(',\n') + ';'
);
say();

/* ---- 5. 菜品 ---- */
say('-- 菜品。category_id 用子查询按 code 取，这样不依赖自增 id 的具体值');
const dishCols =
  '(code, name, category_id, price, img, brief, detail, spice, sold, rating, prep_min, stock, unit, spec_label, enabled)';
say(`INSERT IGNORE INTO t_dish ${dishCols} VALUES`);
say(
  DISHES.map((d) => {
    const cat = `(SELECT id FROM t_category WHERE code = ${q(d.cat)})`;
    return (
      `    (${q(d.id)}, ${q(d.name)}, ${cat}, ${money(d.price)}, ${q(d.img)}, ` +
      `${q(d.desc)}, ${q(d.detail)}, ${n(d.spice)}, ${n(d.sold)}, ` +
      `${Number(d.rating).toFixed(1)}, ${n(d.prepMin)}, ${n(d.stock)}, ` +
      `${q(d.unit)}, ${q(d.specLabel)}, 1)`
    );
  }).join(',\n') + ';'
);
say();

/* ---- 6. 标签 ---- */
const tagRows = [];
for (const d of DISHES) {
  for (const t of d.tags ?? []) {
    tagRows.push(`    ((SELECT id FROM t_dish WHERE code = ${q(d.id)}), ${q(t)})`);
  }
}
say('-- 标签。招牌 / 素食 / 冰饮 / 现做 —— 推荐算法里几处分支都读它');
say('INSERT IGNORE INTO t_dish_tag (dish_id, tag) VALUES');
say(tagRows.join(',\n') + ';');
say();

/* ---- 7. 食材 ---- */
const ingRows = [];
for (const d of DISHES) {
  (d.ingredients ?? []).forEach((name, i) => {
    ingRows.push(`    ((SELECT id FROM t_dish WHERE code = ${q(d.id)}), ${q(name)}, ${i})`);
  });
}
say('-- 食材。忌口过滤的数据来源，存原文不存编码：算法做的是子串 + 别名匹配');
say('INSERT IGNORE INTO t_dish_ingredient (dish_id, name, sort) VALUES');
say(ingRows.join(',\n') + ';');
say();

/* ---- 8. 规格 ---- */
const specRows = [];
for (const d of DISHES) {
  (d.specs ?? []).forEach((s, i) => {
    specRows.push(
      `    ((SELECT id FROM t_dish WHERE code = ${q(d.id)}), ${q(s.name)}, ${money(s.delta)}, ${i})`
    );
  });
}
say('-- 规格。price_delta 是相对基准价的加价，改基础价时不用逐条改这里');
say('INSERT IGNORE INTO t_dish_spec (dish_id, name, price_delta, sort) VALUES');
say(specRows.join(',\n') + ';');
say();

/* ---- 9. 自查 ---- */
say('-- 生成结果自查：');
say(`--   分类 ${realCategories.length} 条 / 菜品 ${DISHES.length} 条 / ` +
    `标签 ${tagRows.length} 条 / 食材 ${ingRows.length} 条 / ` +
    `规格 ${specRows.length} 条 / 餐桌 ${SHOP.tables.length} 条`);

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, lines.join('\n') + '\n', 'utf8');

console.log(`已生成 ${path.relative(ROOT, OUT)}`);
console.log(
  `  分类 ${realCategories.length} / 菜品 ${DISHES.length} / 标签 ${tagRows.length} / ` +
  `食材 ${ingRows.length} / 规格 ${specRows.length} / 餐桌 ${SHOP.tables.length}`
);
