import http from './client'

/**
 * 后端接口的调用清单。
 *
 * 集中在一个文件里而不是散在各页面，是为了让「前端到底依赖哪些接口」
 * 一眼可查 —— 写论文的接口设计章节时可以直接照着它列，
 * 也不会出现两个页面各自拼了一个路径、其中一个拼错的情况。
 */

// ---------- 菜单（匿名可访问） ----------
export const listCategories = () => http.get('/api/categories')
export const listDishes = (params) => http.get('/api/dishes', { params })
export const getDish = (code) => http.get(`/api/dishes/${code}`)
export const listTables = () => http.get('/api/tables')

// ---------- 推荐（匿名可访问） ----------
export const recommend = (params) => http.get('/api/recommend', { params })
export const recommendWith = (body) => http.post('/api/recommend', body)

// ---------- 账号 ----------
export const login = (body) => http.post('/api/auth/login', body)
export const register = (body) => http.post('/api/auth/register', body)
export const me = () => http.get('/api/auth/me')
export const updateProfile = (body) => http.put('/api/auth/profile', body)

// ---------- 口味画像 ----------
export const getTaste = () => http.get('/api/profile/taste')
export const saveTaste = (body) => http.put('/api/profile/taste', body)

// ---------- 订单 ----------
/** 结算页的金额试算。收费规则只在后端有一份，前端不重算 */
export const quoteOrder = (body) => http.post('/api/orders/quote', body)
export const createOrder = (body) => http.post('/api/orders', body)
export const listMyOrders = () => http.get('/api/orders')
export const getOrder = (orderNo) => http.get(`/api/orders/${orderNo}`)
export const cancelOrder = (orderNo) => http.post(`/api/orders/${orderNo}/cancel`)
export const payOrder = (orderNo, payMethod) =>
  http.post(`/api/orders/${orderNo}/pay`, null, { params: { payMethod } })

// ---------- 后厨（需 KITCHEN / ADMIN） ----------
export const kitchenOrders = () => http.get('/api/kitchen/orders')
export const kitchenStats = () => http.get('/api/kitchen/stats')
export const advanceOrder = (orderNo, to) =>
  http.post(`/api/kitchen/orders/${orderNo}/status`, null, { params: { to } })

// ---------- 后台（需 ADMIN） ----------
export const dashboard = () => http.get('/api/admin/dashboard')
export const adminDishes = () => http.get('/api/admin/dishes')
export const adminUpdateDish = (code, body) => http.put(`/api/admin/dishes/${code}`, body)
export const adminToggleDish = (code, enabled) =>
  http.put(`/api/admin/dishes/${code}/enabled`, { enabled })
