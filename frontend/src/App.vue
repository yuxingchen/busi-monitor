<template>
  <!-- 登录页全屏渲染 -->
  <router-view v-if="$route.path === '/login'" />

  <!-- 其他页面使用主布局 -->
  <div v-else class="app-container">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo-wrapper" v-if="!sidebarCollapsed">
          <el-icon class="logo-icon">
            <Monitor />
          </el-icon>
          <div class="logo-text">
            <span class="logo-title">业务监控</span>
            <span class="logo-subtitle">MONITOR</span>
          </div>
        </div>
        <el-icon v-else class="logo-icon-mini">
          <Monitor />
        </el-icon>
      </div>

      <nav class="sidebar-nav">
        <router-link v-for="item in menuItems" :key="item.path" :to="item.path" class="nav-item"
          :class="{ active: $route.path === item.path }">
          <el-icon class="nav-icon">
            <component :is="item.icon" />
          </el-icon>
          <span class="nav-label" v-if="!sidebarCollapsed">{{ item.label }}</span>
          <div class="nav-indicator"></div>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon>
            <ArrowLeft v-if="!sidebarCollapsed" />
            <ArrowRight v-else />
          </el-icon>
        </button>
      </div>
    </aside>

    <!-- Main Area -->
    <div class="main-wrapper">
      <!-- Top Header -->
      <header class="top-header">
        <div class="header-left">
          <h2 class="page-title">{{ currentPageTitle }}</h2>
        </div>
        <div class="header-right">
          <div class="header-item" @click="toggleTheme" :title="isDark ? '切换浅色模式' : '切换暗色模式'">
            <el-icon>
              <Moon v-if="isDark" />
              <Sunny v-else />
            </el-icon>
          </div>
          <div class="header-item" @click="toggleFullscreen" title="全屏模式">
            <el-icon>
              <FullScreen v-if="!isFullscreen" />
              <Close v-else />
            </el-icon>
          </div>
          <el-popover placement="bottom-end" :width="400" trigger="click">
            <template #reference>
              <div class="header-item alarm-bell">
                <el-badge :value="activeAlarms.length" :hidden="activeAlarms.length === 0" :max="99"
                  class="alarm-badge">
                  <el-icon>
                    <Bell />
                  </el-icon>
                </el-badge>
              </div>
            </template>
            <div class="alarm-popover">
              <div class="alarm-popover-header">
                <span class="title">系统告警</span>
                <el-tag v-if="wsConnected" type="success" size="small" effect="plain">已连接</el-tag>
                <el-tag v-else type="danger" size="small" effect="plain">未连接</el-tag>
              </div>
              <div class="alarm-list" v-if="activeAlarms.length > 0">
                <div v-for="alarm in activeAlarms.slice(0, 10)" :key="alarm.id" class="alarm-item">
                  <div class="alarm-item-header">
                    <el-tag :type="getLevelType(alarm.level)" size="small">{{ alarm.level }}</el-tag>
                    <span class="alarm-time">{{ formatTime(alarm.firstTriggerTime || alarm.time) }}</span>
                  </div>
                  <div class="alarm-message">
                    <MdPreview :editorId="'popover-alarm-' + alarm.id"
                      :modelValue="alarm.alarmMessage || alarm.message" />
                  </div>
                  <div class="alarm-actions">
                    <span class="action-btn confirm" @click="acknowledgeAlarm(alarm)">确认</span>
                    <span class="action-btn suppress" @click="suppressAlarm(alarm)">抑制</span>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无告警" :image-size="60" />
              <div class="alarm-popover-footer" v-if="activeAlarms.length > 0">
                <router-link to="/alarm?tab=history">
                  查看全部告警 ({{ activeAlarms.length }})
                </router-link>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="hover" @command="handleUserCommand">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar">{{ currentUsername.charAt(0).toUpperCase() }}</el-avatar>
              <span class="user-name">{{ currentUsername }}</span>
              <el-icon class="dropdown-arrow">
                <ArrowDown />
              </el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-item command="logout">
                <el-icon>
                  <SwitchButton />
                </el-icon> 退出登录
              </el-dropdown-item>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- Page Content -->
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 右侧固定告警通知面板 -->
    <AlarmNotificationPanel ref="alarmNotificationRef" :maxCount="8" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor, DataLine, Setting, Connection, ArrowLeft, ArrowRight, Bell, FullScreen, Close, Moon, Sunny, Document, Grid, Share, ArrowDown, User, Lock, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import request from './api/request'
