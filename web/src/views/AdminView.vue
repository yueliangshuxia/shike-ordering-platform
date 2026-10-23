<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { money } from '@/utils/format'
import {
  CARD, CINNABAR, INK, INK_2, INK_3, PINE,
  axisCommon, gridCommon, splitLineCommon, tooltipCommon,
} from '@/assets/chart-theme'

/**
 * 经营看板。
 *
 * 两个图各自只画一个系列（营业额、分类销售额），所以**都不需要图例** ——
 * 标题已经说明了画的是什么，再加一个只有一项的图例框纯属占地方。
 *
 * 分类占比用的是横向条形图而不是饼图：饼图比不出相近的值，
 * 22% 和 18% 两块斜边看着差不多，而横条的长度是能直接比出来的。
 * 分类名已经写在轴上，所以条不用按分类上色 —— 那样只是装饰，
 * 不传递任何额外信息，反而让人以为颜色有含义。
 */
echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const data = ref(null)
const dishes = ref([])
const loading = ref(true)
const errorMsg = ref('')
const showTable = ref(false)

const trendEl = ref(null)
const shareEl = ref(null)
let trendChart = null
let shareChart = null

const change = computed(() => {
  const c = data.value?.revenueChange
  if (c === null || c === undefined) return null
  return Math.round(c * 1000) / 10
})

const statusLabel = (k) =>
  ({ PENDING: '待接单', COOKING: '制作中', SERVED: '已上菜', DONE: '已完成', CANCELLED: '已取消' }[k] || k)

/* ---------- 图表 ---------- */

function renderTrend() {
  if (!trendEl.value || !trendChart) return
  const points = data.value?.revenueTrend || []
  trendChart.setOption({
    // 关掉动画：这是看板不是演示页，改个库存就重画一遍、柱子还带缓动，
    // 只会让人觉得数据在跳。数字直接到位反而更可信
    animation: false,
    grid: gridCommon,
    tooltip: {
      ...tooltipCommon,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: INK_3, width: 1, type: 'dashed' } },
      formatter: (ps) => {
        const p = ps[0]
        const row = points[p.dataIndex]
        return `${row.label}<br/>营业额 <b>¥${money(row.amount)}</b><br/>订单 ${row.orders} 单`
      },
    },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.label),
      ...axisCommon,
      boundaryGap: false,
      axisLabel: { ...axisCommon.axisLabel, interval: 1 },
    },
    yAxis: {
      type: 'value',
      ...axisCommon,
      axisLine: { show: false },
      splitLine: splitLineCommon,
      axisLabel: { ...axisCommon.axisLabel, formatter: (v) => (v >= 10000 ? `${v / 10000}万` : v) },
    },
    series: [
      {
        name: '营业额',
        type: 'line',
        data: points.map((p) => Number(p.amount)),
        // 细笔画 + 8px 标记点 + 2px 白色描边，避免点被线吃掉
        lineStyle: { color: PINE, width: 2 },
        itemStyle: { color: PINE, borderColor: CARD, borderWidth: 2 },
        symbol: 'circle',
        symbolSize: 8,
        showSymbol: points.length <= 20,
        smooth: false,
        areaStyle: { color: 'rgba(30, 64, 52, .06)' },
      },
    ],
  })
}

function renderShare() {
  if (!shareEl.value || !shareChart) return
  const rows = [...(data.value?.categoryShare || [])].sort(
    (a, b) => Number(a.amount) - Number(b.amount),
  )
  shareChart.setOption({
    animation: false,
    // right 要留出条形末端那个金额标签的位置。containLabel 只管轴标签，
    // 不管系列自己的 label —— 不留的话最长的那个条会把金额顶出卡片外面
    grid: { ...gridCommon, left: 4, right: 64 },
    tooltip: {
      ...tooltipCommon,
      trigger: 'item',
      formatter: (p) => `${p.name}<br/>销售额 <b>¥${money(p.value)}</b>`,
    },
    xAxis: {
      type: 'value',
      ...axisCommon,
      axisLine: { show: false },
      splitLine: splitLineCommon,
      axisLabel: { ...axisCommon.axisLabel, formatter: (v) => (v >= 10000 ? `${v / 10000}万` : v) },
    },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      ...axisCommon,
      axisLine: { show: false },
    },
    series: [
      {
        name: '销售额',
        type: 'bar',
        data: rows.map((r) => Number(r.amount)),
        // 数据端（远离基线的那一头）4px 圆角，基线那头保持方角
        itemStyle: { color: PINE, borderRadius: [0, 4, 4, 0] },
        barMaxWidth: 22,
        // 直接标数值。分类只有六七个，标得下，而且省掉一次"看轴估长度"
        label: {
          show: true,
          position: 'right',
          color: INK_2,
          fontSize: 12,
          formatter: (p) => `¥${money(p.value)}`,
        },
      },
    ],
  })
}

