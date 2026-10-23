<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as api from '@/api'
import { useMenuStore } from '@/stores/menu'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { money } from '@/utils/format'
import { dishPlaceholder } from '@/utils/placeholder'
import { ElMessage } from 'element-plus'

/**
 * 推荐实验室 —— 把算法的中间过程摊开给用户看。
 *
 * 这一页是答辩演示的核心：五根滑杆对应 AHP 算出来的五个准则权重，
 * 拖动之后 TOPSIS 重新排序，每一道菜的贴近度、到正负理想解的距离、
 * 以及「为什么是它」都会跟着变。
 *
 * 滑杆值**故意不归一化显示**（0–100 的整数），提交时才由后端归一化成和为 1。
 * 演示版在这里出过一个 bug：拖动时重画了滑杆本身，把正在拖的 DOM 节点换掉了，
 * 拖到一半就断。Vue 这边用 v-model 绑数组，天然不会发生 ——
 * 值的更新不会重建节点。
 */
const menu = useMenuStore()
const cart = useCartStore()
const user = useUserStore()

const KEYS = ['taste', 'price', 'hot', 'wait', 'healthy']

const sliders = reactive({ taste: 38, price: 25, hot: 19, wait: 12, healthy: 6 })
const taste = reactive({ maxSpice: 3, avoidSpice: false, avoid: [], prefer: [] })
const topK = ref(8)
const category = ref('')

const result = ref(null)
const loading = ref(true)
const errorMsg = ref('')
const ingredientOptions = ref([])

const images = computed(() =>
  Object.fromEntries(menu.dishes.map((d) => [d.code, menu.imageUrl(d) || dishPlaceholder(d.name)])),
)

const avoidText = ref('')

function addAvoid() {
  const v = avoidText.value.trim()
  if (v && !taste.avoid.includes(v)) taste.avoid.push(v)
  avoidText.value = ''
}

async function run() {
  loading.value = true
  errorMsg.value = ''
  try {
    result.value = await api.recommendWith({
      weights: KEYS.map((k) => Number(sliders[k])),
      maxSpice: taste.maxSpice,
      avoidSpice: taste.avoidSpice,
      avoidIngredients: taste.avoid,
      preferCategories: null,
      category: category.value || null,
      topK: topK.value,
    })
  } catch (e) {
    errorMsg.value = e.message || '推荐计算失败'
    result.value = null
  } finally {
    loading.value = false
  }
}

/** 恢复成 AHP 默认权重。数值就是论文表 3.3 算出来的那组 */
function resetWeights() {
  const w = result.value?.weights || []
  if (w.length === 5) {
    KEYS.forEach((k, i) => {
      sliders[k] = Math.round(w[i].value * 100)
    })
  } else {
    Object.assign(sliders, { taste: 38, price: 25, hot: 19, wait: 12, healthy: 6 })
  }
}

// 拖滑杆时不要每动一下就请求一次；等用户停手
let timer = null
watch(
  () => [sliders.taste, sliders.price, sliders.hot, sliders.wait, sliders.healthy, taste.maxSpice, taste.avoidSpice, taste.avoid.length, topK.value, category.value],
  () => {
    clearTimeout(timer)
    timer = setTimeout(run, 220)
  },
)

onMounted(async () => {
  try {
    await menu.load()
    if (user.loggedIn) {
      const t = await api.getTaste()
      taste.maxSpice = t.maxSpice
      taste.avoidSpice = t.avoidSpice
      taste.avoid = [...t.avoidIngredients]
      taste.prefer = [...t.preferCategories]
      ingredientOptions.value = t.ingredientOptions || []
    }
  } catch {
    /* 未登录或菜单拿不到，用默认值继续 —— 这一页必须对游客可用 */
  }
  await run()
})

function addFirst(d) {
  cart.add({ code: d.code, ...d }, null, 1)
  ElMessage({ message: `已加入点单：${d.name}`, type: 'success', duration: 1200 })
}
</script>

