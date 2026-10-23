<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

/**
 * 页头。
 *
 * 结构、类名、图标全部沿用 demo 的 `layout.js` —— 那份设计用户已经确认过，
 * 这里只把 `<a href>` 换成 Vue Router，把登录态从全局变量换成 store。
 * 重画一套样式的话，演示版和正式版会长得不一样，
 * 而"正式版不如演示版好看"是很难解释的事。
 */
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const cart = useCartStore()

const nav = computed(() => {
  const base = [
    { name: 'home', label: '首页' },
    { name: 'menu', label: '点餐' },
    { name: 'recommend', label: '推荐实验室' },
    { name: 'orders', label: '我的订单', auth: true },
  ]
  if (user.isAdmin) base.push({ name: 'admin', label: '商家后台' })
  return base.filter((n) => !n.auth || user.loggedIn)
})

// 页头搜索。demo 里是 <form action="menu.html">，这里改成路由跳转 ——
// 用 form 的话浏览器会整页刷新，Vue 应用被重新挂载，白闪一下
const keyword = ref('')

function search() {
  const q = keyword.value.trim()
  router.push({ name: 'menu', query: q ? { q } : {} })
}

const accountTarget = computed(() => (user.loggedIn ? 'profile' : 'login'))
</script>

<template>
  <header class="topbar">
    <div class="wrap-wide">
      <RouterLink class="brand" :to="{ name: 'home' }">
        食<span class="brand-mark">刻</span>
        <small>SHI KE KITCHEN</small>
      </RouterLink>

      <nav class="mainnav" aria-label="主导航">
        <RouterLink
          v-for="item in nav"
          :key="item.name"
          :to="{ name: item.name }"
          :aria-current="route.name === item.name ? 'page' : undefined"
        >
          {{ item.label }}
        </RouterLink>
        <RouterLink
          v-if="user.isKitchen"
          :to="{ name: 'kitchen' }"
          :aria-current="route.name === 'kitchen' ? 'page' : undefined"
        >
          后厨屏
        </RouterLink>
      </nav>

      <form class="topbar-search" role="search" @submit.prevent="search">
        <iconify-icon icon="mdi:magnify" width="16" height="16" aria-hidden="true"></iconify-icon>
        <label class="sr-only" for="q">搜索菜品</label>
        <input
          id="q"
          v-model="keyword"
          type="search"
          placeholder="搜菜名，比如「小笼包」"
          autocomplete="off"
        />
      </form>

      <div class="topbar-actions">
        <RouterLink
          class="icon-btn"
          :to="{ name: accountTarget }"
          :title="user.loggedIn ? user.displayName : '登录 / 注册'"
        >
          <iconify-icon
            :icon="user.loggedIn ? 'mdi:account-circle-outline' : 'mdi:account-outline'"
            width="18"
            height="18"
            aria-hidden="true"
          ></iconify-icon>
          <span class="nav-text">{{ user.loggedIn ? user.displayName : '登录' }}</span>
        </RouterLink>

        <RouterLink class="icon-btn cart-badge" :to="{ name: 'cart' }">
          <iconify-icon icon="mdi:receipt-text-outline" width="18" height="18" aria-hidden="true"></iconify-icon>
          <span class="nav-text">点单</span>
          <span class="badge" :class="{ 'is-on': cart.count > 0 }" aria-live="polite">
            {{ cart.count }}
          </span>
        </RouterLink>
      </div>
    </div>
  </header>
</template>