function resize() {
  trendChart?.resize()
  shareChart?.resize()
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [dash, ds] = await Promise.all([api.dashboard(), api.adminDishes()])
    data.value = dash
    dishes.value = ds
  } catch (e) {
    errorMsg.value = e.message || '看板加载失败'
  } finally {
    loading.value = false
  }

  // 初始化必须等 loading 变成 false 之后。
  // 图表容器长在 `v-else-if="data"` 那个分支里，而这个分支只有
  // 不处于 loading 时才会渲染 —— 放在 try 里面执行的话，
  // 这一刻页面上还是骨架屏，ref 拿到的是 null，
  // echarts.init(null) 静默失败，两个图就永远是空的，而且不报错。
  // 这个 bug 只有真正打开页面看一眼才会发现，构建和接口测试都抓不到。
  await nextTick()
  initCharts()
}

function initCharts() {
  if (trendEl.value && !trendChart) trendChart = echarts.init(trendEl.value)
  if (shareEl.value && !shareChart) shareChart = echarts.init(shareEl.value)
  renderTrend()
  renderShare()
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  shareChart?.dispose()
  trendChart = null
  shareChart = null
})

watch(showTable, async () => {
  // 表格视图会把图表容器隐藏再显示，重新显示时 ECharts 量不到宽度，
  // 画布会停在 0 宽。等 DOM 更新完 resize 一次
  await nextTick()
  resize()
})

/* ---------- 菜品维护 ---------- */
const editing = ref({})

