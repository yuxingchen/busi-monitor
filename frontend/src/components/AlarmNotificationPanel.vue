<template>
    <div class="alarm-notification-panel" v-show="notifications.length > 0">
        <TransitionGroup name="alarm-slide" tag="div" class="notification-list">
            <div v-for="notification in notifications" :key="notification.id" class="notification-item"
                :class="getLevelClass(notification.level)">
                <div class="notification-header">
                    <el-icon class="notification-icon" :class="getLevelClass(notification.level)">
                        <WarningFilled v-if="notification.level === 'CRITICAL'" />
                        <Warning v-else-if="notification.level === 'WARNING'" />
                        <InfoFilled v-else />
                    </el-icon>
                    <span class="notification-title">{{ notification.title || '系统告警' }}</span>
                    <el-icon class="close-btn" @click="dismiss(notification.id)">
                        <Close />
                    </el-icon>
                </div>
                <div class="notification-content">{{ notification.message }}</div>
                <div class="notification-footer">
                    <span class="notification-time">{{ formatTime(notification.time) }}</span>
                </div>
            </div>
        </TransitionGroup>
    </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { WarningFilled, Warning, InfoFilled, Close } from '@element-plus/icons-vue'

const props = defineProps({
    maxCount: {
        type: Number,
        default: 8
    }
})

const notifications = ref([])
let idCounter = 0

// 添加通知
const addNotification = (notification) => {
    const id = ++idCounter
    const item = {
        id,
        title: notification.title || '系统告警',
        message: notification.message,
        level: notification.level || 'WARNING',
        time: new Date()
    }

    notifications.value.unshift(item)

    // 限制最大数量，移除最早的
    while (notifications.value.length > props.maxCount) {
        notifications.value.pop()
    }

    // 自动消失（可选）
    if (notification.duration !== 0) {
        setTimeout(() => {
            dismiss(id)
        }, notification.duration || 8000)
    }
}

// 关闭通知
const dismiss = (id) => {
    const idx = notifications.value.findIndex(n => n.id === id)
    if (idx >= 0) {
        notifications.value.splice(idx, 1)
    }
}

// 清空所有
const clear = () => {
    notifications.value = []
}

// 获取级别样式类
const getLevelClass = (level) => {
    return {
        'level-critical': level === 'CRITICAL',
        'level-warning': level === 'WARNING',
        'level-info': level === 'INFO'
    }
}

// 格式化时间
const formatTime = (time) => {
    if (!time) return ''
    const d = new Date(time)
    return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

// 暴露方法供父组件调用
defineExpose({
    addNotification,
    dismiss,
    clear
})
</script>

<style scoped>
.alarm-notification-panel {
    position: fixed;
    right: 20px;
    top: 80px;
    z-index: 9999;
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-height: calc(100vh - 120px);
    overflow: hidden;
    pointer-events: none;
}

.notification-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.notification-item {
    width: 360px;
    background: var(--theme-card-bg, #1e2a3a);
    border-radius: 8px;
    padding: 14px 16px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    border-left: 4px solid var(--el-color-warning);
    pointer-events: auto;
    backdrop-filter: blur(10px);
}

.notification-item.level-critical {
    border-left-color: var(--el-color-danger, #f56c6c);
    background: linear-gradient(90deg, rgba(245, 108, 108, 0.1), transparent);
}

.notification-item.level-warning {
    border-left-color: var(--el-color-warning, #e6a23c);
    background: linear-gradient(90deg, rgba(230, 162, 60, 0.1), transparent);
}

.notification-item.level-info {
    border-left-color: var(--el-color-info, #909399);
    background: linear-gradient(90deg, rgba(144, 147, 153, 0.1), transparent);
}

.notification-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
}

.notification-icon {
    font-size: 18px;
}

.notification-icon.level-critical {
    color: var(--el-color-danger, #f56c6c);
}

.notification-icon.level-warning {
    color: var(--el-color-warning, #e6a23c);
}

.notification-icon.level-info {
    color: var(--el-color-info, #909399);
}

.notification-title {
    flex: 1;
    font-weight: 600;
    font-size: 14px;
    color: var(--theme-text, #fff);
}

.close-btn {
    cursor: pointer;
    color: var(--theme-text-secondary, #999);
    font-size: 16px;
    transition: color 0.2s;
}

.close-btn:hover {
    color: var(--theme-text, #fff);
}

.notification-content {
    font-size: 13px;
    color: var(--theme-text-secondary, #b0b8c8);
    line-height: 1.5;
    word-break: break-all;
}

.notification-footer {
    margin-top: 8px;
    display: flex;
    justify-content: flex-end;
}

.notification-time {
    font-size: 12px;
    color: var(--theme-text-muted, #666);
}

/* 滑动动画 */
.alarm-slide-enter-active {
    animation: slideIn 0.3s ease-out;
}

.alarm-slide-leave-active {
    animation: slideOut 0.3s ease-in;
}

.alarm-slide-move {
    transition: transform 0.3s ease;
}

@keyframes slideIn {
    from {
        transform: translateX(100%);
        opacity: 0;
    }

    to {
        transform: translateX(0);
        opacity: 1;
    }
}

@keyframes slideOut {
    from {
        transform: translateX(0);
        opacity: 1;
    }

    to {
        transform: translateX(100%);
        opacity: 0;
    }
}
</style>
