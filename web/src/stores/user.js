import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '@/api'
import { getToken, setToken } from '@/api/client'

export const useUserStore = defineStore('user', () => {
  const profile = ref(null)
  const ready = ref(false)

  const loggedIn = computed(() => !!profile.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const isKitchen = computed(() => profile.value?.role === 'KITCHEN' || isAdmin.value)
  const displayName = computed(() => profile.value?.nickname || '登录')

  /**
   * 恢复登录态。
   *
   * 注意这里**不是**「localStorage 里有令牌就算登录」——
   * 令牌可能已经过期，那样界面会显示成已登录，但每个请求都 401，
   * 用户点什么都失败却不知道为什么。所以拿令牌去问一次后端。
   */
  async function restore() {
    if (!getToken()) {
      ready.value = true
      return
    }
    try {
      profile.value = await api.me()
    } catch {
      // 令牌失效。client.js 的拦截器已经把它清掉了
      profile.value = null
    } finally {
      ready.value = true
    }
  }

  async function login(username, password) {
    const res = await api.login({ username, password })
    setToken(res.token)
    profile.value = res.user
    return res.user
  }

  async function register(payload) {
    const res = await api.register(payload)
    setToken(res.token)
    profile.value = res.user
    return res.user
  }

  function logout() {
    setToken('')
    profile.value = null
  }

  async function refresh() {
    if (!getToken()) return
    try {
      profile.value = await api.me()
    } catch {
      profile.value = null
    }
  }

  return { profile, ready, loggedIn, isAdmin, isKitchen, displayName, restore, login, register, logout, refresh }
})
