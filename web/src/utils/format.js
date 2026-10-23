/**
 * 展示用的格式化与映射。全部是纯函数，不依赖 store，方便单独用。
 */

/** 金额。后端返回的是字符串形式的 DECIMAL，直接算会变成字符串拼接 */
export function money(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  // 整数不显示小数位（¥42 而不是 ¥42.00），有零头才显示两位 ——
  // 菜单上满屏的 .00 很吵，而服务费那种真带零头的地方又不能省
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

export function priceWithCurrency(v) {
  return `¥${money(v)}`
}

const SPICE_LABELS = ['不辣', '微辣', '中辣', '特辣']

export function spiceLabel(level) {
  return SPICE_LABELS[level] ?? ''
}

/** 标签 → 图标与样式类。和 demo 的 tagOf 保持一致，不要各画各的 */
const TAG_META = {
  招牌: { icon: 'mdi:star', cls: 'tag-sign' },
  素食: { icon: 'mdi:leaf', cls: 'tag-veg' },
  冰饮: { icon: 'mdi:snowflake', cls: 'tag-cold' },
  现做: { icon: 'mdi:chef-hat', cls: 'tag' },
}

export function tagMeta(tag) {
  return TAG_META[tag] || { icon: 'mdi:check-circle', cls: 'tag' }
}

/** 时间戳 → 「刚刚 / 12 分钟前」。后厨屏按等待时长排序，需要这个 */
export function relativeMinutes(iso) {
  if (!iso) return 0
  const then = new Date(iso).getTime()
  if (!Number.isFinite(then)) return 0
  return Math.max(0, Math.floor((Date.now() - then) / 60000))
}

export function dateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
