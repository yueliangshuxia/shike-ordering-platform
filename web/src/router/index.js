import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/client'

/**
 * 路由表。
 *
 * `meta.auth` 标出需要登录的页面，`meta.role` 标出需要的角色。
 * 守卫里统一判断，页面自己不再写一遍 —— 两处都写迟早会漂。
 *
 * 注意这只是**界面层的引导**（未登录时跳登录页，体验好一点），
 * 真正的权限控制在后端。前端守卫拦不住直接调接口的人，
 * 所以绝不能把"前端已经拦了"当成安全依据。
 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    // bleed：hero 要通栏铺背景，所以这个页面自己管容器宽度
    meta: { title: '首页', bleed: true },
  },
  { path: '/menu', name: 'menu', component: () => import('@/views/MenuView.vue'), meta: { title: '点单' } },
  { path: '/dish/:code', name: 'dish', component: () => import('@/views/DishView.vue'), meta: { title: '菜品详情' } },
  { path: '/recommend', name: 'recommend', component: () => import('@/views/RecommendView.vue'), meta: { title: '推荐实验室' } },
  { path: '/cart', name: 'cart', component: () => import('@/views/CartView.vue'), meta: { title: '购物车' } },
  { path: '/checkout', name: 'checkout', component: () => import('@/views/CheckoutView.vue'), meta: { title: '结算' } },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('@/views/OrdersView.vue'),
    meta: { title: '我的订单', auth: true },
  },
  {
    path: '/order/:orderNo',
    name: 'order',
    component: () => import('@/views/OrderDetailView.vue'),
    meta: { title: '订单详情', auth: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '个人中心', auth: true },
  },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
  {
    path: '/kitchen',
    name: 'kitchen',
    component: () => import('@/views/KitchenView.vue'),
    // bare：不要站点页头页脚。后厨屏是挂在墙上的独立显示器，
    // 上面出现导航链接纯属误导 —— 没人会在灶台边上点「推荐实验室」
    meta: { title: '后厨屏', auth: true, role: 'KITCHEN', bare: true },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { title: '经营看板', auth: true, role: 'ADMIN' },
  },
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, saved) => saved || { top: 0 },
})

router.beforeEach((to) => {
  if (!to.meta.auth) return true

  if (!getToken()) {
    // 记住原本要去哪儿，登录完直接送过去 ——
    // 不然用户点「我的订单」被弹到登录页，登完却落在首页，还得自己再点一次
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 食刻点餐` : '食刻点餐'
})

export default router
