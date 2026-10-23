<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { dateTime, money } from '@/utils/format'

/**
 * 我的订单。
 *
 * 状态标签的颜色直接按订单状态给，不做第二套映射 ——
 * 后端已经在 `statusLabel` 里给了中文说法，前端再翻译一次就会漂。
 */
const orders = ref([])
const loading = ref(true)
const errorMsg = ref('')
const filter = ref('all')

const STATUS_CLASS = {
  PENDING: 'sk-tag--signature',
  COOKING: 'sk-tag--signature',
  SERVED: 'sk-tag--veg',
  DONE: 'sk-tag--plain',
  CANCELLED: 'sk-tag--plain',
}

const shown = computed(() => {
  if (filter.value === 'all') return orders.value
  if (filter.value === 'active') return orders.value.filter((o) => o.cancellable)
  return orders.value.filter((o) => o.status === filter.value)
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    orders.value = await api.listMyOrders()
  } catch (e) {
    errorMsg.value = e.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function cancel(order) {
  try {
    const updated = await api.cancelOrder(order.orderNo)
    // 就地替换而不是整表重拉：只变了一条，整表重拉会让列表跳一下，
    // 用户刚点的那一行位置就变了
    const i = orders.value.findIndex((o) => o.orderNo === order.orderNo)
    if (i >= 0) orders.value[i] = updated
    ElMessage.success('订单已取消，库存已退回')
  } catch (e) {
    ElMessage.error(e.message || '取消失败')
    await load()
  }
}
</script>

<template>
  <div class="page-head">
    <div>
      <h1>我的订单</h1>
      <p>点过什么、花了多少，都在这儿</p>
    </div>
    <div class="filter-chips">
      <button
        v-for="f in [
          { key: 'all', label: '全部' },
          { key: 'active', label: '进行中' },
          { key: 'DONE', label: '已完成' },
        ]"
        :key="f.key"
        type="button"
        class="spec-opt"
        :class="{ 'is-on': filter === f.key }"
        :aria-pressed="filter === f.key"
        @click="filter = f.key"
      >
        {{ f.label }}
      </button>
    </div>
  </div>

  <div v-if="loading" class="sk-card">
    <div class="sk-skeleton" style="height: 18px; width: 45%"></div>
  </div>

  <div v-else-if="errorMsg" class="empty-state">
    <h3>订单没能加载出来</h3>
    <p>{{ errorMsg }}</p>
    <button class="btn btn-primary" type="button" @click="load">重试</button>
  </div>

  <div v-else-if="!shown.length" class="empty-state">
    <h3>{{ filter === 'all' ? '还没有订单' : '这一类里没有订单' }}</h3>
    <p>去点几道菜吧，下完单后厨会立刻收到。</p>
    <RouterLink class="btn btn-primary" :to="{ name: 'menu' }">去点菜</RouterLink>
  </div>

  <div v-else style="display: grid; gap: 14px">
    <article v-for="o in shown" :key="o.orderNo" class="order-card">
      <div class="order-card-head">
        <div>
          <RouterLink :to="{ name: 'order', params: { orderNo: o.orderNo } }">
            <strong>{{ o.orderNo }}</strong>
          </RouterLink>
          <div style="font-size: var(--fs-xs); color: var(--ink-3)">
            {{ dateTime(o.createdAt) }}
            <template v-if="o.tableCode"> · {{ o.tableCode }} 桌</template>
            · {{ o.dineMode === 'TAKEOUT' ? '到店自取' : '堂食' }}
          </div>
        </div>
        <span class="sk-tag" :class="STATUS_CLASS[o.status] || 'sk-tag--plain'">
          {{ o.statusLabel }}
        </span>
      </div>

      <div class="order-card-body">
        <ul style="list-style: none; margin: 0; padding: 0; font-size: var(--fs-sm)">
          <li v-for="it in o.items" :key="`${it.dishName}-${it.specName}`" class="sk-row-between" style="padding: 3px 0">
            <span>
              {{ it.dishName }}
              <span v-if="it.specName" style="color: var(--ink-3)">· {{ it.specName }}</span>
            </span>
            <span class="sk-num" style="color: var(--ink-3)">
              ¥{{ money(it.unitPrice) }} × {{ it.qty }}
            </span>
          </li>
        </ul>
      </div>

      <div class="order-card-foot">
        <span style="font-size: var(--fs-sm); color: var(--ink-3)">
          {{ o.payMethod }}
          <template v-if="Number(o.serviceFee) > 0"> · 含服务费 ¥{{ money(o.serviceFee) }}</template>
          <template v-if="Number(o.discount) > 0"> · 自提立减 −¥{{ money(o.discount) }}</template>
        </span>
        <span class="sk-row">
          <span class="price" style="font-size: var(--fs-md)">¥{{ money(o.total) }}</span>
          <button
            v-if="o.cancellable"
            class="btn btn-ghost btn-sm"
            type="button"
            @click="cancel(o)"
          >
            取消订单
          </button>
          <RouterLink class="btn btn-primary btn-sm" :to="{ name: 'order', params: { orderNo: o.orderNo } }">
            查看
          </RouterLink>
        </span>
      </div>
    </article>
  </div>
</template>