import AlarmNotificationPanel from './components/AlarmNotificationPanel.vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { WS_URL } from './utils/config'

const route = useRoute()
const router = useRouter()
const sidebarCollapsed = ref(false)
const isFullscreen = ref(false)
const isDark = ref(true)
const currentUsername = ref(localStorage.getItem('username') || 'Admin')
const currentRole = ref(localStorage.getItem('role') || 'USER')

// Alarm related
const activeAlarms = ref([])
const wsConnected = ref(false)
const alarmNotificationRef = ref(null)
let stompClient = null

const menuItems = [
  { path: '/dashboard', label: '监控大屏', icon: DataLine },
  { path: '/dashboard-editor', label: '大屏编辑', icon: Grid },
  { path: '/workflow', label: '工作流', icon: Share },
  { path: '/task', label: '任务配置', icon: Setting },
  { path: '/server', label: '服务器管理', icon: Monitor },
  { path: '/template', label: '监控模板', icon: Document },
  { path: '/alarm', label: '报警配置', icon: Bell },
  { path: '/datasource', label: '数据源管理', icon: Connection },
  { path: '/user', label: '用户管理', icon: UserFilled }
]

const currentPageTitle = computed(() => {
  const item = menuItems.find(m => m.path === route.path)
  return item ? item.label : '业务监控系统'
})

// Theme toggle
const toggleTheme = () => {
  isDark.value = !isDark.value
  applyTheme()
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
}

const applyTheme = () => {
  if (isDark.value) {
    document.documentElement.classList.add('dark')
    document.documentElement.classList.remove('light')
  } else {
    document.documentElement.classList.add('light')
    document.documentElement.classList.remove('dark')
  }
}

// Initialize theme from localStorage
onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme) {
    isDark.value = savedTheme === 'dark'
  }
  applyTheme()
})

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().then(() => {
      isFullscreen.value = true
    }).catch(() => { })
  } else {
    document.exitFullscreen().then(() => {
      isFullscreen.value = false
    }).catch(() => { })
  }
}

// 用户菜单命令处理
const handleUserCommand = (command) => {
  switch (command) {
    case 'logout':
      ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        localStorage.removeItem('role')
        router.push('/login')
        ElMessage.success('已退出登录')
      }).catch(() => { })
      break
  }
}

// Listen for fullscreen change (ESC key exits fullscreen)
document.addEventListener('fullscreenchange', () => {
  isFullscreen.value = !!document.fullscreenElement
})

// ========== Alarm WebSocket and Functions ==========
const loadActiveAlarms = async () => {
  try {
    activeAlarms.value = await request.get('/alarm/active')
  } catch (e) {
    console.error('Failed to load alarms:', e)
  }
}

