<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { useCartStore } from '@/stores/cart'
import { useMenuStore } from '@/stores/menu'
import { money } from '@/utils/format'

/**
 * 结算页。
 *
 * 金额一律由 `POST /api/orders/quote` 算出来，前端**不做任何加减**。
 * 演示版在这里出过事故：页面自己按「堂食加 5%」算一遍，
 * 订单详情页又对已经含服务费的合计乘了一次 1.05，同一个订单两个价。
 * 现在收费规则只存在于后端的 FeeCalculator 里，前端只负责显示它算出来的数。
 */
const router = useRouter()
const cart = useCartStore()
const menu = useMenuStore()

const dineMode = ref('DINE_IN')
const payMethod = ref('微信支付')
const remark = ref('')
const submitting = ref(false)

const quote = ref(null)
const quoteError = ref('')
const quoting = ref(false)
const tables = ref([])

const PAY_METHODS = ['微信支付', '支付宝', '到店付款']

const isEmpty = computed(() => cart.lines.length === 0)
const canSubmit = computed(
  () => !isEmpty.value && quote.value && !quoting.value && !submitting.value,
)

/** 请求体只包含身份和数量，**不带价格** —— 价格以服务端为准 */
function body() {
  return {
    tableCode: dineMode.value === 'TAKEOUT' ? null : cart.table,
    dineMode: dineMode.value,
    payMethod: payMethod.value,
    remark: remark.value.trim(),
    items: cart.toRequestItems(),
  }
}

async function refreshQuote() {
  if (isEmpty.value) {
    quote.value = null
    return
  }
  quoting.value = true
  quoteError.value = ''
  try {
    quote.value = await api.quoteOrder(body())
  } catch (e) {
    quote.value = null
    quoteError.value = e.message || '算价失败'
  } finally {
    quoting.value = false
  }
}

// 就餐方式一改就要重算：堂食按小计原价，自取有立减，两个口径不同
watch(dineMode, refreshQuote)

onMounted(async () => {
  try {
    await menu.load()
  } catch {
    /* 菜单拿不到时报价会失败，下面有错误提示 */
  }
  try {
    tables.value = await api.listTables()
  } catch {
    tables.value = []
  }
  await refreshQuote()
})

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const order = await api.createOrder(body())
    cart.clear()
    ElMessage.success('下单成功，后厨已经收到')
    // 跳到订单详情。那里的金额是**服务端落库后读回来的**，
    // 所以用户看到的实付金额和结算页报价一致是必然的，不靠前端记性
    router.replace({ name: 'order', params: { orderNo: order.orderNo } })
  } catch (e) {
    ElMessage.error(e.message || '下单失败')
    // 失败很可能是库存不够，重新算一次让用户看到最新情况
    await refreshQuote()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-head">
    <div>
      <h1>结算</h1>
      <p>确认就餐方式和桌号，然后提交给后厨</p>
    </div>
  </div>

  <div v-if="isEmpty" class="empty-state">
    <h3>点单栏是空的</h3>
    <p>先去点几道菜再来结算。</p>
    <RouterLink class="btn btn-primary" :to="{ name: 'menu' }">去点菜</RouterLink>
  </div>

  <div v-else class="sk-split">
    <div>
      <div class="sk-card">
        <h2>就餐方式</h2>
        <div class="filter-chips">
          <button
            type="button"
            class="spec-opt"
            :class="{ 'is-on': dineMode === 'DINE_IN' }"
            :aria-pressed="dineMode === 'DINE_IN'"
            @click="dineMode = 'DINE_IN'"
          >
            堂食
          </button>
          <button
            type="button"
            class="spec-opt"
            :class="{ 'is-on': dineMode === 'TAKEOUT' }"
            :aria-pressed="dineMode === 'TAKEOUT'"
            @click="dineMode = 'TAKEOUT'"
          >
            到店自取
          </button>
        </div>
        <p style="margin: 10px 0 0; font-size: var(--fs-sm); color: var(--ink-3)">
          {{
            dineMode === 'DINE_IN'
              ? '不收服务费，按菜品小计结算。'
              : '自己来拿省了服务成本，小计再减 2%（最多 4 元）。'
          }}
        </p>
      </div>

      <div v-if="dineMode === 'DINE_IN'" class="sk-card">
        <h2>桌号</h2>
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

      <div class="sk-card">
        <h2>支付方式</h2>
        <div class="filter-chips">
          <button
            v-for="p in PAY_METHODS"
            :key="p"
            type="button"
            class="spec-opt"
            :class="{ 'is-on': payMethod === p }"
            :aria-pressed="payMethod === p"
            @click="payMethod = p"
          >
            {{ p }}
          </button>
        </div>
      </div>

      <div class="sk-card">
        <h2>备注</h2>
        <label class="sr-only" for="remark">订单备注</label>
        <textarea
          id="remark"
          v-model="remark"
          rows="3"
          maxlength="255"
          placeholder="比如「麻婆豆腐少放花椒」"
          style="width: 100%; padding: 10px; border: 1px solid var(--line); border-radius: var(--r-sm); font: inherit; resize: vertical"
        ></textarea>
        <p style="margin: 6px 0 0; font-size: var(--fs-xs); color: var(--ink-3)">
          备注会直接打在后厨的单子上。
        </p>
      </div>
    </div>

    <div>
      <div class="summary-card">
        <h2 style="margin: 0 0 12px">金额明细</h2>

        <div v-if="quoting" class="sk-skeleton" style="height: 16px; margin-bottom: 10px"></div>
        <p v-else-if="quoteError" style="color: var(--cinnabar); margin: 0 0 10px">
          {{ quoteError }}
        </p>

        <template v-else-if="quote">
          <ul style="list-style: none; margin: 0 0 12px; padding: 0; font-size: var(--fs-sm)">
            <li
              v-for="(l, i) in quote.lines"
              :key="i"
              style="display: flex; justify-content: space-between; gap: 10px; padding: 4px 0"
            >
              <span>
                {{ l.dishName }}
                <span v-if="l.specName" style="color: var(--ink-3)">· {{ l.specName }}</span>
                <span style="color: var(--ink-3)"> × {{ l.qty }}</span>
              </span>
              <span class="sk-num">¥{{ money(l.amount) }}</span>
            </li>
          </ul>

          <div class="summary-body">
            <div class="summary-row">
              <span>菜品小计</span>
              <span class="sk-num">¥{{ money(quote.subtotal) }}</span>
            </div>
            <!-- 服务费当前恒为 0，就不占一行了。真收了会自动出现 -->
            <div v-if="Number(quote.serviceFee) > 0" class="summary-row">
              <span>服务费</span>
              <span class="sk-num">¥{{ money(quote.serviceFee) }}</span>
            </div>
            <div v-if="Number(quote.discount) > 0" class="summary-row" style="color: var(--cinnabar)">
              <span>自提立减（2%，最多 4 元）</span>
              <span class="sk-num">−¥{{ money(quote.discount) }}</span>
            </div>
          </div>

          <div class="summary-row" style="border-top: 2px solid var(--ink); padding-top: 12px; margin-top: 10px">
            <span class="total-label">应 付</span>
            <span class="total-value">¥{{ money(quote.total) }}</span>
          </div>
        </template>

        <button
          class="btn btn-primary btn-block btn-lg"
          type="button"
          style="margin-top: 14px"
          :disabled="!canSubmit"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>

        <p style="margin: 10px 0 0; font-size: var(--fs-xs); color: var(--ink-3); text-align: center">
          提交后后厨会立刻收到单子
        </p>
      </div>
    </div>
  </div>
</template>
