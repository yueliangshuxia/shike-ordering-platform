import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

const CART_KEY = 'sk_cart_v2'
const TABLE_KEY = 'sk_table_v1'

/**
 * 购物车。
 *
 * <p>沿用 demo 已经定下来的两条规则，不要改：
 *
 * 1. **一行的身份是 `dishCode#specId`**，不是菜品编码。
 *    同一道菜选了「标准 6 只」和「大份 10 只」是两行 —— 用菜品编码做键
 *    会把它们合并成一行，数量对了但规格只剩一个，用户拿到的不是他要的。
 *
 * 2. **购物车存在前端**，下单时才把 `{dishCode, specId, qty}` 发给后端。
 *    后端不接受前端传来的价格 —— 价格一律以服务端数据库为准。
 */
export const useCartStore = defineStore('cart', () => {
  const lines = ref(load())

  /**
   * 当前桌号。扫码进来时会带上，没扫就默认 A12。
   *
   * 放在购物车这一层而不是单独开一个 store：它俩的生命周期是一致的
   * ——「这一桌这一次点单」。换桌等于重新开始点，清空购物车是合理的。
   */
  const table = ref(localStorage.getItem(TABLE_KEY) || 'A12')

  function setTable(code) {
    table.value = code || 'A12'
    try {
      localStorage.setItem(TABLE_KEY, table.value)
    } catch {
      /* 隐私模式下写不了，忽略 */
    }
  }

  /** 菜品的完整信息由菜单 store 补进来，购物车本身只存身份和数量 */
  const count = computed(() => lines.value.reduce((s, l) => s + l.qty, 0))
  const isEmpty = computed(() => lines.value.length === 0)

  function lineKey(line) {
    return `${line.dishCode}#${line.specId ?? ''}`
  }

  function add(dish, spec, qty = 1) {
    const specId = spec?.id ?? null
    const key = `${dish.code}#${specId ?? ''}`
    const existing = lines.value.find((l) => lineKey(l) === key)
    if (existing) {
      existing.qty += qty
    } else {
      lines.value.push({ dishCode: dish.code, specId, qty })
    }
  }

  function changeQty(line, delta) {
    const target = lines.value.find((l) => lineKey(l) === lineKey(line))
    if (!target) return
    target.qty += delta
    if (target.qty <= 0) {
      remove(target)
    }
  }

  function remove(line) {
    const key = lineKey(line)
    lines.value = lines.value.filter((l) => lineKey(l) !== key)
  }

  function clear() {
    lines.value = []
  }

  /** 下单成功后要把行号带上（同一道菜可能因为同名规格拆成两行） */
  function toRequestItems() {
    return lines.value.map((l) => ({ dishCode: l.dishCode, specId: l.specId, qty: l.qty }))
  }

  function load() {
    try {
      const raw = JSON.parse(localStorage.getItem(CART_KEY) || '[]')
      // 只保留结构正确的行。localStorage 里可能是旧版本留下的数据，
      // 直接信任它会让页面在渲染时炸在一个莫名其妙的字段上
      return Array.isArray(raw)
        ? raw.filter((l) => l && typeof l.dishCode === 'string' && Number.isFinite(l.qty) && l.qty > 0)
        : []
    } catch {
      return []
    }
  }

  watch(
    lines,
    (v) => {
      try {
        localStorage.setItem(CART_KEY, JSON.stringify(v))
      } catch {
        /* 隐私模式下 localStorage 不可写，忽略即可 —— 购物车退化成刷新即丢，
           但不该因此让页面报错 */
      }
    },
    { deep: true },
  )

  return {
    lines, count, isEmpty, table,
    lineKey, setTable, add, changeQty, remove, clear, toRequestItems,
  }
})
