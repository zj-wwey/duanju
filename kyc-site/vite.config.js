import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端构建配置：Vue 插件 + dev 模式将 /api 代理到 Express 后端
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
})
