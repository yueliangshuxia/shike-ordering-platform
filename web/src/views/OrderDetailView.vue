<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { dateTime, money } from '@/utils/format'

/**
 * 订单详情。
 *
 * 这一页的金额**全部读订单表里存下来的值**，不做任何加减。
 * 演示版的 bug 就在这儿：页面对已经含服务费的合计又乘了一次 1.05，
 * 结算页报 ¥258.50、详情页显示 ¥262.50。
 * 现在金额只在服务端算一次并落库，详情页只负责显示。
 */
const route = useRoute()

const order = ref(null)
const loading = ref(true)
const errorMsg = ref('')

const STEPS = [
  { status: 'PENDING', label: '已下单', icon: 'mdi:receipt-text-outline' },
  { status: 'COOKING', label: '制作中', icon: 'mdi:chef-hat' },
  { status: 'SERVED', label: '已上菜', icon: 'mdi:silverware-fork-knife' },
  { status: 'DONE', label: '已完成', icon: 'mdi:check-circle-outline' },
]

const cancelled = computed(() => order.value?.status === 'CANCELLED')

/** 当前走到第几步。已取消的单不显示进度条 —— 它不是"没走完"，是另一条分支 */
const currentStep = computed(() => {
  if (!order.value) return -1
  return STEPS.findIndex((s) => s.status === order.value.status)
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    order.value = await api.getOrder(route.params.orderNo)
  } catch (e) {
    errorMsg.value = e.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.orderNo, load)

async function pay() {
  try {
    order.value = await api.payOrder(order.value.orderNo, '微信支付')
    ElMessage.success('已标记为已支付')
  } catch (e) {
    ElMessage.error(e.message || '支付失败')
  }
}

async function cancel() {
  try {
    order.value = await api.cancelOrder(order.value.orderNo)
    ElMessage.success('订单已取消，库存已退回')
  } catch (e) {
    ElMessage.error(e.message || '取消失败')
  }
}
</script>

<template>
  <nav class="crumb" aria-label="面包屑">
    <RouterLink :to="{ name: 'orders' }">我的订单</RouterLink>
    <iconify-icon icon="mdi:chevron-right" width="14" height="14" aria-hidden="true"></iconify-icon>
    <span>{{ route.params.orderNo }}</span>
  </nav>

  <div v-if="loading" class="sk-card">
    <div class="sk-skeleton" style="height: 20px; width: 40%"></div>
  </div>

  <div v-else-if="errorMsg" class="empty-state">
    <h3>订单没能加载出来</h3>
    <p>{{ errorMsg }}</p>
    <RouterLink class="btn btn-primary" :to="{ name: 'orders' }">回订单列表</RouterLink>
  </div>

  <template v-else-if="order">
    <div class="page-head">
      <div>
        <h1>{{ order.statusLabel }}</h1>
        <p>
          订单号 {{ order.orderNo }} · {{ dateTime(order.createdAt) }}
          <template v-if="order.tableCode"> · {{ order.tableCode }} 桌</template>
        </p>
      </div>
      <div class="sk-row">
        <button
          v-if="order.payMethod === '未支付' && !cancelled"
          class="btn btn-ghost"
          type="button"
          @click="pay"
        >
          标记为已支付
        </button>
        <button v-if="order.cancellable" class="btn btn-ghost" type="button" @click="cancel">
          取消订单
        </button>
      </div>
    </div>

    <div class="sk-split">
      <div>
        <div v-if="!cancelled" class="sk-card">
          <h2>进度</h2>
          <ul class="steps">
            <li
              v-for="(s, i) in STEPS"
              :key="s.status"
              class="step"
              :class="{ 'is-done': i <= currentStep, 'is-now': i === currentStep }"
            >
              <iconify-icon :icon="s.icon" width="18" height="18" aria-hidden="true"></iconify-icon>
              <span>{{ s.label }}</span>
            </li>
          </ul>
          <p style="margin: 12px 0 0; font-size: var(--fs-sm); color: var(--ink-3)">
            后厨屏上状态一变，这里刷新就能看到。
          </p>
        </div>

        <div v-else class="sk-card" style="border-color: var(--line)">
          <h2>订单已取消</h2>
          <p style="margin: 0; color: var(--ink-2)">
            这道单没有成交，占用的库存已经退回菜单。
          </p>
        </div>

        <div class="sk-card">
          <h2>菜品</h2>
          <ul style="list-style: none; margin: 0; padding: 0">
            <li
              v-for="it in order.items"
              :key="`${it.dishName}-${it.specName}`"
              class="sk-row-between"
              style="padding: 10px 0; border-bottom: 1px solid var(--line-2)"
            >
              <span>
                <strong>{{ it.dishName }}</strong>
                <span v-if="it.specName" style="color: var(--ink-3)"> · {{ it.specName }}</span>
                <div style="font-size: var(--fs-xs); color: var(--ink-3)">
                  ¥{{ money(it.unitPrice) }} × {{ it.qty }}
                </div>
              </span>
              <span class="sk-num">¥{{ money(it.amount) }}</span>
            </li>
          </ul>
        </div>

        <div v-if="order.remark" class="sk-card">
          <h2>备注</h2>
          <p style="margin: 0">{{ order.remark }}</p>
        </div>
      </div>

      <div>
        <div class="summary-card">
          <h2 style="margin: 0 0 12px">金额明细</h2>
          <div class="summary-body">
            <div class="summary-row">
              <span>菜品小计</span>
              <span class="sk-num">¥{{ money(order.subtotal) }}</span>
            </div>
            <div v-if="Number(order.serviceFee) > 0" class="summary-row">
              <span>服务费</span>
              <span class="sk-num">¥{{ money(order.serviceFee) }}</span>
            </div>
            <div v-if="Number(order.discount) > 0" class="summary-row" style="color: var(--cinnabar)">
              <span>自提立减</span>
              <span class="sk-num">−¥{{ money(order.discount) }}</span>
            </div>
          </div>

          <div class="summary-row" style="border-top: 2px solid var(--ink); padding-top: 12px; margin-top: 10px">
            <span class="total-label">实 付</span>
            <span class="total-value">¥{{ money(order.total) }}</span>
          </div>

          <div style="margin-top: 14px; font-size: var(--fs-sm); color: var(--ink-3)">
            <div class="sk-row-between">
              <span>支付方式</span>
              <span>{{ order.payMethod }}</span>
            </div>
            <div class="sk-row-between">
              <span>就餐方式</span>
              <span>{{ order.dineMode === 'TAKEOUT' ? '到店自取' : '堂食' }}</span>
            </div>
            <div v-if="order.tableCode" class="sk-row-between">
              <span>桌号</span>
              <span>{{ order.tableCode }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </template>
</template>
