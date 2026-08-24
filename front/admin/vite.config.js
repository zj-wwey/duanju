import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const basePath = env.VITE_BASE_PATH || '/'

  return {
    base: basePath,
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/uploads': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/upload': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true
        }
      }
    }
  }
})
