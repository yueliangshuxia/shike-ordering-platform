import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api'

/**
 * 菜单缓存。
 *
 * <p>整个应用里只有这里持有菜品列表，其余页面都从它取。
 * 页面各自去拉一遍的话，会出现「菜单页显示有货、购物车显示售罄」
 * 这种自相矛盾的画面，而它们其实读的是同一份数据，只是取的时间不同。
 *
 * <p>它同时负责把购物车里的 `dishCode` 还原成完整的菜品 ——
 * 购物车只存身份和数量，结算时要拿单价、规格名和图片，都得从这儿查。
 */
export const useMenuStore = defineStore('menu', () => {
  const categories = ref([])
  const dishes = ref([])
  const loading = ref(false)
  const error = ref('')
  const loadedAt = ref(0)

  const byCode = computed(() =>
    Object.fromEntries(dishes.value.map((d) => [d.code, d])),
  )

  /**
   * 加载菜单。传 `force` 才重新请求。
   *
   * 加这层判断是因为「加购之后跳回菜单」这类跳转很频繁，
   * 每次都重新拉一遍会让页面一直闪。菜单本身的变动频率很低，
   * 单次会话内用缓存是划算的 —— 后台改了价，刷新页面就能看到。
   */
  async function load(force = false) {
    if (!force && dishes.value.length && Date.now() - loadedAt.value < 60_000) {
      return
    }
    loading.value = true
    error.value = ''
    try {
      const [cats, list] = await Promise.all([api.listCategories(), api.listDishes()])
      categories.value = cats
      dishes.value = list
      loadedAt.value = Date.now()
    } catch (e) {
      error.value = e.message || '菜单加载失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function dishByCode(code) {
    return byCode.value[code] || null
  }

  /**
   * 菜品图片地址。
   *
   * 库里存的是 `assets/img/dishes/xxx.jpg` 这样的相对路径（沿用了 demo 的写法），
   * 而 Vue 这边图片放在 `public/img/...`。这里做一次映射，
   * 免得把这个前缀散落到每个要显示图片的组件里。
   *
   * 没有实拍图的菜返回空串，由组件决定怎么画占位块 ——
   * 后端本来就没有这个字段，不要在这里编一个假路径。
   */
  function imageUrl(dish) {
    const raw = dish?.img
    if (!raw) return ''
    return '/' + raw.replace(/^assets\//, '')
  }

  /** 按分类分组，虚拟分类「招牌推荐」由后端给，这里不需要特判 */
  function byCategory(code) {
    if (code === 'signature') {
      return dishes.value.filter((d) => d.tags?.includes('招牌'))
    }
    return dishes.value.filter((d) => d.categoryId === code)
  }

  return { categories, dishes, loading, error, byCode, load, dishByCode, imageUrl, byCategory }
})
