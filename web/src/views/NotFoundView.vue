<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="sk-empty" style="padding: 96px 20px">
    <h1 style="font-size: var(--fs-3xl); margin: 0; color: var(--ink)">走错路了</h1>
    <p>这个地址没有对应的页面。</p>
    <div class="sk-row" style="justify-content: center">
      <button class="btn btn-primary" type="button" @click="router.push({ name: 'home' })">回首页</button>
      <button class="btn btn-ghost" type="button" @click="router.push({ name: 'menu' })">去点餐</button>
    </div>
  </div>
</template>
