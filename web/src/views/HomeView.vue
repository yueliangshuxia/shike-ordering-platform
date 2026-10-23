<script setup>
import { computed, onMounted, ref } from 'vue'
import { useMenuStore } from '@/stores/menu'
import { recommend } from '@/api'
import DishCard from '@/components/DishCard.vue'
import { dishPlaceholder } from '@/utils/placeholder'

/**
 * 首页。
 *
 * 卖点写在最上面：**每道菜要做多久，点之前就告诉你**。
 * 这不是文案修辞，是这个系统真实在做的事 —— 出餐时长是 TOPSIS 的
 * 五个准则之一（等待，成本型），用户看到的推荐排序里它占 11.66% 的权重。
 */
const menu = useMenuStore()
const recs = ref([])
const recLoading = ref(true)

const availableCount = computed(() => menu.dishes.filter((d) => !d.soldOut).length)
const avgPrep = computed(() => {
  const list = menu.dishes.filter((d) => !d.soldOut)
  if (!list.length) return 0
  return Math.round(list.reduce((s, d) => s + (d.prepMin || 0), 0) / list.length)
})

const heroPhoto = computed(() => {
  // 挑一张有条码的实拍图当主视觉；都取不到就退回占位图，不留空白
  const withImg = menu.dishes.find((d) => d.img && d.tags?.includes('招牌'))
  return withImg ? menu.imageUrl(withImg) : dishPlaceholder('食刻')
})

onMounted(async () => {
  try {
    await menu.load()
  } catch {
    /* 首页拿不到菜单也不该白屏，下面的推荐区会各自兜底 */
  }
  try {
    // 首页只要前 4 名。未登录也能调 —— 新客进来就该看到推荐，
    // 这正是本方案相对协同过滤的优势所在
    const res = await recommend({ topK: 4 })
    const byCode = Object.fromEntries(menu.dishes.map((d) => [d.code, d]))
    recs.value = res.dishes.map((r) => ({ ...r, dish: byCode[r.code] })).filter((r) => r.dish)
  } catch {
    recs.value = []
  } finally {
    recLoading.value = false
  }
})
</script>

<template>
  <section class="hero">
    <div class="wrap-wide">
      <div>
        <h1>每道菜要做多久<br />点之前就告诉你</h1>
        <p>
          食刻是一家做蒸点、小炒、烧烤和火锅的小馆。菜是现做的，
          所以有等待时间 —— 我们把每道菜的出餐时长都标在菜单上，
          赶时间就挑快的，不赶就慢慢等一笼小笼包。
        </p>

        <div class="hero-actions">
          <RouterLink class="btn btn-primary btn-lg" :to="{ name: 'menu' }">
            <iconify-icon icon="mdi:silverware-fork-knife" width="19" height="19" aria-hidden="true"></iconify-icon>
            开始点菜
          </RouterLink>
          <RouterLink class="btn btn-ghost btn-lg" :to="{ name: 'recommend' }">
            <iconify-icon icon="mdi:star" width="19" height="19" aria-hidden="true"></iconify-icon>
            看推荐是怎么算的
          </RouterLink>
        </div>

        <div class="hero-facts">
          <div>
            <div class="fact-value">{{ availableCount || '—' }}</div>
            <div class="fact-label">在售菜品</div>
          </div>
          <div>
            <div class="fact-value">{{ avgPrep || '—' }}</div>
            <div class="fact-label">平均出餐（分钟）</div>
          </div>
          <div>
            <div class="fact-value">5</div>
            <div class="fact-label">推荐准则</div>
          </div>
        </div>
      </div>

      <div class="hero-art">
        <div class="hero-seal" aria-hidden="true">现做<br />现出</div>
        <figure class="hero-photo">
          <img :src="heroPhoto" alt="食刻的招牌菜" />
          <figcaption>招牌菜 · 现点现做</figcaption>
        </figure>
      </div>
    </div>
  </section>

  <div class="wrap-wide" style="padding-bottom: 64px">
    <section style="margin-top: 40px">
      <div class="page-head">
        <div>
          <h2 style="margin: 0; font-size: var(--fs-xl)">为你推荐</h2>
          <p>用 AHP 定权重、TOPSIS 排的名次，每条都带着推荐理由</p>
        </div>
        <RouterLink class="btn btn-ghost" :to="{ name: 'recommend' }">
          打开推荐实验室
        </RouterLink>
      </div>

      <div v-if="recLoading" class="rec-band">
        <div v-for="i in 4" :key="i" class="sk-card">
          <div class="sk-skeleton" style="height: 18px; width: 55%"></div>
          <div class="sk-skeleton" style="height: 13px; margin-top: 12px"></div>
          <div class="sk-skeleton" style="height: 13px; margin-top: 8px; width: 80%"></div>
        </div>
      </div>

      <div v-else-if="!recs.length" class="empty-state">
        <h3>推荐暂时出不来</h3>
        <p>后端可能还没启动。菜单和点餐仍然可用。</p>
      </div>

      <div v-else class="rec-band">
        <article v-for="r in recs" :key="r.code" class="sk-card">
          <div class="sk-row-between">
            <span class="sk-tag sk-tag--signature">第 {{ r.rank }} 名</span>
            <span class="sk-num" style="font-size: var(--fs-xs); color: var(--ink-3)">
              贴近度 {{ r.closeness.toFixed(4) }}
            </span>
          </div>

          <h3 style="margin: 10px 0 4px">
            <RouterLink :to="{ name: 'dish', params: { code: r.code } }">{{ r.name }}</RouterLink>
          </h3>
          <p style="margin: 0 0 10px; color: var(--ink-3); font-size: var(--fs-sm)">{{ r.dish.brief }}</p>

          <ul style="margin: 0; padding-left: 18px; font-size: var(--fs-sm); color: var(--ink-2)">
            <li v-for="reason in r.reasons.slice(0, 3)" :key="reason">{{ reason }}</li>
          </ul>

          <p v-if="r.caveats?.length" style="margin: 8px 0 0; font-size: var(--fs-xs); color: var(--gold)">
            {{ r.caveats[0] }}
          </p>

          <div class="sk-row-between" style="margin-top: 12px">
            <span class="sk-price price"><span class="cur">¥</span>{{ Number(r.price).toFixed(0) }}</span>
            <!-- 用 ghost 而不是 primary：四张卡片各挂一个朱砂按钮，
                 会把「朱砂只给价格和主操作」这条规矩冲淡，
                 价格也就不再是这一屏里最跳的东西了 -->
            <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'dish', params: { code: r.code } }">
              去看看
            </RouterLink>
          </div>
        </article>
      </div>
    </section>

    <section style="margin-top: 44px">
      <h2 style="font-size: var(--fs-xl)">按分类逛</h2>
      <div class="cat-cards">
        <RouterLink
          v-for="c in menu.categories.filter((x) => x.count > 0)"
          :key="c.code"
          class="cat-card"
          :to="{ name: 'menu', query: { category: c.code } }"
        >
          <span class="cat-icon">
            <iconify-icon :icon="c.icon || 'mdi:silverware-variant'" width="22" height="22" aria-hidden="true"></iconify-icon>
          </span>
          <span>
            <span class="cat-name" style="display: block">{{ c.name }}</span>
            <span class="cat-count">{{ c.count }} 道</span>
          </span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
