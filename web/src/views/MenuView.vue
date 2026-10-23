<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMenuStore } from '@/stores/menu'
import { useCartStore } from '@/stores/cart'
import DishCard from '@/components/DishCard.vue'
import CartRail from '@/components/CartRail.vue'

/**
 * 菜单页。
 *
 * 布局、类名全部照搬 demo 的 menu.html：`.order-layout` 三栏 =
 * 分类竖栏（sticky）+ 菜品区 + 点单栏。筛选胶囊用 `.spec-opt`，
 * 和菜品规格、结算页的就餐方式是同一套样式（改一次全站生效）。
 */
const route = useRoute()
const router = useRouter()
const menu = useMenuStore()
const cart = useCartStore()

const keyword = ref(String(route.query.q || ''))
const activeCat = ref('')
const activeFilter = ref('all')
const loading = ref(true)
const errorMsg = ref('')

const FILTERS = [
  { key: 'all', label: '全部', test: () => true },
  { key: '招牌', label: '招牌', test: (d) => d.tags?.includes('招牌') },
  { key: '素食', label: '素食', test: (d) => d.tags?.includes('素食') },
  { key: '不辣', label: '不辣', test: (d) => d.spice === 0 },
  { key: '快', label: '10 分钟内', test: (d) => d.prepMin <= 10 },
  { key: '平价', label: '30 元以内', test: (d) => Number(d.price) <= 30 },
]

function passesFilter(d) {
  const f = FILTERS.find((x) => x.key === activeFilter.value)
  return f ? f.test(d) : true
}

/** 关键词过滤在前端做：菜品只有几十道，为它多一次往返不划算 */
function matchesKeyword(d) {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return true
  return (
    d.name.toLowerCase().includes(q) ||
    (d.brief || '').toLowerCase().includes(q) ||
    (d.tags || []).some((t) => t.toLowerCase().includes(q))
  )
}

const visibleDishes = computed(() => menu.dishes.filter((d) => passesFilter(d) && matchesKeyword(d)))

/** 分组。搜索时退化成单一结果区，不再按分类拆 */
const groups = computed(() => {
  if (keyword.value.trim()) {
    return visibleDishes.value.length
      ? [{ code: 'search', name: `搜索「${keyword.value.trim()}」`, dishes: visibleDishes.value }]
      : []
  }
  return menu.categories
    .map((c) => ({ code: c.code, name: c.name, dishes: menu.byCategory(c.code).filter(passesFilter) }))
    .filter((g) => g.dishes.length > 0)
})

/** 分类竖栏上每一项的可用状态：筛选后没菜的分类要置灰并禁用 */
const railItems = computed(() =>
  menu.categories.map((c) => {
    const total = menu.byCategory(c.code).length
    const kept = menu.byCategory(c.code).filter(passesFilter).length
    return { ...c, total, kept, off: total > 0 && kept === 0 }
  }),
)

/* ---------- 分类竖栏的滚动高亮 ----------
 * 这里有个必须小心的地方：分组变了之后，观察器盯着的旧 DOM 已经不在页面上了。
 * 断开重建是必须的，否则它不报错、只是再也不触发，高亮就永远停在第一个分类 ——
 * 演示版踩过这个坑。所以重建绑在 groups 上，不是只在 onMounted 跑一次。
 */
const sectionEls = ref({})
let observer = null

function setSectionRef(code) {
  return (el) => {
    if (el) sectionEls.value[code] = el
    else delete sectionEls.value[code]
  }
}

function rebuildObserver() {
  observer?.disconnect()
  observer = null

  const entries = Object.entries(sectionEls.value)
  if (!entries.length) return

  observer = new IntersectionObserver(
    (records) => {
      // 取最靠上的那个可见分区。直接拿 records[0] 在快速滚动时会来回跳
      const visible = records
        .filter((r) => r.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible.length) activeCat.value = visible[0].target.dataset.cat
    },
    { rootMargin: '-96px 0px -72% 0px', threshold: 0 },
  )
  entries.forEach(([, el]) => observer.observe(el))
}

function scrollToCategory(code) {
  const el = sectionEls.value[code]
  if (!el) return
  activeCat.value = code
  window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 96, behavior: 'smooth' })
}

/* ---------- 搜索词与 URL 同步 ---------- */
watch(
  () => route.query.q,
  (q) => {
    const next = String(q || '')
    if (next !== keyword.value) keyword.value = next
  },
)

watch(keyword, (q) => {
  if (q === String(route.query.q || '')) return
  // replace 而不是 push：输入「小笼包」会逐字触发路由，
  // push 的话用户得按三次返回才能退出搜索
  router.replace({ name: 'menu', query: q ? { q } : {} })
})