<template>
  <div class="page-head">
    <div>
      <h1>推荐实验室</h1>
      <p>
        五个准则的权重由 AHP 层次分析法算出，再用 TOPSIS 对每道菜算贴近度排先后。
        拖动滑杆能实时看到排序怎么变。
      </p>
    </div>
  </div>

  <div class="sk-split">
    <div>
      <div v-if="loading && !result" class="sk-card">
        <div class="sk-skeleton" style="height: 20px; width: 50%"></div>
        <div class="sk-skeleton" style="height: 14px; margin-top: 12px"></div>
      </div>

      <div v-else-if="errorMsg" class="empty-state">
        <h3>推荐算不出来</h3>
        <p>{{ errorMsg }}</p>
        <button class="btn btn-primary" type="button" @click="run">重试</button>
      </div>

      <template v-else-if="result">
        <div
          v-for="d in result.dishes"
          :key="d.code"
          class="sk-card"
          style="display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 16px"
        >
          <img
            :src="images[d.code]"
            :alt="d.name"
            style="width: 96px; height: 76px; object-fit: cover; border-radius: var(--r-sm)"
          />

          <div>
            <div class="sk-row-between">
              <h3 style="margin: 0; font-size: var(--fs-md)">
                <span class="sk-tag sk-tag--signature" style="margin-right: 8px">#{{ d.rank }}</span>
                <RouterLink :to="{ name: 'dish', params: { code: d.code } }">{{ d.name }}</RouterLink>
              </h3>
              <span class="price"><span class="cur">¥</span>{{ money(d.price) }}</span>
            </div>

            <p style="margin: 6px 0; color: var(--ink-3); font-size: var(--fs-sm)">{{ d.brief }}</p>

            <ul style="margin: 6px 0 0; padding-left: 18px; font-size: var(--fs-sm); color: var(--ink-2)">
              <li v-for="r in d.reasons" :key="r">{{ r }}</li>
            </ul>
            <p
              v-for="c in d.caveats"
              :key="c"
              style="margin: 4px 0 0; font-size: var(--fs-sm); color: var(--gold)"
            >
              {{ c }}
            </p>

            <div
              class="sk-row"
              style="flex-wrap: wrap; gap: 14px; margin-top: 10px; font-size: var(--fs-xs); color: var(--ink-3)"
            >
              <span class="sk-num">贴近度 C = {{ d.closeness.toFixed(4) }}</span>
              <span class="sk-num">D⁺ = {{ d.dPlus.toFixed(4) }}</span>
              <span class="sk-num">D⁻ = {{ d.dMinus.toFixed(4) }}</span>
              <button class="btn btn-ghost btn-sm" type="button" @click="addFirst(d)">加入点单</button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <div>
      <div class="sk-card">
        <div class="sk-row-between">
          <h2 style="margin: 0">准则权重</h2>
          <button class="btn btn-ghost btn-sm" type="button" @click="resetWeights">还原 AHP</button>
        </div>
        <p style="margin: 6px 0 14px; font-size: var(--fs-xs); color: var(--ink-3)">
          提交时后端会归一化成和为 1，所以拖成什么样都不影响结果的有效性。
        </p>

        <div v-for="(c, i) in result?.weights || []" :key="c.key" class="field">
          <label :for="`w-${c.key}`" class="sk-row-between" style="display: flex">
            <span>{{ c.label }}</span>
            <b class="sk-num">{{ sliders[KEYS[i]] }}</b>
          </label>
          <input
            :id="`w-${c.key}`"
            v-model.number="sliders[KEYS[i]]"
            type="range"
            min="0"
            max="100"
            step="1"
            style="width: 100%; accent-color: var(--cinnabar)"
          />
        </div>
      </div>

      <div class="sk-card">
        <h2>口味条件</h2>

        <div class="field">
          <label for="maxspice">辣度上限：{{ ['不辣', '微辣', '中辣', '特辣'][taste.maxSpice] }}</label>
          <input
            id="maxspice"
            v-model.number="taste.maxSpice"
            type="range"
            min="0"
            max="3"
            step="1"
            style="width: 100%; accent-color: var(--cinnabar)"
          />
        </div>

        <label class="choice" style="margin-bottom: 12px">
          <input v-model="taste.avoidSpice" type="checkbox" />
          <span>
            <span class="choice-title">完全不吃辣</span>
            <span class="choice-note">比辣度上限更强的约束</span>
          </span>
        </label>

        <div class="field">
          <label for="avoid">忌口食材</label>
          <div class="sk-row">
            <input
              id="avoid"
              v-model="avoidText"
              type="text"
              placeholder="输入后回车"
              style="flex: 1"
              @keyup.enter="addAvoid"
            />
            <button class="btn btn-ghost btn-sm" type="button" @click="addAvoid">加</button>
          </div>
          <div class="filter-chips" style="margin-top: 8px">
            <button
              v-for="a in taste.avoid"
              :key="a"
              type="button"
              class="spec-opt is-on"
              @click="taste.avoid = taste.avoid.filter((x) => x !== a)"
            >
              {{ a }} ×
            </button>
            <button
              v-for="o in ingredientOptions.filter((x) => !taste.avoid.includes(x))"
              :key="o"
              type="button"
              class="spec-opt"
              @click="taste.avoid.push(o)"
            >
              {{ o }}
            </button>
          </div>
          <p style="margin: 8px 0 0; font-size: var(--fs-xs); color: var(--ink-3)">
            每命中一项忌口，口味匹配扣 30 分。含牛羊的菜会明显往下掉。
          </p>
        </div>

        <div class="field">
          <label for="topk">只看前几名</label>
          <select id="topk" v-model.number="topK" style="width: 100%; padding: 8px">
            <option :value="5">前 5 名</option>
            <option :value="8">前 8 名</option>
            <option :value="15">前 15 名</option>
            <option :value="99">全部</option>
          </select>
        </div>
      </div>

      <div v-if="result" class="sk-card">
        <h2>TOPSIS 中间量</h2>
        <p style="margin: 0 0 10px; font-size: var(--fs-xs); color: var(--ink-3)">
          正负理想解 V⁺ / V⁻ 是在<b>全部候选</b>上取的加权归一值，不是只在前几名里取 ——
          只在前几名里取会算出偏掉的理想解。论文表 3.4 就是这几列。
        </p>
        <div class="table-wrap">
          <table style="width: 100%; border-collapse: collapse; font-size: var(--fs-xs)">
            <thead>
              <tr style="color: var(--ink-3); text-align: left">
                <th style="padding: 6px 4px">准则</th>
                <th style="padding: 6px 4px; text-align: right">权重 w</th>
                <th style="padding: 6px 4px; text-align: right">V⁺</th>
                <th style="padding: 6px 4px; text-align: right">V⁻</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(c, i) in result.weights"
                :key="c.key"
                style="border-top: 1px solid var(--line-2)"
              >
                <td style="padding: 6px 4px">
                  {{ c.label }}
                  <span style="color: var(--ink-3)">{{ c.benefit ? '效益型' : '成本型' }}</span>
                </td>
                <td style="padding: 6px 4px; text-align: right" class="sk-num">{{ c.value.toFixed(4) }}</td>
                <td style="padding: 6px 4px; text-align: right" class="sk-num">
                  {{ result.ideal[i].value.toFixed(4) }}
                </td>
                <td style="padding: 6px 4px; text-align: right" class="sk-num">
                  {{ result.anti[i].value.toFixed(4) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="!user.loggedIn" class="sk-card">
        <h2>登录后更准</h2>
        <p style="margin: 0 0 10px; font-size: var(--fs-sm); color: var(--ink-3)">
          现在用的是新客画像。登录之后会结合你的口味偏好和历史订单来算。
        </p>
        <RouterLink class="btn btn-primary btn-block" :to="{ name: 'login' }">去登录</RouterLink>
      </div>
    </div>
  </div>
</template>
