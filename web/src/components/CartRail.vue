<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useMenuStore } from '@/stores/menu'
import { money } from '@/utils/format'

/**
 * 点单栏 —— 全站唯一的记忆点，做成「点心单」的样子。
 *
 * 结构和类名照搬 demo 的 `renderOrderRail()`：竖朱线、条目虚线、
 * 数量方框、双线合计，加购时盖一下朱砂圆章。
 * 这一块是设计里花了最多心思的地方，不要重画。
 *
 * 行的单价由菜单 store 的菜品 + 规格算出来，**不在购物车里存价格** ——
 * 存了就会和后台改价后的菜单对不上。
 */
const router = useRouter()
const cart = useCartStore()
const menu = useMenuStore()

/** 把购物车的行还原成可展示的条目。菜品被下架时那一行会消失，所以要过滤 */
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
        amount: unit * l.qty,
      }
    })
    .filter(Boolean),
)

const subtotal = computed(() => rows.value.reduce((s, r) => s + r.amount, 0))

function goCheckout() {
  router.push({ name: 'checkout' })
}
</script>

<template>
  <aside class="order-rail" aria-label="当前点单">
    <div class="order-rail-head">
      <h2>点 单</h2>
      <span class="table-no">{{ cart.table }} 桌</span>
    </div>

    <div v-if="!rows.length" class="order-empty">
      <img src="/illustrations/add_to_cart_vkjp.svg" alt="" />
      <p>还没点菜。左侧菜单里点「+」就加进来了。</p>
    </div>

    <template v-else>
      <div class="order-rail-body">
        <div v-for="r in rows" :key="r.key" class="order-line">
          <div class="order-line-name">
            {{ r.dish.name }}
            <span class="order-line-spec">{{ r.specName }}</span>
          </div>
          <span class="order-line-price">¥{{ money(r.amount) }}</span>
          <div class="qty-box">
            <button type="button" :aria-label="`减少 ${r.dish.name}`" @click="cart.changeQty(r.line, -1)">
              <iconify-icon icon="mdi:minus" width="14" height="14"></iconify-icon>
            </button>
            <span class="qty">{{ r.line.qty }}</span>
            <button type="button" :aria-label="`增加 ${r.dish.name}`" @click="cart.changeQty(r.line, 1)">
              <iconify-icon icon="mdi:plus" width="14" height="14"></iconify-icon>
            </button>
          </div>
        </div>
      </div>

      <div class="order-rail-foot">
        <div class="sub-row">
          <span>共 {{ cart.count }} 件</span>
          <span>餐位费 ¥0</span>
        </div>
        <div class="total-row">
          <span class="total-label">合 计</span>
          <span class="total-value">¥{{ money(subtotal) }}</span>
        </div>
        <button
          class="btn btn-primary btn-block btn-lg"
          type="button"
          style="margin-top: 12px"
          @click="goCheckout"
        >
          <iconify-icon icon="mdi:receipt-text-outline" width="19" height="19"></iconify-icon>
          去结算
        </button>
      </div>
    </template>
  </aside>
</template>