watch(groups, async () => {
  await nextTick()
  rebuildObserver()
})

/* ---------- 加载 ---------- */
async function load(force = false) {
  loading.value = true
  errorMsg.value = ''
  try {
    await menu.load(force)
    await nextTick()
    rebuildObserver()
  } catch (e) {
    errorMsg.value = e.message || '菜单加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 从首页「招牌推荐」跳过来时清空筛选：筛选条和分类是两套条件，
  // 叠加起来会出现「点了招牌推荐，却因为筛选停在『素食』而一片空白」
  if (route.query.category) activeFilter.value = 'all'
  await load()
  const cat = String(route.query.category || '')
  if (cat) {
    activeCat.value = cat
    nextTick(() => scrollToCategory(cat))
  }
})

onBeforeUnmount(() => observer?.disconnect())

function onAdded(dish) {
  ElMessage({ message: `已加入点单：${dish.name}`, type: 'success', duration: 1200 })
}
</script>

<template>
  <nav class="crumb" aria-label="面包屑">
    <RouterLink :to="{ name: 'home' }">首页</RouterLink>
    <iconify-icon icon="mdi:chevron-right" width="14" height="14" aria-hidden="true"></iconify-icon>
    <span>点餐</span>
  </nav>

  <div class="page-head" style="display: flex; align-items: flex-end; gap: 20px; flex-wrap: wrap">
    <div style="flex: 1; min-width: 280px">
      <h1>{{ keyword ? `搜索「${keyword}」` : '全部菜单' }}</h1>
      <p>现做现出，出餐时间都标在卡片上 · {{ cart.table }} 桌</p>
    </div>

    <div class="filter-chips" role="group" aria-label="筛选条件">
      <button
        v-for="f in FILTERS"
        :key="f.key"
        type="button"
        class="spec-opt"
        :class="{ 'is-on': activeFilter === f.key }"
        :aria-pressed="activeFilter === f.key"
        @click="activeFilter = f.key"
      >
        {{ f.label }}
      </button>
    </div>
  </div>

  <div class="order-layout">
    <aside class="cat-rail" aria-label="菜品分类">
      <h2>分 类</h2>
      <ul>
        <li v-for="c in railItems" :key="c.code">
          <a
            href="#"
            :class="{ 'is-active': activeCat === c.code, 'cat-off': c.off }"
            :aria-current="activeCat === c.code ? 'true' : undefined"
            @click.prevent="!c.off && scrollToCategory(c.code)"
          >
            {{ c.name }}
            <span class="count">{{ c.kept }}</span>
          </a>
        </li>
      </ul>
    </aside>

    <div>
      <div v-if="loading" class="dish-grid">
        <div v-for="i in 6" :key="i" class="sk-card" style="padding: 0; overflow: hidden">
          <div class="sk-skeleton" style="height: 168px; border-radius: 0"></div>
          <div style="padding: 14px">
            <div class="sk-skeleton" style="height: 16px; width: 60%"></div>
            <div class="sk-skeleton" style="height: 13px; margin-top: 10px"></div>
          </div>
        </div>
      </div>

      <div v-else-if="errorMsg" class="empty-state">
        <h3>菜单没能加载出来</h3>
        <p>{{ errorMsg }}</p>
        <button class="btn btn-primary" type="button" @click="load(true)">重试</button>
      </div>

      <div v-else-if="!groups.length" class="empty-state">
        <h3>没有符合条件的菜</h3>
        <p>
          {{ keyword ? `「${keyword}」没搜到。` : '当前筛选下没有菜。' }}
          换个条件试试，或点下面的按钮清空。
        </p>
        <button
          class="btn btn-ghost"
          type="button"
          @click="
            () => {
              keyword = ''
              activeFilter = 'all'
            }
          "
        >
          清空搜索与筛选
        </button>
      </div>

      <template v-else>
        <section v-for="g in groups" :key="g.code" :ref="setSectionRef(g.code)" :data-cat="g.code">
          <h2 class="cat-title">{{ g.name }}</h2>
          <div class="dish-grid">
            <DishCard v-for="d in g.dishes" :key="d.code" :dish="d" @added="onAdded" />
          </div>
        </section>
      </template>
    </div>

    <CartRail />
  </div>
</template>

<style scoped>
/* .cat-section 在 demo 里没有专门的样式，靠的是通用 h2。
   这里给分组标题加一点上间距，让相邻分类之间有呼吸感 */
.cat-title {
  margin: 30px 0 14px;
  font-size: var(--fs-lg);
}

section:first-of-type .cat-title { margin-top: 0; }
</style>
