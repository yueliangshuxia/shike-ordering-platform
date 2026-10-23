<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import { useUserStore } from '@/stores/user'
import { useMenuStore } from '@/stores/menu'

/**
 * 个人中心。
 *
 * 口味设置不是普通的「设置项」，它是推荐算法的输入之一 ——
 * 在这里改一下，推荐结果立刻就变。界面上要把这句话说清楚，
 * 否则用户不会知道填它有什用。
 */
const user = useUserStore()
const menu = useMenuStore()

const loading = ref(true)
const saving = ref(false)
const nickname = ref('')

const taste = reactive({
  preferCategories: [],
  maxSpice: 3,
  avoidSpice: false,
  avoidIngredients: [],
})

const maxSpiceLabels = ['不辣', '微辣', '中辣', '特辣']
const ingredientOptions = ref([])
const avoidText = ref('')

/** 可选的偏好分类：虚拟的「招牌推荐」也在里面 */
const categoryOptions = ref([])

function addAvoid() {
  const v = avoidText.value.trim()
  if (v && !taste.avoidIngredients.includes(v)) taste.avoidIngredients.push(v)
  avoidText.value = ''
}

function toggleCategory(code) {
  const i = taste.preferCategories.indexOf(code)
  if (i >= 0) taste.preferCategories.splice(i, 1)
  else taste.preferCategories.push(code)
}

async function load() {
  loading.value = true
  try {
    await menu.load()
    nickname.value = user.profile?.nickname || ''
    const t = await api.getTaste()
    taste.preferCategories = [...t.preferCategories]
    taste.maxSpice = t.maxSpice
    taste.avoidSpice = t.avoidSpice
    taste.avoidIngredients = [...t.avoidIngredients]
    ingredientOptions.value = t.ingredientOptions || []
    categoryOptions.value = t.categoryOptions || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save() {
  saving.value = true
  try {
    if (nickname.value.trim() && nickname.value.trim() !== user.profile?.nickname) {
      await api.updateProfile({ nickname: nickname.value.trim() })
      await user.refresh()
    }
    await api.saveTaste({
      preferCategories: taste.preferCategories,
      maxSpice: taste.maxSpice,
      avoidSpice: taste.avoidSpice,
      avoidIngredients: taste.avoidIngredients,
    })
    ElMessage.success('已保存，下次推荐会按新的来算')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function signOut() {
  user.logout()
  location.href = '/'
}
</script>

<template>
  <div class="page-head">
    <div>
      <h1>个人中心</h1>
      <p>口味设置会直接参与推荐算法的计算</p>
    </div>
  </div>

  <div v-if="loading" class="sk-card">
    <div class="sk-skeleton" style="height: 20px; width: 40%"></div>
  </div>

  <div v-else class="sk-split">
    <div>
      <div class="sk-card">
        <h2>口味偏好</h2>
        <p style="margin: 0 0 14px; font-size: var(--fs-sm); color: var(--ink-3)">
          勾了哪些分类，这些分类的菜在「口味匹配」这一项上会加分。
          历史订单也会自动参与计算，两者取较高的那个。
        </p>

        <div class="filter-chips">
          <button
            v-for="c in categoryOptions"
            :key="c"
            type="button"
            class="spec-opt"
            :class="{ 'is-on': taste.preferCategories.includes(c) }"
            :aria-pressed="taste.preferCategories.includes(c)"
            @click="toggleCategory(c)"
          >
            {{ menu.categories.find((x) => x.code === c)?.name || c }}
          </button>
        </div>
      </div>

      <div class="sk-card">
        <h2>辣度</h2>
        <div class="field">
          <label for="spice">能接受的上限：{{ maxSpiceLabels[taste.maxSpice] }}</label>
          <input
            id="spice"
            v-model.number="taste.maxSpice"
            type="range"
            min="0"
            max="3"
            step="1"
            style="width: 100%; accent-color: var(--cinnabar)"
          />
        </div>
        <label class="choice">
          <input v-model="taste.avoidSpice" type="checkbox" />
          <span>
            <span class="choice-title">完全不吃辣</span>
            <span class="choice-note">比辣度上限更强的约束，辣的菜会被明显压下去</span>
          </span>
        </label>
      </div>

      <div class="sk-card">
        <h2>忌口</h2>
        <p style="margin: 0 0 10px; font-size: var(--fs-sm); color: var(--ink-3)">
          每命中一项扣 30 分。勾「牛羊肉」也能挡住「牛肉末」这类写法。
        </p>

        <div class="sk-row" style="margin-bottom: 10px">
          <input
            v-model="avoidText"
            type="text"
            placeholder="也可以自己填，回车加入"
            style="flex: 1"
            aria-label="添加忌口食材"
            @keyup.enter="addAvoid"
          />
          <button class="btn btn-ghost btn-sm" type="button" @click="addAvoid">加入</button>
        </div>

        <div class="filter-chips">
          <button
            v-for="a in taste.avoidIngredients"
            :key="a"
            type="button"
            class="spec-opt is-on"
            @click="taste.avoidIngredients = taste.avoidIngredients.filter((x) => x !== a)"
          >
            {{ a }} ×
          </button>
          <button
            v-for="o in ingredientOptions.filter((x) => !taste.avoidIngredients.includes(x))"
            :key="o"
            type="button"
            class="spec-opt"
            @click="taste.avoidIngredients.push(o)"
          >
            {{ o }}
          </button>
        </div>
      </div>

      <div class="sk-card">
        <div class="sk-row-between">
          <h2 style="margin: 0">昵称</h2>
        </div>
        <label class="sr-only" for="nickname">昵称</label>
        <input
          id="nickname"
          v-model="nickname"
          type="text"
          maxlength="32"
          style="width: 100%; padding: 9px; margin-top: 10px"
        />
      </div>

      <button class="btn btn-primary btn-lg" type="button" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存设置' }}
      </button>
    </div>

    <div>
      <div class="sk-card">
        <div class="sk-row" style="gap: 12px">
          <iconify-icon icon="mdi:account-circle" width="46" height="46" style="color: var(--pine)"></iconify-icon>
          <div>
            <div style="font-family: var(--font-song); font-size: var(--fs-md); font-weight: 700">
              {{ user.profile?.nickname }}
            </div>
            <div style="font-size: var(--fs-xs); color: var(--ink-3)">
              {{ user.profile?.phone }} · {{ user.profile?.level }}
            </div>
          </div>
        </div>
      </div>

      <div class="sk-card">
        <h2>快捷入口</h2>
        <ul style="list-style: none; margin: 0; padding: 0">
          <li style="padding: 6px 0"><RouterLink :to="{ name: 'orders' }">我的订单</RouterLink></li>
          <li style="padding: 6px 0"><RouterLink :to="{ name: 'recommend' }">推荐实验室</RouterLink></li>
          <li style="padding: 6px 0"><RouterLink :to="{ name: 'cart' }">我的点单</RouterLink></li>
          <li v-if="user.isKitchen" style="padding: 6px 0">
            <RouterLink :to="{ name: 'kitchen' }">后厨出单屏</RouterLink>
          </li>
          <li v-if="user.isAdmin" style="padding: 6px 0">
            <RouterLink :to="{ name: 'admin' }">经营看板</RouterLink>
          </li>
        </ul>
        <button class="btn btn-ghost btn-block" type="button" style="margin-top: 12px" @click="signOut">
          退出登录
        </button>
      </div>
    </div>
  </div>
</template>
