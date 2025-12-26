import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
    baseURL: 'http://localhost:8080/api', // Backend URL
    timeout: 30000
})

// 请求拦截器 - 添加Token
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器 - 处理401未授权
request.interceptors.response.use(
    response => response.data,
    error => {
        if (error.response && error.response.status === 401) {
            // Token无效或过期，清除登录状态并跳转到登录页
            localStorage.removeItem('token')
            localStorage.removeItem('username')
            localStorage.removeItem('role')
            
            // 如果不是登录页，跳转到登录页
            if (window.location.pathname !== '/login') {
                ElMessage.warning('登录已过期，请重新登录')
                window.location.href = '/login'
            }
        } else {
            ElMessage.error(error.message || '请求失败')
        }
        return Promise.reject(error)
    }
)

export default request

