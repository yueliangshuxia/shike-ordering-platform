<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { getToken } from '@/api/client'
import { relativeMinutes } from '@/utils/format'
import '@/assets/kitchen.css'

/**
 * 后厨出单屏（KDS）。
 *
 * <p>这一页挂在厨房的墙上，看的人手上可能是油、离屏两三米，
 * 所以：整体反色、字号放大、按等待时长排序、等越久边框越红。
 *
 * <p>实时性靠 WebSocket：订单一变，后端推一条「变了」，
 * 这里重新拉一遍完整列表。**推送里不带订单数据** ——
 * 客户端拿到的始终是接口返回的、和数据库一致的那一份，
 * 不会出现「推送里少带一个字段、界面和列表页不一致」的问题。
 */
const orders = ref([])
const stats = ref({ doneToday: 0, pendingCount: 0, activeCount: 0, maxWaitMinutes: 0 })
const loading = ref(true)
const errorMsg = ref('')
const connected = ref(false)
const now = ref(Date.now())

let ws = null
let retryTimer = null
let clockTimer = null
let closing = false

/** 等待时长要跟着秒针走，否则一张单挂在那儿数字永远不动 */
function waitOf(order) {
  return relativeMinutes(order.createdAt)
}

function waitClass(minutes) {
  if (minutes >= 15) return 'w-late'
  if (minutes >= 8) return 'w-warn'
  return ''
}

const sorted = computed(() =>
  [...orders.value].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)),
)

const nextAction = (order) => {
  switch (order.status) {
    case 'PENDING':
      return { to: 'COOKING', label: '接单开工', cls: 'is-primary' }
    case 'COOKING':
      return { to: 'SERVED', label: '出餐', cls: 'is-primary' }
    case 'SERVED':
      return { to: 'DONE', label: '已上菜', cls: 'is-done' }
    default:
      return null
  }
}

async function refresh() {
  try {
    const [list, s] = await Promise.all([api.kitchenOrders(), api.kitchenStats()])
    orders.value = list
    stats.value = s
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function advance(order) {
  const action = nextAction(order)
  if (!action) return
  // 先本地改掉，界面立刻有反馈；失败了再拉一次真实状态盖回去。
  // 后厨不可能等一个网络往返才看到按钮响应
  order.status = action.to
  try {
    await api.advanceOrder(order.orderNo, action.to)
    await refresh()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
    await refresh()
  }
}

function connect() {
  if (closing) return
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  // 令牌通过查询串带上：WebSocket 握手没法自定义请求头，
  // 而浏览器又不会自动带上 Authorization
  const url = `${proto}//${location.host}/ws/kitchen?token=${encodeURIComponent(getToken())}`
  ws = new WebSocket(url)

  ws.onopen = () => {
    connected.value = true
  }

  ws.onmessage = () => {
    // 收到的是「变了」，数据重新拉 —— 见文件头的说明
    refresh()
  }

  ws.onclose = () => {
    connected.value = false
    if (closing) return
    // 断线重连。后厨的路由器重启、网线被人碰掉都很常见，
    // 不重连的话屏上就永远停在断开那一刻的菜单，厨师照着做会出错
    retryTimer = setTimeout(connect, 4000)
  }

  ws.onerror = () => {
    ws?.close()
  }
}

onMounted(async () => {
  document.body.classList.add('kds')
  await refresh()
  connect()
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 20000)
})

onBeforeUnmount(() => {
  closing = true
  document.body.classList.remove('kds')
  clearTimeout(retryTimer)
  clearInterval(clockTimer)
  ws?.close()
})
</script>

<template>
  <div class="kds-bar">
    <h1>后厨出单</h1>

    <span class="kds-stat">
      待接单 <b>{{ stats.pendingCount }}</b>
    </span>
    <span class="kds-stat">
      制作中 <b>{{ stats.activeCount }}</b>
    </span>
    <span class="kds-stat">
      今日已出 <b>{{ stats.doneToday }}</b>
    </span>
    <span class="kds-stat">
      最长等待 <b>{{ sorted.length ? Math.max(...sorted.map(waitOf)) : 0 }}</b> 分
    </span>

    <span
      class="kds-stat"
      :style="{ color: connected ? '#6FBF8F' : '#E8705C', borderColor: connected ? '#3E7A5E' : '#5C322B' }"
    >
      {{ connected ? '实时已连接' : '连接断开，重连中' }}
    </span>

    <span class="kds-clock">
      {{ new Date(now).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}
    </span>
  </div>

  <div v-if="loading" class="kds-grid">
    <div v-for="i in 3" :key="i" class="ticket" style="padding: 20px">
      <div class="sk-skeleton" style="height: 22px; width: 45%"></div>
      <div class="sk-skeleton" style="height: 14px; margin-top: 12px"></div>
    </div>
  </div>

  <div v-else-if="errorMsg" class="kds-grid">
    <div class="kds-empty">
      <h2>加载失败</h2>
      <p>{{ errorMsg }}</p>
      <button class="kds-btn is-primary" type="button" @click="refresh">重试</button>
    </div>
  </div>

  <div v-else class="kds-grid">
    <div v-if="!sorted.length" class="kds-empty">
      <h2 style="color: #8fa79b; letter-spacing: 0.2em">队列清空</h2>
      <p>当前没有待处理的单子。</p>
      <p style="font-size: var(--fs-sm)">今日已出 {{ stats.doneToday }} 单</p>
    </div>

    <article
      v-for="o in sorted"
      :key="o.orderNo"
      class="ticket"
      :class="waitClass(waitOf(o))"
    >
      <div class="ticket-head">
        <span class="t-table">{{ o.tableCode || '自取' }}{{ o.tableCode ? ' 桌' : '' }}</span>
        <span class="t-no">{{ o.orderNo }}</span>
        <span class="t-wait">{{ waitOf(o) }}′</span>
      </div>

      <div class="ticket-items">
        <div v-for="it in o.items" :key="`${it.dishName}-${it.specName}`" class="t-item">
          <span class="t-qty">×{{ it.qty }}</span>
          <span class="t-name">
            {{ it.dishName }}
            <span v-if="it.specName" style="color: #8fa79b; font-size: var(--fs-sm)">
              · {{ it.specName }}
            </span>
          </span>
        </div>
        <p v-if="o.remark" style="margin: 10px 0 0; color: #e0b45c; font-size: var(--fs-sm)">
          备注：{{ o.remark }}
        </p>
      </div>

      <div class="ticket-foot">
        <span class="t-status">{{ o.statusLabel }}</span>
        <button
          v-if="nextAction(o)"
          class="kds-btn"
          :class="nextAction(o).cls"
          type="button"
          style="margin-left: auto"
          @click="advance(o)"
        >
          {{ nextAction(o).label }}
        </button>
      </div>
    </article>
  </div>
</template>
