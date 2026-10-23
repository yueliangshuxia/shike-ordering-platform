<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 登录 / 注册。
 *
 * 注册完直接进登录态（后端会把令牌一起返回），不让用户再输一遍 ——
 * 「注册成功，请登录」是最没必要的多一步。
 */
const route = useRoute()
const router = useRouter()
const user = useUserStore()

const mode = ref('login')
const submitting = ref(false)
const form = reactive({ username: '', password: '', nickname: '', phone: '' })

/** 演示账号。写在页面上是因为这是课程作品，评分老师需要能立刻进去看 */
const demoAccounts = [
  { label: '顾客', username: '13800000621', password: '123456' },
  { label: '管理员', username: '13900000001', password: 'admin123' },
  { label: '后厨', username: '13900000002', password: 'kitchen123' },
]

function fill(acc) {
  form.username = acc.username
  form.password = acc.password
  mode.value = 'login'
}

async function submit() {
  if (submitting.value) return
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写账号和密码')
    return
  }
  if (mode.value === 'register' && !form.nickname.trim()) {
    ElMessage.warning('请填写昵称')
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'login') {
      await user.login(form.username.trim(), form.password)
      ElMessage.success('欢迎回来')
    } else {
      await user.register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim(),
        phone: form.phone.trim() || form.username.trim(),
      })
      ElMessage.success('注册成功，已自动登录')
    }
    // 登录后回原来要去的地方，没有就回首页
    router.replace(String(route.query.redirect || '/'))
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="sk-center" style="margin-top: 40px">
    <div class="sk-card">
      <div class="filter-chips" style="margin-bottom: 20px">
        <button
          type="button"
          class="spec-opt"
          :class="{ 'is-on': mode === 'login' }"
          :aria-pressed="mode === 'login'"
          @click="mode = 'login'"
        >
          登录
        </button>
        <button
          type="button"
          class="spec-opt"
          :class="{ 'is-on': mode === 'register' }"
          :aria-pressed="mode === 'register'"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="username">手机号</label>
          <input
            id="username"
            v-model="form.username"
            type="tel"
            inputmode="numeric"
            autocomplete="username"
            placeholder="11 位手机号"
          />
        </div>

        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            :placeholder="mode === 'login' ? '请输入密码' : '6–32 位'"
          />
        </div>

        <template v-if="mode === 'register'">
          <div class="field">
            <label for="nickname">昵称</label>
            <input id="nickname" v-model="form.nickname" type="text" maxlength="32" placeholder="怎么称呼你" />
          </div>
        </template>

        <button class="btn btn-primary btn-block btn-lg" type="submit" :disabled="submitting" style="margin-top: 8px">
          {{ submitting ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p style="margin: 16px 0 0; font-size: var(--fs-sm); color: var(--ink-3)">
        不登录也能逛菜单和用推荐实验室，只是推荐不会结合你的口味。
      </p>
    </div>

    <div class="sk-card">
      <h2>演示账号</h2>
      <p style="margin: 0 0 12px; font-size: var(--fs-sm); color: var(--ink-3)">
        点一下自动填入。
      </p>
      <div class="sk-row" style="flex-wrap: wrap">
        <button
          v-for="a in demoAccounts"
          :key="a.username"
          type="button"
          class="btn btn-ghost btn-sm"
          @click="fill(a)"
        >
          {{ a.label }}
        </button>
      </div>
    </div>
  </div>
</template>
