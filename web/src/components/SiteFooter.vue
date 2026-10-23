<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/client'

/**
 * 页脚。结构和类名沿用 demo 的 layout.js。
 *
 * 门店信息从 `/api/shop` 取，不是前端写死的 ——
 * 页脚和电子小票读同一份，改地址时才不会只改到一处。
 * 取不到时各字段留空，页脚仍然完整（而不是整块消失）。
 */
const shop = ref({ phone: '', address: '', hours: '' })

onMounted(async () => {
  try {
    shop.value = await http.get('/api/shop')
  } catch {
    /* 门店信息拿不到不影响逛菜单，静默降级 */
  }
})

const year = new Date().getFullYear()
const tel = () => (shop.value.phone || '').replace(/\s/g, '')
</script>

<template>
  <footer class="site-foot">
    <div class="wrap-wide">
      <div class="foot-grid">
        <div>
          <div class="foot-brand">食刻 · SHI KE</div>
          <p style="margin: 0 0 10px; max-width: 34ch">
            现做现出的港式茶餐厅，早茶点心、粥粉面、碟头小炒、糖水与奶茶，一日三餐都做。
          </p>
          <p style="margin: 0">{{ shop.address }}</p>
          <p style="margin: 6px 0 0">{{ shop.hours }}</p>
        </div>

        <div>
          <h2>点餐</h2>
          <ul>
            <li><RouterLink :to="{ name: 'menu' }">全部菜单</RouterLink></li>
            <li><RouterLink :to="{ name: 'menu', query: { category: 'signature' } }">招牌推荐</RouterLink></li>
            <li><RouterLink :to="{ name: 'recommend' }">为我推荐</RouterLink></li>
            <li><RouterLink :to="{ name: 'cart' }">我的点单</RouterLink></li>
          </ul>
        </div>

        <div>
          <h2>我的</h2>
          <ul>
            <li><RouterLink :to="{ name: 'profile' }">个人中心</RouterLink></li>
            <li><RouterLink :to="{ name: 'orders' }">订单记录</RouterLink></li>
            <li><RouterLink :to="{ name: 'login' }">登录 / 注册</RouterLink></li>
          </ul>
        </div>

        <div>
          <h2>门店</h2>
          <ul>
            <li><RouterLink :to="{ name: 'kitchen' }">后厨出单屏</RouterLink></li>
            <li><RouterLink :to="{ name: 'admin' }">商家后台</RouterLink></li>
            <li><a v-if="shop.phone" :href="`tel:${tel()}`">{{ shop.phone }}</a></li>
          </ul>
        </div>
      </div>

      <div class="foot-bottom">
        <span>© {{ year }} 食刻点餐 · 本科毕业设计作品</span>
        <span>图片来自 Pexels，插画来自 unDraw，图标来自 Iconify</span>
      </div>
    </div>
  </footer>
</template>