async function saveDish(dish) {
  const price = editing.value[`${dish.code}:price`]
  const stock = editing.value[`${dish.code}:stock`]
  if (price === undefined && stock === undefined) return
  try {
    const updated = await api.adminUpdateDish(dish.code, {
      price: price === undefined ? null : Number(price),
      stock: stock === undefined ? null : Number(stock),
    })
    const i = dishes.value.findIndex((d) => d.code === dish.code)
    if (i >= 0) dishes.value[i] = updated
    delete editing.value[`${dish.code}:price`]
    delete editing.value[`${dish.code}:stock`]
    ElMessage.success(`${dish.name} 已更新`)
    // 库存改了，「已售罄」的计数跟着变
    data.value = await api.dashboard()
    renderTrend()
    renderShare()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

/** 上架 / 下架。传的是目标状态，不是「取反当前状态」——
 *  后者在连点两次或并发时会反转成和界面相反的结果 */
async function setEnabled(dish, enabled) {
  try {
    const updated = await api.adminToggleDish(dish.code, enabled)
    const i = dishes.value.findIndex((d) => d.code === dish.code)
    if (i >= 0) dishes.value[i] = updated
    ElMessage.success(enabled ? `${dish.name} 已上架` : `${dish.name} 已下架`)
    // 分类计数会跟着变
    data.value = await api.dashboard()
    renderShare()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}
</script>

<template>
  <div class="page-head">
    <div>
      <h1>经营看板</h1>
      <p>今日经营情况与近 14 天走势</p>
    </div>
    <button class="btn btn-ghost" type="button" @click="showTable = !showTable">
      {{ showTable ? '看图表' : '看数据表' }}
    </button>
  </div>

  <div v-if="loading" class="sk-card">
    <div class="sk-skeleton" style="height: 20px; width: 40%"></div>
  </div>

  <div v-else-if="errorMsg" class="empty-state">
    <h3>看板加载失败</h3>
    <p>{{ errorMsg }}</p>
    <button class="btn btn-primary" type="button" @click="load">重试</button>
  </div>

  <template v-else-if="data">
    <div class="stat-row">
      <div class="stat">
        <div class="stat-label">今日营业额</div>
        <div class="stat-value sk-num">¥{{ money(data.todayRevenue) }}</div>
        <div class="stat-delta" :style="{ color: change === null ? INK_3 : change >= 0 ? '#3E7A5E' : CINNABAR }">
          <template v-if="change === null">昨日无可比数据</template>
          <template v-else>
            {{ change >= 0 ? '↑' : '↓' }} {{ Math.abs(change) }}% 较昨日
          </template>
        </div>
      </div>

      <div class="stat">
        <div class="stat-label">今日订单数</div>
        <div class="stat-value sk-num">{{ data.todayOrders }}</div>
        <div class="stat-delta" style="color: var(--ink-3)">含全部状态</div>
      </div>

      <div class="stat">
        <div class="stat-label">客单价</div>
        <div class="stat-value sk-num">¥{{ money(data.avgTicket) }}</div>
        <div class="stat-delta" style="color: var(--ink-3)">营业额 ÷ 单数</div>
      </div>

      <div class="stat">
        <div class="stat-label">待处理</div>
        <div class="stat-value sk-num">{{ data.pendingOrders }}</div>
        <div class="stat-delta" style="color: var(--ink-3)">待接单 + 制作中</div>
      </div>

      <div class="stat">
        <div class="stat-label">售罄菜品</div>
        <div class="stat-value sk-num">{{ data.soldOutDishes }}</div>
        <div class="stat-delta" style="color: var(--ink-3)">需要在后台补货</div>
      </div>
    </div>

    <div class="two-col" style="margin-top: 16px">
      <div class="chart-box">
        <div class="sec-head">
          <h2>近 14 天营业额</h2>
          <span style="font-size: var(--fs-xs); color: var(--ink-3)">单位：元</span>
        </div>

        <div v-show="!showTable" ref="trendEl" style="height: 260px"></div>

        <div v-if="showTable" class="table-wrap" style="max-height: 260px; overflow: auto">
          <table style="width: 100%; border-collapse: collapse; font-size: var(--fs-sm)">
            <thead>
              <tr style="text-align: left; color: var(--ink-3)">
                <th style="padding: 6px 4px">日期</th>
                <th style="padding: 6px 4px; text-align: right">营业额</th>
                <th style="padding: 6px 4px; text-align: right">订单数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in data.revenueTrend" :key="p.date" style="border-top: 1px solid var(--line-2)">
                <td style="padding: 6px 4px">{{ p.label }}</td>
                <td style="padding: 6px 4px; text-align: right" class="sk-num">¥{{ money(p.amount) }}</td>
                <td style="padding: 6px 4px; text-align: right" class="sk-num">{{ p.orders }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="chart-box">
        <div class="sec-head">
          <h2>分类销售额</h2>
          <span style="font-size: var(--fs-xs); color: var(--ink-3)">近 14 天</span>
        </div>

        <div v-show="!showTable" ref="shareEl" style="height: 260px"></div>

        <div v-if="showTable" class="table-wrap" style="max-height: 260px; overflow: auto">
          <table style="width: 100%; border-collapse: collapse; font-size: var(--fs-sm)">
            <thead>
              <tr style="text-align: left; color: var(--ink-3)">
                <th style="padding: 6px 4px">分类</th>
                <th style="padding: 6px 4px; text-align: right">销售额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in data.categoryShare" :key="r.name" style="border-top: 1px solid var(--line-2)">
                <td style="padding: 6px 4px">{{ r.name }}</td>
                <td style="padding: 6px 4px; text-align: right" class="sk-num">¥{{ money(r.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="sk-card" style="margin-top: 16px">
      <div class="sec-head">
        <h2>近 14 天订单状态分布</h2>
      </div>
      <div class="filter-chips">
        <span v-for="(v, k) in data.statusCount" :key="k" class="sk-tag sk-tag--plain">
          {{ statusLabel(k) }} {{ v }}
        </span>
      </div>
    </div>

    <div class="sk-card" style="margin-top: 16px">
      <div class="sec-head">
        <h2>菜品维护</h2>
        <span style="font-size: var(--fs-xs); color: var(--ink-3)">
          只开放价格和库存 —— 改菜名或食材会影响推荐算法的输入，要改请改 data.js
        </span>
      </div>

      <div class="table-wrap" style="max-height: 520px; overflow: auto">
        <table style="width: 100%; border-collapse: collapse; font-size: var(--fs-sm)">
          <thead>
            <tr style="text-align: left; color: var(--ink-3); position: sticky; top: 0; background: var(--card)">
              <th style="padding: 8px 6px">菜品</th>
              <th style="padding: 8px 6px">分类</th>
              <th style="padding: 8px 6px; text-align: right">价格</th>
              <th style="padding: 8px 6px; text-align: right">库存</th>
              <th style="padding: 8px 6px; text-align: right">月售</th>
              <th style="padding: 8px 6px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in dishes" :key="d.code" style="border-top: 1px solid var(--line-2)">
              <td style="padding: 8px 6px">
                {{ d.name }}
                <span v-if="!d.enabled" class="sk-tag sk-tag--plain">已下架</span>
                <span
                  v-else-if="d.soldOut"
                  class="sk-tag"
                  style="background: var(--cinnabar-soft); color: var(--cinnabar-dk)"
                >
                  售罄
                </span>
              </td>
              <td style="padding: 8px 6px; color: var(--ink-3)">{{ d.categoryId }}</td>
              <td style="padding: 8px 6px; text-align: right">
                <input
                  type="number"
                  min="0"
                  step="1"
                  :value="d.price"
                  :aria-label="`${d.name} 的价格`"
                  style="width: 84px; text-align: right; padding: 5px"
                  @input="editing[`${d.code}:price`] = $event.target.value"
                />
              </td>
              <td style="padding: 8px 6px; text-align: right">
                <input
                  type="number"
                  min="0"
                  step="1"
                  :value="d.stock"
                  :aria-label="`${d.name} 的库存`"
                  style="width: 84px; text-align: right; padding: 5px"
                  @input="editing[`${d.code}:stock`] = $event.target.value"
                />
              </td>
              <td style="padding: 8px 6px; text-align: right" class="sk-num">{{ d.sold }}</td>
              <td style="padding: 8px 6px; text-align: right; white-space: nowrap">
                <button class="btn btn-ghost btn-sm" type="button" @click="saveDish(d)">保存</button>
                <button
                  class="btn btn-ghost btn-sm"
                  type="button"
                  @click="setEnabled(d, !d.enabled)"
                >
                  {{ d.enabled ? '下架' : '上架' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>
