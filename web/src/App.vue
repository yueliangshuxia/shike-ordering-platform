<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteTopbar from '@/components/SiteTopbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const route = useRoute()

// 后厨屏不要站点页头页脚（meta.bare），见 router 里的说明
const bare = computed(() => route.meta.bare === true)

// 有些页面自己控制容器宽度（首页的通栏 hero、后厨屏的全屏看板），
// 就不要再套一层 .sk-main，否则 hero 的背景只能铺到 1440px 里
const bleed = computed(() => route.meta.bleed === true)
</script>

<template>
  <template v-if="bare">
    <RouterView />
  </template>

  <template v-else>
    <a class="skip sr-only" href="#main">跳到主要内容</a>
    <SiteTopbar />
    <main id="main" :class="{ 'sk-main': !bleed }">
      <RouterView />
    </main>
    <SiteFooter />
  </template>
</template>