const connectWebSocket = () => {
  // 防止重复连接
  if (stompClient && stompClient.connected) {
    console.log('[WebSocket] Already connected')
    return
  }
  if (stompClient) {
    try {
      stompClient.deactivate()
    } catch (e) {
      console.warn('[WebSocket] Deactivate error:', e)
    }
  }

  console.log('[WebSocket] Connecting to:', WS_URL)

  const socket = new SockJS(WS_URL)
  stompClient = new Client({
    webSocketFactory: () => socket,
    reconnectDelay: 5000,        // 5秒后重连
    heartbeatIncoming: 10000,    // 服务端心跳间隔
    heartbeatOutgoing: 10000,    // 客户端心跳间隔
    debug: (str) => {
      // 仅在开发环境输出调试信息
      if (import.meta.env.DEV) {
        console.debug('[STOMP]', str)
      }
    },
    onConnect: () => {
      console.log('[WebSocket] Connected successfully')
      wsConnected.value = true

      stompClient.subscribe('/topic/alarm', (message) => {
        try {
          const alarm = JSON.parse(message.body)
          handleAlarmEvent(alarm)
        } catch (e) {
          console.error('[WebSocket] Parse message error:', e)
        }
      })
    },
    onDisconnect: () => {
      console.log('[WebSocket] Disconnected')
      wsConnected.value = false
    },
    onStompError: (frame) => {
      console.error('[WebSocket] STOMP error:', frame.headers?.message || frame)
      wsConnected.value = false
    },
    onWebSocketError: (event) => {
      console.error('[WebSocket] WebSocket error:', event)
      wsConnected.value = false
    },
    onWebSocketClose: (event) => {
      console.log('[WebSocket] Connection closed:', event.code, event.reason)
      wsConnected.value = false
    }
  })

  stompClient.activate()
}

// 告警通知去重：记录最近显示的告警ID和时间
const recentNotifications = new Map()

const handleAlarmEvent = (alarm) => {
  console.log('Received alarm event:', alarm)
  // 兼容 type 和 status 字段
  const alarmType = alarm.type || alarm.status
  const alarmMessage = alarm.message || alarm.alarmMessage

  console.log('Alarm type:', alarmType, 'Message:', alarmMessage)

  // 去重：同一告警1秒内不重复显示（防止网络重发）
  const alarmKey = `${alarm.id}-${alarmType}`
  const now = Date.now()
  if (recentNotifications.has(alarmKey)) {
    const lastTime = recentNotifications.get(alarmKey)
    if (now - lastTime < 1000) {
      console.log('Duplicate notification ignored:', alarmKey)
      return
    }
  }
  recentNotifications.set(alarmKey, now)

  if (alarmType === 'FIRING') {
    // 添加或更新告警
    const idx = activeAlarms.value.findIndex(a => a.id === alarm.id)
    const isUpdate = idx >= 0

    if (isUpdate) {
      activeAlarms.value[idx] = alarm
    } else {
      activeAlarms.value.unshift(alarm)
    }

    // 无论新增还是更新都显示通知
    console.log('Showing notification, ref:', alarmNotificationRef.value, 'isUpdate:', isUpdate)
    if (alarmNotificationRef.value) {
      alarmNotificationRef.value.addNotification({
        title: isUpdate ? '告警更新' : '新告警',
        message: alarmMessage,
        level: alarm.level || 'WARNING',
        duration: 10000
      })
    } else {
      console.warn('alarmNotificationRef is null!')
    }
  } else if (alarmType === 'RESOLVED') {
    // 移除已恢复的告警（后端发送的是 alarmId 字段）
    const resolvedId = alarm.alarmId || alarm.id
    activeAlarms.value = activeAlarms.value.filter(a => a.id !== resolvedId)
    // 显示恢复通知
    if (alarmNotificationRef.value) {
      alarmNotificationRef.value.addNotification({
        title: '告警恢复',
        message: alarmMessage || '告警已自动恢复',
        level: 'INFO',
        duration: 5000
      })
    }
  }
}

const acknowledgeAlarm = async (alarm) => {
  try {
    await request.post(`/alarm/active/${alarm.id}/acknowledge`)
    ElMessage.success('告警已确认')
    loadActiveAlarms()
  } catch (e) {
    console.error('Acknowledge failed:', e)
    ElMessage.error('告警确认失败')
  }
}

const suppressAlarm = async (alarm) => {
  try {
    await request.post(`/alarm/active/${alarm.id}/suppress?minutes=30`)
    ElMessage.success('告警已抑制')
    loadActiveAlarms()
  } catch (e) {
    console.error('Suppress failed:', e)
    ElMessage.error('告警抑制失败')
  }
}

const getLevelType = (level) => {
  const map = { 'CRITICAL': 'danger', 'WARNING': 'warning', 'INFO': 'info' }
  return map[level] || 'info'
}

