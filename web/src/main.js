import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// 顺序有讲究：先引 Element Plus 的样式，再引我们自己的。
// 反过来的话，app.css 里那套覆盖会被 Element Plus 的默认值盖回去，
// 主色会变回它那个蓝，而且只在部分组件上生效，很难查
import 'element-plus/dist/index.css'
import './assets/style.css'
import './assets/app.css'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/client'
import { useUserStore } from './stores/user'
import { useCartStore } from './stores/cart'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

const userStore = useUserStore(pinia)

/**
 * 扫码进店：`/?table=A12`
 *
 * 真实店里每张桌子上贴的二维码就是带这个参数的链接，扫进来桌号已经填好了，
 * 不需要顾客自己去列表里找。演示时在地址栏加 `?table=B07` 就能演一遍。
 *
 * 读完就把参数从地址栏抹掉：留着一个 `?table=A12` 会让用户以为
 * 换桌要改地址，而换桌其实在结算页选。抹掉也顺便避免了刷新时
 * 把已经改过的桌号又盖回去。
 */
const tableFromQr = new URLSearchParams(location.search).get('table')
if (tableFromQr) {
  useCartStore(pinia).setTable(tableFromQr)
  const rest = new URLSearchParams(location.search)
  rest.delete('table')
  const qs = rest.toString()
  history.replaceState(null, '', location.pathname + (qs ? `?${qs}` : '') + location.hash)
}

// 令牌失效时（后端返回 401）回到登录页，并把当前地址记下来。
// 放在这里而不是 client.js 里，是为了避免 client → router → stores → client 的循环引用
setUnauthorizedHandler(() => {
  userStore.logout()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

// 先恢复登录态再挂载。反过来的话首屏会先画出「登录」再跳成用户名，
// 页头闪一下，看起来像是没登录成功
userStore.restore().finally(() => {
  app.mount('#app')
})
