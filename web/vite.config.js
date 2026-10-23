import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发时把 /api 代理到后端。
    // 走代理而不是让前端直连 8080，好处是同源 —— 浏览器不发 CORS 预检，
    // 后端那边的 CORS 配置也就不用为了开发环境反复调。
    // 部署时前端和后端同域也一样能用，不用改代码。
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      // target 必须是 http:// 而不是 ws:// —— http-proxy 靠 `ws: true`
      // 自己去完成协议升级，把它写成 ws:// 反而会让握手失败，
      // 而报错只有浏览器控制台里一句 "WebSocket connection failed"，
      // 看不出是代理配置的问题。
      '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true },
    },
  },
})
