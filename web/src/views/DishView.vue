<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { useCartStore } from '@/stores/cart'
import { useMenuStore } from '@/stores/menu'
import { money, spiceLabel, tagMeta } from '@/utils/format'
import { dishPlaceholder } from '@/utils/placeholder'

/**
 * 菜品详情。
 *
 * 规格必须在这里选，不能像列表页那样「点 + 就加进去」——
 * 列表页看不出「标准 6 只」和「大份 10 只」的差别，
 * 替用户默认选一个，他永远拿不到想要的那份。
 */
const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const menu = useMenuStore()

const dish = ref(null)
const loading = ref(true)
const errorMsg = ref('')
const specId = ref(null)
const qty = ref(1)

const specs = computed(() => dish.value?.specs || [])
const chosenSpec = computed(() => specs.value.find((s) => s.id === specId.value) || specs.value[0] || null)

/** 实际单价 = 基准价 + 规格加价。这个数由后端算好放在 spec.price 里 */
const unitPrice = computed(() => {
  if (!dish.value) return 0
  return chosenSpec.value ? Number(chosenSpec.value.price) : Number(dish.value.price)
})

const total = computed(() => unitPrice.value * qty.value)
const img = computed(() => (dish.value ? menu.imageUrl(dish.value) || dishPlaceholder(dish.value.name) : ''))
const soldOut = computed(() => dish.value?.soldOut === true)

