import { createRouter, createWebHashHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import DataSource from '../views/DataSource.vue'
import TaskConfig from '../views/TaskConfig.vue'
import AlarmConfig from '../views/AlarmConfig.vue'
import ServerAsset from '../views/ServerAsset.vue'
import MonitorTemplate from '../views/MonitorTemplate.vue'
import DashboardEditor from '../views/DashboardEditor.vue'
import WorkflowDesigner from '../views/WorkflowDesigner.vue'
import Login from '../views/Login.vue'
import UserManagement from '../views/UserManagement.vue'

const routes = [
    { path: '/login', component: Login, name: 'Login', meta: { requiresAuth: false } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: Dashboard, name: 'Dashboard', meta: { requiresAuth: true } },
    { path: '/dashboard-editor', component: DashboardEditor, name: 'DashboardEditor', meta: { requiresAuth: true } },
    { path: '/workflow', component: WorkflowDesigner, name: 'WorkflowDesigner', meta: { requiresAuth: true } },
    { path: '/task', component: TaskConfig, name: 'TaskConfig', meta: { requiresAuth: true } },
    { path: '/server', component: ServerAsset, name: 'ServerAsset', meta: { requiresAuth: true } },
    { path: '/template', component: MonitorTemplate, name: 'MonitorTemplate', meta: { requiresAuth: true } },
    { path: '/alarm', component: AlarmConfig, name: 'AlarmConfig', meta: { requiresAuth: true } },
    { path: '/datasource', component: DataSource, name: 'DataSource', meta: { requiresAuth: true } },
    { path: '/user', component: UserManagement, name: 'UserManagement', meta: { requiresAuth: true } }
]

const router = createRouter({
    history: createWebHashHistory(),
    routes
})

// 路由守卫 - 检查登录状态
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    
    // 如果访问登录页且已登录，跳转到首页
    if (to.path === '/login' && token) {
        next('/dashboard')
        return
    }
    
    // 如果需要认证且未登录，跳转到登录页
    if (to.meta.requiresAuth !== false && !token) {
        next('/login')
        return
    }
    
    next()
})

export default router