const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}

// Initialize alarms - 只在用户已登录时执行
onMounted(() => {
  const token = localStorage.getItem('token')
  if (token) {
    loadActiveAlarms()
    connectWebSocket()
  }
})

onUnmounted(() => {
  if (stompClient) {
    stompClient.deactivate()
  }
})
</script>

<style>
/* Global Reset & Base Styles */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

body {
  background: var(--theme-bg);
  color: var(--theme-text);
  overflow: hidden;
}

/* 下拉菜单样式 */
.el-dropdown__popper {
  border-radius: 8px !important;
  overflow: hidden;
}

.el-dropdown-menu {
  border-radius: 8px !important;
  overflow: hidden;
}

.el-dropdown-menu__item {
  border-radius: 6px !important;
  margin: 4px 6px;
}
</style>

<style scoped>
/* App Container */
.app-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* Sidebar */
.sidebar {
  width: 240px;
  background: var(--theme-sidebar-bg);
  border-right: 1px solid var(--theme-border);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, background 0.3s ease;
  backdrop-filter: blur(10px);
  z-index: 100;
}

.sidebar.collapsed {
  width: 70px;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--theme-border);
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 28px;
  color: var(--primary);
  filter: drop-shadow(0 0 8px rgba(0, 212, 255, 0.5));
}

.logo-icon-mini {
  font-size: 24px;
  color: var(--primary);
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-title {
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(90deg, var(--primary), var(--accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 2px;
}

.logo-subtitle {
  font-size: 9px;
  color: var(--text-muted);
  letter-spacing: 3px;
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  padding: 15px 10px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 15px;
  border-radius: 10px;
  color: var(--text-secondary);
  text-decoration: none;
  position: relative;
  transition: all 0.3s ease;
  overflow: hidden;
}

.nav-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.1), transparent);
  opacity: 0;
  transition: opacity 0.3s;
}

.nav-item:hover {
  color: var(--text-primary);
  background: rgba(0, 212, 255, 0.08);
}

.nav-item:hover::before {
  opacity: 1;
}

.nav-item.active {
  color: var(--primary);
  background: rgba(0, 212, 255, 0.15);
}

.nav-item.active .nav-indicator {
  opacity: 1;
}

.nav-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.nav-label {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.nav-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 24px;
  background: var(--primary);
  border-radius: 0 3px 3px 0;
  opacity: 0;
  transition: opacity 0.3s;
  box-shadow: 0 0 10px var(--primary);
}

/* Sidebar Footer */
.sidebar-footer {
  padding: 15px;
  border-top: 1px solid var(--theme-border);
}

.collapse-btn {
  width: 100%;
  padding: 10px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.collapse-btn:hover {
  background: var(--el-fill-color);
  color: var(--theme-accent);
}

/* Main Wrapper */
.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* Top Header */
.top-header {
  height: 60px;
  padding: 0 25px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--theme-header-bg);
  border-bottom: 1px solid var(--theme-border);
  backdrop-filter: blur(10px);
  transition: background 0.3s ease;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-item {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  transition: all 0.3s;
}

.header-item:hover {
  color: var(--theme-accent);
  background: var(--el-fill-color);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  background: rgba(0, 212, 255, 0.05);
  border: 1px solid var(--border-color);
  border-radius: 25px;
}

.user-avatar {
  background: linear-gradient(135deg, var(--primary), var(--accent));
  font-size: 14px;
}

.user-name {
  font-size: 13px;
  color: var(--text-secondary);
}

/* Page Content */
.page-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* Transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Custom Scrollbar */
.page-content::-webkit-scrollbar {
  width: 6px;
}

.page-content::-webkit-scrollbar-track {
  background: transparent;
}

.page-content::-webkit-scrollbar-thumb {
  background: rgba(0, 212, 255, 0.3);
  border-radius: 3px;
}

.page-content::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 212, 255, 0.5);
}

