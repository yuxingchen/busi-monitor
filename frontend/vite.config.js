import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')
  
  return {
    // 根据环境变量设置基础路径，开发环境默认为 '/'
    base: env.VITE_BASE_URL || '/',
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
  }
})