async function load() {
  loading.value = true
  errorMsg.value = ''
  dish.value = null
  try {
    dish.value = await api.getDish(route.params.code)
    specId.value = dish.value.specs?.[0]?.id ?? null
    qty.value = 1
  } catch (e) {
    errorMsg.value = e.message || '菜品加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 从「相关菜品」跳到另一道菜时组件会被复用，不监听参数就还是上一道菜
watch(() => route.params.code, load)

function addToCart() {
  if (soldOut.value) return
  cart.add(dish.value, chosenSpec.value, qty.value)
  ElMessage({
    message: `已加入点单：${dish.value.name}${chosenSpec.value ? ' · ' + chosenSpec.value.name : ''} × ${qty.value}`,
    type: 'success',
    duration: 1500,
  })
}

function buyNow() {
  addToCart()
  router.push({ name: 'checkout' })
}
</script>

<template>
  <nav class="crumb" aria-label="面包屑">
    <RouterLink :to="{ name: 'home' }">首页</RouterLink>
    <iconify-icon icon="mdi:chevron-right" width="14" height="14" aria-hidden="true"></iconify-icon>
    <RouterLink :to="{ name: 'menu' }">点餐</RouterLink>
    <iconify-icon icon="mdi:chevron-right" width="14" height="14" aria-hidden="true"></iconify-icon>
    <span>{{ dish?.name || '菜品' }}</span>
  </nav>

  <div v-if="loading" class="sk-split">
    <div class="sk-skeleton" style="height: 380px"></div>
    <div class="sk-card">
      <div class="sk-skeleton" style="height: 24px; width: 60%"></div>
      <div class="sk-skeleton" style="height: 14px; margin-top: 14px"></div>
    </div>
  </div>

  <div v-else-if="errorMsg" class="empty-state">
    <h3>这道菜找不到了</h3>
    <p>{{ errorMsg }}</p>
    <RouterLink class="btn btn-primary" :to="{ name: 'menu' }">回菜单</RouterLink>
  </div>

  <template v-else-if="dish">
    <div class="detail-grid">
      <figure class="detail-photo">
        <img :src="img" :alt="dish.name" />
        <span v-if="soldOut" class="dish-flag is-sold">今日售罄</span>
      </figure>

      <div>
        <h1 class="detail-title">{{ dish.name }}</h1>
        <p class="detail-desc">{{ dish.brief }}</p>

        <div class="dish-tags" style="margin: 12px 0">
          <span v-if="dish.spice" class="tag tag-hot">
            <iconify-icon icon="mdi:chili-hot" width="12" height="12" aria-hidden="true"></iconify-icon>
            {{ spiceLabel(dish.spice) }}
          </span>
          <span v-for="t in dish.tags" :key="t" class="tag" :class="tagMeta(t).cls">
            <iconify-icon :icon="tagMeta(t).icon" width="12" height="12" aria-hidden="true"></iconify-icon>
            {{ t }}
          </span>
        </div>

        <div class="detail-meta">
          <span><iconify-icon icon="mdi:clock-outline" width="15" height="15" aria-hidden="true"></iconify-icon> 出餐约 {{ dish.prepMin }} 分钟</span>
          <span><iconify-icon icon="mdi:fire" width="15" height="15" aria-hidden="true"></iconify-icon> 月售 {{ dish.sold }}</span>
          <span><iconify-icon icon="mdi:star-outline" width="15" height="15" aria-hidden="true"></iconify-icon> {{ dish.rating }} 分</span>
          <span v-if="!soldOut"><iconify-icon icon="mdi:package-variant" width="15" height="15" aria-hidden="true"></iconify-icon> 剩 {{ dish.stock }} {{ dish.unit || '份' }}</span>
        </div>

        <p class="detail-price price">
          <span class="cur">¥</span>{{ money(unitPrice) }}
          <small v-if="chosenSpec && Number(chosenSpec.priceDelta) > 0" style="color: var(--ink-3); font-size: var(--fs-sm)">
            含 {{ chosenSpec.name }} 加价 ¥{{ money(chosenSpec.priceDelta) }}
          </small>
        </p>

        <div v-if="specs.length > 1" class="field">
          <label>{{ dish.specLabel || '规格' }}</label>
          <div class="filter-chips" style="margin-top: 8px">
            <button
              v-for="s in specs"
              :key="s.id"
              type="button"
              class="spec-opt"
              :class="{ 'is-on': specId === s.id }"
              :aria-pressed="specId === s.id"
              @click="specId = s.id"
            >
              {{ s.name }}
              <span v-if="Number(s.priceDelta) > 0" style="opacity: .75">+¥{{ money(s.priceDelta) }}</span>
            </button>
          </div>
        </div>

        <div class="field">
          <label for="qty">数量</label>
          <div class="qty-box" id="qty" style="margin-top: 8px; width: fit-content">
            <button type="button" aria-label="减少" @click="qty = Math.max(1, qty - 1)">
              <iconify-icon icon="mdi:minus" width="14" height="14"></iconify-icon>
            </button>
            <span class="qty">{{ qty }}</span>
            <button type="button" aria-label="增加" @click="qty += 1">
              <iconify-icon icon="mdi:plus" width="14" height="14"></iconify-icon>
            </button>
          </div>
        </div>

        <div class="detail-buy">
          <span class="price" style="font-size: var(--fs-lg)">
            合计 <span class="cur">¥</span>{{ money(total) }}
          </span>
          <button class="btn btn-ghost" type="button" :disabled="soldOut" @click="addToCart">
            加入点单
          </button>
          <button class="btn btn-primary" type="button" :disabled="soldOut" @click="buyNow">
            {{ soldOut ? '已售罄' : '立即结算' }}
          </button>
        </div>
      </div>
    </div>

    <div class="sk-split" style="margin-top: 32px">
      <div>
        <div class="sk-card">
          <h2>做法</h2>
          <p style="margin: 0; color: var(--ink-2)">{{ dish.detail }}</p>
        </div>
      </div>

      <div>
        <div class="sk-card">
          <h2>主要食材</h2>
          <ul class="ingredient-list">
            <li v-for="ing in dish.ingredients" :key="ing" class="ingredient">
              <iconify-icon icon="mdi:circle-small" width="14" height="14" aria-hidden="true"></iconify-icon>
              {{ ing }}
            </li>
          </ul>
        </div>
      </div>
    </div>
  </template>
</template>
