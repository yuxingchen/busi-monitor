import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * API前缀配置
 * - 开发环境: http://localhost:8080/api (通过 .env.development 配置)
 * - 生产环境: /api (通过 .env.production 配置, 由nginx代理转发)
 * 
 * 使用方式: 在对应的 .env 文件中设置 VITE_API_PREFIX
 */
const apiPrefix = import.meta.env.VITE_API_PREFIX || '/api'

const request = axios.create({
    baseURL: apiPrefix,
    timeout: 30000
})

// 请求拦截器 - 添加Token
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            // 同时设置两个头，确保兼容性：
            // - Authorization: 标准 Bearer Token（本地开发使用）
            // - X-Auth-Token: 自定义头（Nginx auth_basic 环境使用，避免 Authorization 被拦截）
            config.headers['Authorization'] = `Bearer ${token}`
            config.headers['X-Auth-Token'] = token
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器 - 处理统一响应格式
request.interceptors.response.use(
    response => {
        const data = response.data
        
        // 新版统一响应格式: { success, code, message, data }
        if (typeof data === 'object' && 'success' in data) {
            if (data.success) {
                // 成功时返回data字段（如果有），否则返回整个响应
                return data.data !== undefined ? data.data : data
            } else {
                // 业务失败
                ElMessage.error(data.message || '操作失败')
                return Promise.reject(new Error(data.message || '操作失败'))
            }
        }
        
        // 兼容旧版响应格式（直接返回数据）
        return data
    },
    error => {
        if (error.response) {
            const status = error.response.status
            const data = error.response.data
            
            if (status === 401) {
                // Token无效或过期，清除登录状态并跳转到登录页
                localStorage.removeItem('token')
                localStorage.removeItem('username')
                localStorage.removeItem('role')
                
                // 如果不是登录页，跳转到登录页
                if (window.location.pathname !== '/login') {
                    ElMessage.warning('登录已过期，请重新登录')
                    window.location.href = '/login'
                }
            } else if (status === 400) {
                // 参数验证失败
                ElMessage.error(data.message || '参数错误')
            } else if (status === 403) {
                ElMessage.error('权限不足')
            } else if (status === 404) {
                ElMessage.error(data.message || '资源不存在')
            } else if (status >= 500) {
                ElMessage.error(data.message || '服务器错误，请稍后重试')
            } else {
                ElMessage.error(data.message || error.message || '请求失败')
            }
        } else {
            ElMessage.error('网络错误，请检查网络连接')
        }
        return Promise.reject(error)
    }
)

/**
 * 封装的API响应处理工具
 * 用于需要更细粒度控制的场景
 */
export const handleApiResponse = (response) => {
    if (response && typeof response === 'object' && 'success' in response) {
        if (response.success) {
            return { success: true, data: response.data, message: response.message }
        } else {
            return { success: false, data: null, message: response.message }
        }
    }
    return { success: true, data: response, message: null }
}

export default request

