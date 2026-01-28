/**
 * API 配置工具
 * 统一管理 API 和 WebSocket 的 URL 前缀
 */

// API 前缀
export const API_PREFIX = import.meta.env.VITE_API_PREFIX || '/api'

/**
 * WebSocket URL 配置
 * 
 * 生产环境 nginx 配置示例：
 * location /busi_monitor/ {
 *     proxy_http_version 1.1;
 *     proxy_set_header Upgrade $http_upgrade;
 *     proxy_set_header Connection "upgrade";
 *     proxy_pass http://192.168.16.30:8000/;
 *     rewrite ^/busi_monitor/(.*)$ /$1 break;
 * }
 * 
 * 此时前端访问 /busi_monitor/api/ws 会被代理到后端 /api/ws
 */
export const WS_URL = (() => {
  // 优先使用环境变量配置
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL
  }
  
  // 根据 API_PREFIX 推断 WebSocket URL
  // /api -> /api/ws
  // /busi_monitor/api -> /busi_monitor/api/ws
  // http://xxx:8080/api -> http://xxx:8080/api/ws
  if (API_PREFIX.startsWith('http')) {
    // 绝对路径：http://localhost:8080/api -> http://localhost:8080/api/ws
    return API_PREFIX + '/ws'
  } else {
    // 相对路径：/api -> /api/ws
    return API_PREFIX + '/ws'
  }
})()

/**
 * 获取完整的 API URL
 * @param {string} path - API 路径（如 '/user/list'）
 * @returns {string} 完整的 API URL
 */
export function getApiUrl(path) {
  return API_PREFIX + path
}

/**
 * 获取 WebSocket URL
 * @returns {string} WebSocket URL
 */
export function getWsUrl() {
  return WS_URL
}

