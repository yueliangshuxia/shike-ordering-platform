<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useMenuStore } from '@/stores/menu'
import * as api from '@/api'
import { money } from '@/utils/format'
import { dishPlaceholder } from '@/utils/placeholder'

/**
 * 购物车。
 *
 * 桌号在这里改。堂食的桌号是订单的一部分（后厨按桌号送菜），
 * 所以它在结算前必须确定下来 —— 不能在点单栏里随便改。
 */
const router = useRouter()
const cart = useCartStore()
const menu = useMenuStore()

const tables = ref([])
const loading = ref(true)

const rows = computed(() =>
  cart.lines
    .map((l) => {
      const dish = menu.dishByCode(l.dishCode)
      if (!dish) return null
      const spec = (dish.specs || []).find((s) => s.id === l.specId) || dish.specs?.[0] || null
      const unit = spec ? Number(spec.price) : Number(dish.price)
      return {
        key: `${l.dishCode}#${l.specId ?? ''}`,
        line: l,
        dish,
        specName: spec?.name || '标准',
        unit,
        amount: unit * l.qty,
        img: menu.imageUrl(dish) || dishPlaceholder(dish.name),
      }
    })
    .filter(Boolean),
)

const subtotal = computed(() => rows.value.reduce((s, r) => s + r.amount, 0))

/** 下架或售罄的行要提示出来，否则用户结账时才发现点不了 */
const problems = computed(() =>
  rows.value.filter((r) => r.dish.soldOut).map((r) => r.dish.name),
)

onMounted(async () => {
  try {
    await menu.load()
  } catch {
    /* 菜单拿不到时下面的 rows 会是空的，会走到空态 */
  }
  try {
    tables.value = await api.listTables()
  } catch {
    tables.value = []
  } finally {
    loading.value = false
  }
})

function toCheckout() {
  router.push({ name: 'checkout' })
}

function clearAll() {
  cart.clear()
}
</script>

<template>
  <div class="page-head">
    <div>
      <h1>我的点单</h1>
      <p>共 {{ cart.count }} 件 · 确认之后可以去结算</p>
    </div>
    <button v-if="rows.length" class="btn btn-ghost" type="button" @click="clearAll">清空</button>
  </div>

  <div v-if="loading" class="sk-card">
    <div class="sk-skeleton" style="height: 18px; width: 40%"></div>
  </div>

  <div v-else-if="!rows.length" class="empty-state">
    <img src="/illustrations/add_to_cart_vkjp.svg" alt="" style="max-width: 240px" />
    <h3>还没点菜</h3>
    <p>去菜单里挑几道，点「+」就加进来了。</p>
    <RouterLink class="btn btn-primary" :to="{ name: 'menu' }">去点菜</RouterLink>
  </div>

  <div v-else class="sk-split">
    <div class="sk-card">
      <table class="table-wrap" style="width: 100%; border-collapse: collapse">
        <thead>
          <tr style="text-align: left; font-size: var(--fs-sm); color: var(--ink-3)">
            <th style="padding: 8px 6px">菜品</th>
            <th style="padding: 8px 6px">单价</th>
            <th style="padding: 8px 6px">数量</th>
            <th style="padding: 8px 6px; text-align: right">小计</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.key" style="border-top: 1px solid var(--line-2)">
            <td style="padding: 12px 6px">
              <div class="sk-row">
                <img :src="r.img" :alt="r.dish.name" width="52" height="40" style="object-fit: cover; border-radius: var(--r-sm)" />
                <div>
                  <RouterLink :to="{ name: 'dish', params: { code: r.dish.code } }">
                    <strong>{{ r.dish.name }}</strong>
                  </RouterLink>
                  <div style="font-size: var(--fs-xs); color: var(--ink-3)">{{ r.specName }}</div>
                  <div v-if="r.dish.soldOut" style="font-size: var(--fs-xs); color: var(--cinnabar)">
                    已售罄，结算前请删掉
                  </div>
                </div>
              </div>
            </td>
            <td style="padding: 12px 6px">¥{{ money(r.unit) }}</td>
            <td style="padding: 12px 6px">
              <div class="qty-box" style="width: fit-content">
                <button type="button" :aria-label="`减少 ${r.dish.name}`" @click="cart.changeQty(r.line, -1)">
                  <iconify-icon icon="mdi:minus" width="14" height="14"></iconify-icon>
                </button>
                <span class="qty">{{ r.line.qty }}</span>
                <button type="button" :aria-label="`增加 ${r.dish.name}`" @click="cart.changeQty(r.line, 1)">
                  <iconify-icon icon="mdi:plus" width="14" height="14"></iconify-icon>
                </button>
              </div>
            </td>
            <td style="padding: 12px 6px; text-align: right" class="sk-num">¥{{ money(r.amount) }}</td>
            <td style="padding: 12px 6px; text-align: right">
              <button class="icon-btn" type="button" :aria-label="`删除 ${r.dish.name}`" @click="cart.remove(r.line)">
                <iconify-icon icon="mdi:trash-can-outline" width="16" height="16"></iconify-icon>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div>
      <div class="summary-card">
        <div class="summary-body">
          <div class="summary-row">
            <span>菜品小计</span>
            <span class="sk-num">¥{{ money(subtotal) }}</span>
          </div>
          <div class="summary-row" style="color: var(--ink-3); font-size: var(--fs-xs)">
            <span>服务费 / 自提立减</span>
            <span>结算时计算</span>
          </div>
        </div>

        <div class="summary-row" style="border-top: 2px solid var(--ink); padding-top: 12px">
          <span class="total-label">合 计</span>
          <span class="total-value">¥{{ money(subtotal) }}</span>
        </div>

        <p v-if="problems.length" style="margin: 12px 0 0; font-size: var(--fs-sm); color: var(--cinnabar)">
          有 {{ problems.length }} 道已售罄（{{ problems.join('、') }}），结算前请先删掉。
        </p>

        <button
          class="btn btn-primary btn-block btn-lg"
          type="button"
          style="margin-top: 14px"
          :disabled="problems.length > 0"
          @click="toCheckout"
        >
          去结算
        </button>
      </div>

      <div class="sk-card" style="margin-top: 16px">
        <h2>桌号</h2>
        <p style="margin: 0 0 10px; font-size: var(--fs-sm); color: var(--ink-3)">
          堂食要按桌送菜，所以先选好桌号。
        </p>
        <select
          :value="cart.table"
          class="spec-opt"
          style="width: 100%; padding: 9px"
          aria-label="选择桌号"
          @change="cart.setTable($event.target.value)"
        >
          <option v-for="t in tables" :key="t.code" :value="t.code">
            {{ t.code }} · {{ t.area }} · {{ t.seats }} 座
          </option>
          <option v-if="!tables.length" :value="cart.table">{{ cart.table }}</option>
        </select>
      </div>
    </div>
  </div>
</template>
