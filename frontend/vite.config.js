import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    global: 'globalThis'  // Fix for sockjs-client
  },
  server: {
    proxy: {
      '/api/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true  // 启用 WebSocket 代理
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
