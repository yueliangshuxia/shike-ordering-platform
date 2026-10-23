import axios from 'axios'

/**
 * 后端返回的是统一响应体 { code, message, data }。
 *
 * 这里把它拆开：code 为 0 就把 data 直接交给调用方，否则抛异常。
 * 让每个页面都写一遍 `if (res.data.code === 0)` 是没意义的重复 ——
 * 更糟的是总会有人忘记写，于是失败被当成成功，页面上悄悄渲染出空数据。
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 15000,
})

const TOKEN_KEY = 'sk_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 时通知外部（由 main.js 注册），避免这里直接 import router 造成循环依赖 */
let onUnauthorized = null
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 后端所有接口都走统一响应体，所以这里的结构是确定的
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      throw new ApiError(body.message || '请求失败', body.code)
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.message || error.message || '网络异常'

    // 令牌过期或没带令牌。清掉本地那份，让界面回到未登录状态；
    // 不退化成"什么也不做"——否则用户会一直看到一个操作不了但看起来已登录的页面
    if (status === 401) {
      setToken('')
      if (onUnauthorized) onUnauthorized()
    }
    return Promise.reject(new ApiError(message, body?.code ?? status ?? -1))
  },
)

export class ApiError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export default http
