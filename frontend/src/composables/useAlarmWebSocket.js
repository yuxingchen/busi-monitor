import { ref, onMounted, onUnmounted } from 'vue'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'
import { ElNotification } from 'element-plus'

/**
 * 告警 WebSocket Composable
 * 用于连接后端 WebSocket 并接收告警实时推送
 */
export function useAlarmWebSocket() {
    const alarms = ref([])
    const connected = ref(false)
    let stompClient = null
    
    /**
     * 连接 WebSocket
     */
    const connect = () => {
        // 创建 STOMP 客户端
        stompClient = new Client({
            // 使用 SockJS 作为传输层
            webSocketFactory: () => new SockJS('/api/ws'),
            
            // 调试日志（生产环境可关闭）
            debug: (str) => {
                console.log('[STOMP]', str)
            },
            
            // 重连配置
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            
            // 连接成功回调
            onConnect: () => {
                connected.value = true
                console.log('[告警WebSocket] 连接成功')
                
                // 订阅告警主题
                stompClient.subscribe('/topic/alarm', (message) => {
                    try {
                        const data = JSON.parse(message.body)
                        handleAlarmMessage(data)
                    } catch (e) {
                        console.error('[告警WebSocket] 消息解析失败:', e)
                    }
                })
            },
            
            // 连接断开回调
            onDisconnect: () => {
                connected.value = false
                console.log('[告警WebSocket] 连接断开')
            },
            
            // 错误回调
            onStompError: (frame) => {
                console.error('[告警WebSocket] STOMP错误:', frame.headers['message'])
            }
        })
        
        // 激活连接
        stompClient.activate()
    }
    
    /**
     * 处理告警消息
     */
    const handleAlarmMessage = (data) => {
        switch (data.type) {
            case 'FIRING':
                // 新告警：添加到列表并显示通知
                alarms.value.unshift(data)
                showAlarmNotification(data)
                break
                
            case 'RESOLVED':
                // 告警恢复：从列表移除
                alarms.value = alarms.value.filter(a => a.id !== data.alarmId)
                ElNotification({
                    title: '告警已恢复',
                    message: `告警 #${data.alarmId} 已恢复`,
                    type: 'success',
                    duration: 5000
                })
                break
                
            case 'ACKNOWLEDGED':
                // 告警确认：更新状态
                const alarm = alarms.value.find(a => a.id === data.alarmId)
                if (alarm) {
                    alarm.status = 'ACKNOWLEDGED'
                    alarm.acknowledgeBy = data.acknowledgeBy
                }
                break
                
            default:
                console.log('[告警WebSocket] 未知消息类型:', data.type)
        }
    }
    
    /**
     * 显示告警通知
     */
    const showAlarmNotification = (alarm) => {
        const type = alarm.level === 'CRITICAL' ? 'error' : 'warning'
        
        ElNotification({
            title: `【${alarm.level}】告警`,
            message: alarm.message || `任务 ${alarm.taskId} 触发告警`,
            type: type,
            duration: 0, // 不自动关闭
            position: 'top-right'
        })
    }
    
    /**
     * 断开连接
     */
    const disconnect = () => {
        if (stompClient) {
            stompClient.deactivate()
            stompClient = null
        }
        connected.value = false
    }
    
    /**
     * 清除所有告警
     */
    const clearAlarms = () => {
        alarms.value = []
    }
    
    // 生命周期钩子
    onMounted(() => {
        connect()
    })
    
    onUnmounted(() => {
        disconnect()
    })
    
    return {
        alarms,
        connected,
        connect,
        disconnect,
        clearAlarms
    }
}