/* Alarm Bell Badge */
.alarm-bell {
  position: relative;
}

.alarm-badge :deep(.el-badge__content) {
  background: #f56c6c;
  border: none;
  font-size: 10px;
  height: 16px;
  min-width: 16px;
  line-height: 16px;
  padding: 0 4px;
  right: -4px;
  top: 2px;
}

/* Alarm Popover */
.alarm-popover {
  max-height: 450px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.alarm-popover-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--theme-border);
  margin-bottom: 12px;
}

.alarm-popover-header .title {
  font-weight: 600;
  font-size: 15px;
  color: var(--theme-text);
}

.alarm-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alarm-item {
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border-left: 3px solid var(--el-color-danger);
}

.alarm-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.alarm-time {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.alarm-message {
  font-size: 13px;
  color: var(--theme-text);
  line-height: 1.4;
  word-break: break-all;
}

/* Markdown 预览样式覆盖 */
.alarm-message :deep(.md-editor-preview-wrapper),
.alarm-message :deep(.md-editor),
.alarm-message :deep(.md-editor-preview),
.alarm-message :deep([class*="md-editor"]) {
  padding: 0 !important;
  background: transparent !important;
  background-color: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

.alarm-message :deep(.md-editor-preview) {
  font-size: 13px !important;
  line-height: 1.4 !important;
  color: var(--theme-text) !important;
  font-family: inherit !important;
}

.alarm-message :deep(.md-editor-preview p) {
  margin: 0 0 4px 0 !important;
  color: var(--theme-text) !important;
  background: transparent !important;
}

.alarm-message :deep(.md-editor-preview blockquote) {
  margin: 4px 0 !important;
  padding: 4px 8px !important;
  border-left: 3px solid var(--theme-accent, #00d4ff) !important;
  background: rgba(0, 212, 255, 0.1) !important;
  color: var(--theme-text) !important;
}

.alarm-message :deep(.md-editor-preview blockquote p) {
  color: var(--theme-text) !important;
}

.alarm-message :deep(.md-editor-preview strong),
.alarm-message :deep(.md-editor-preview b) {
  color: var(--theme-text) !important;
  font-weight: 600 !important;
}

.alarm-message :deep(.md-editor-preview h1),
.alarm-message :deep(.md-editor-preview h2),
.alarm-message :deep(.md-editor-preview h3),
.alarm-message :deep(.md-editor-preview h4),
.alarm-message :deep(.md-editor-preview h5),
.alarm-message :deep(.md-editor-preview h6) {
  color: var(--theme-text) !important;
  margin: 0 0 4px 0 !important;
  font-size: 13px !important;
}

.alarm-message :deep(.md-editor-preview a) {
  color: var(--theme-accent, #00d4ff) !important;
}

.alarm-message :deep(.md-editor-preview code) {
  color: var(--theme-text) !important;
  background: rgba(0, 212, 255, 0.1) !important;
  padding: 1px 4px !important;
  border-radius: 3px !important;
}

.alarm-message :deep(.md-editor-preview *) {
  color: inherit;
  background: transparent !important;
}

.alarm-actions {
  margin-top: 8px;
  text-align: right;
}

.alarm-actions .action-btn {
  padding: 4px 12px;
  cursor: pointer;
  font-size: 13px;
  border-radius: 4px;
  margin-left: 8px;
}

.alarm-actions .action-btn.confirm {
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
}

.alarm-actions .action-btn.confirm:hover {
  background: rgba(64, 158, 255, 0.2);
}

.alarm-actions .action-btn.suppress {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.1);
}

.alarm-actions .action-btn.suppress:hover {
  background: rgba(230, 162, 60, 0.2);
}

.alarm-popover-footer {
  padding-top: 12px;
  border-top: 1px solid var(--theme-border);
  margin-top: 12px;
  text-align: center;
}

.alarm-popover-footer a {
  color: var(--el-color-primary);
  text-decoration: none;
  font-size: 13px;
}

.alarm-popover-footer a:hover {
  text-decoration: underline;
}
</style>
