<template>
  <div class="alarm-config">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 报警通道配置 -->
      <el-tab-pane label="报警通道" name="channel">
        <div class="section-header">
          <el-button type="primary" @click="showChannelDialog()">
            <el-icon>
              <Plus />
            </el-icon> 新建通道
          </el-button>
        </div>

        <el-table :data="channels" stripe>
          <el-table-column prop="name" label="通道名称" />
          <el-table-column prop="type" label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getChannelTypeTag(row.type)">{{ getChannelTypeName(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isActive" label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.isActive" :active-value="1" :inactive-value="0"
                @change="updateChannelStatus(row)" />
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="showChannelDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteChannel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 报警模板配置 -->
      <el-tab-pane label="报警模板" name="template">
        <div class="section-header">
          <el-button type="primary" @click="showTemplateDialog()">
            <el-icon>
              <Plus />
            </el-icon> 新建模板
          </el-button>
        </div>

        <el-table :data="templates" stripe>
          <el-table-column prop="name" label="模板名称" width="200" />
          <el-table-column prop="subject" label="标题" width="200" />
          <el-table-column prop="content" label="模板内容" show-overflow-tooltip />
          <el-table-column prop="isDefault" label="默认" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
              <el-tag v-else type="info" size="small">否</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="showTemplateDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 报警历史 -->
      <el-tab-pane label="报警历史" name="history">
        <el-table :data="history" stripe>
          <el-table-column prop="triggerTime" label="触发时间" width="180" />
          <el-table-column prop="taskId" label="任务ID" width="80" />
          <el-table-column prop="triggerType" label="触发类型" width="100">
            <template #default="{ row }">
              {{ getTriggerTypeName(row.triggerType) }}
            </template>
          </el-table-column>
          <el-table-column prop="triggerValue" label="触发值" width="100" />
          <el-table-column prop="thresholdValue" label="阈值" width="100" />
          <el-table-column prop="isSuccess" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isSuccess ? 'success' : 'danger'">
                {{ row.isSuccess ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="消息内容" show-overflow-tooltip />
        </el-table>
        <div class="pagination-container">
          <el-pagination v-model:current-page="historyPage" v-model:page-size="historyPageSize"
            :page-sizes="[10, 20, 50, 100]" :total="historyTotal" layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadHistory" @current-change="loadHistory" />
        </div>
      </el-tab-pane>

      <!-- 活跃告警 -->
      <el-tab-pane name="active">
        <template #label>
          <span>
            活跃告警
            <el-badge v-if="activeAlarms.length > 0" :value="activeAlarms.length" class="alarm-badge" />
          </span>
        </template>
        <div class="section-header">
          <el-button type="primary" @click="loadActiveAlarms">
            <el-icon>
              <Refresh />
            </el-icon> 刷新
          </el-button>
        </div>

        <el-table :data="activeAlarms" stripe row-key="id">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="row.level === 'CRITICAL' ? 'danger' : 'warning'">
                {{ row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getAlarmStatusType(row.status)">{{ getAlarmStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="taskType" label="任务类型" width="120" />
          <el-table-column prop="taskId" label="任务ID" width="80" />
          <el-table-column prop="message" label="告警消息" show-overflow-tooltip />
          <el-table-column prop="triggerCount" label="触发次数" width="90" />
          <el-table-column prop="firstTriggerTime" label="首次触发" width="180" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" type="primary" :disabled="row.status !== 'FIRING'"
                @click="acknowledgeAlarm(row.id)">确认</el-button>
              <el-button size="small" type="warning" :disabled="row.status === 'SUPPRESSED'"
                @click="showSuppressDialog(row)">抑制</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 通道编辑对话框 -->
    <el-dialog v-model="channelDialogVisible" :title="editingChannel.id ? '编辑通道' : '新建通道'" width="600px">
      <el-form :model="editingChannel" label-width="100px">
        <el-form-item label="通道名称" required>
          <el-input v-model="editingChannel.name" placeholder="如：运维告警邮箱" />
        </el-form-item>
        <el-form-item label="通道类型" required>
          <el-select v-model="editingChannel.type" @change="onChannelTypeChange">
            <el-option label="邮件" value="EMAIL" />
            <el-option label="短信" value="SMS" />
            <el-option label="钉钉机器人" value="DINGTALK" />
            <el-option label="企业微信" value="WECHAT" />
            <el-option label="系统公告" value="ANNOUNCEMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="editingChannel.isActive" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <!-- 根据类型显示不同配置 -->
        <el-divider content-position="left">通道配置</el-divider>

        <template v-if="editingChannel.type === 'EMAIL'">
          <el-form-item label="SMTP服务器">
            <el-input v-model="channelConfig.host" placeholder="smtp.example.com" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="channelConfig.port" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="channelConfig.username" placeholder="alarm@example.com" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="channelConfig.password" type="password" show-password />
          </el-form-item>
          <el-form-item label="收件人">
            <el-input v-model="channelConfig.recipients" placeholder="多个邮箱用逗号分隔" />
          </el-form-item>
        </template>

        <template v-else-if="editingChannel.type === 'DINGTALK'">
          <el-form-item label="Webhook">
            <el-input v-model="channelConfig.webhook"
              placeholder="https://oapi.dingtalk.com/robot/send?access_token=xxx" />
          </el-form-item>
          <el-form-item label="加签密钥">
            <el-input v-model="channelConfig.secret" placeholder="SECxxx (可选)" />
          </el-form-item>
        </template>

        <template v-else-if="editingChannel.type === 'WECHAT'">
          <el-form-item label="Webhook">
            <el-input v-model="channelConfig.webhook"
              placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx" />
          </el-form-item>
        </template>

        <template v-else-if="editingChannel.type === 'SMS'">
          <el-form-item label="接口URL">
            <el-input v-model="channelConfig.apiUrl" placeholder="https://sms.api.com/send" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="channelConfig.apiKey" />
          </el-form-item>
          <el-form-item label="接收手机">
            <el-input v-model="channelConfig.phones" placeholder="多个手机号用逗号分隔" />
          </el-form-item>
        </template>

        <template v-else-if="editingChannel.type === 'ANNOUNCEMENT'">
          <el-form-item label="显示时长(秒)">
            <el-input-number v-model="channelConfig.displayDuration" :min="60" :max="86400" />
          </el-form-item>
          <el-form-item label="告警级别">
            <el-select v-model="channelConfig.level">
              <el-option label="信息" value="info" />
              <el-option label="警告" value="warning" />
              <el-option label="错误" value="error" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="channelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveChannel">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板编辑对话框 -->
    <el-dialog v-model="templateDialogVisible" :title="editingTemplate.id ? '编辑模板' : '新建模板'" width="900px">
      <el-form :model="editingTemplate" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="editingTemplate.name" placeholder="如：默认告警模板" />
        </el-form-item>
        <el-form-item label="标题模板">
          <el-input v-model="editingTemplate.subject" placeholder="【告警】${taskName} 触发报警" />
        </el-form-item>
        <el-form-item label="内容格式">
          <el-select v-model="editingTemplate.contentType" style="width: 150px">
            <el-option label="纯文本" value="TEXT" />
            <el-option label="Markdown" value="MARKDOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容模板" required>
          <template v-if="editingTemplate.contentType === 'MARKDOWN'">
            <div class="md-editor-wrapper">
              <MdEditor v-model="editingTemplate.content" :preview="true" :toolbarsExclude="['github', 'save', 'mermaid', 'katex']" placeholder="支持 Markdown 语法，可使用变量: ${taskName}, ${ip}, ${value} 等" language="zh-CN" :style="{ height: '280px' }" />
            </div>
          </template>
          <template v-else>
            <el-input v-model="editingTemplate.content" type="textarea" :rows="8" placeholder="支持变量: ${taskName}, ${ip}, ${value}, ${threshold}, ${time}, ${triggerType}" />
          </template>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="editingTemplate.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-alert type="info" :closable="false" style="margin-top: 10px;">
          <p><strong>可用变量：</strong></p>
          <p><code>${taskName}</code> - 任务名称</p>
          <p><code>${ip}</code> - 服务器 IP 地址</p>
          <p><code>${serverName}</code> - 服务器名称</p>
          <p><code>${value}</code> - 当前值</p>
          <p><code>${threshold}</code> - 阈值</p>
          <p><code>${time}</code> - 触发时间</p>
          <p><code>${triggerType}</code> - 触发类型</p>
        </el-alert>
      </el-form>

      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import request from '../api/request'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'

import { useRoute } from 'vue-router'

const route = useRoute()
const activeTab = ref('channel')
const channels = ref([])
const templates = ref([])
const history = ref([])
const historyPage = ref(1)
const historyPageSize = ref(20)
const historyTotal = ref(0)
const activeAlarms = ref([])
const wsConnected = ref(false)
let stompClient = null

const channelDialogVisible = ref(false)
const templateDialogVisible = ref(false)

const editingChannel = reactive({
  id: null,
  name: '',
  type: 'EMAIL',
  isActive: 1,
  config: ''
})

const channelConfig = reactive({
  // EMAIL
  host: '',
  port: 465,
  username: '',
  password: '',
  recipients: '',
  // DINGTALK
  webhook: '',
  secret: '',
  // SMS
  apiUrl: '',
  apiKey: '',
  phones: '',
  // ANNOUNCEMENT
  displayDuration: 3600,
  level: 'warning'
})

const editingTemplate = reactive({
  id: null,
  name: '',
  channelType: 'EMAIL',
  subject: '',
  content: '',
  contentType: 'TEXT',
  isDefault: 0
})

const getChannelTypeName = (type) => {
  const map = { EMAIL: '邮件', SMS: '短信', DINGTALK: '钉钉', WECHAT: '微信', ANNOUNCEMENT: '公告' }
  return map[type] || type
}

const getChannelTypeTag = (type) => {
  const map = { EMAIL: 'primary', SMS: 'success', DINGTALK: 'warning', WECHAT: 'success', ANNOUNCEMENT: 'info' }
  return map[type] || ''
}

const getTriggerTypeName = (type) => {
  const map = { THRESHOLD: '阈值', YOY: '同比', MOM: '环比' }
  return map[type] || type
}

const loadHistory = async () => {
  try {
    const res = await request.get('/alarm/history/page', {
      params: { page: historyPage.value, pageSize: historyPageSize.value }
    })
    history.value = res.records || []
    historyTotal.value = res.total || 0
  } catch (e) {
    console.error(e)
  }
}

const loadData = async () => {
  try {
    const [ch, tp] = await Promise.all([
      request.get('/alarm/channel'),
      request.get('/alarm/template')
    ])
    channels.value = ch
    templates.value = tp
    await loadHistory()
  } catch (e) {
    console.error(e)
  }
}

const showChannelDialog = (channel = null) => {
  if (channel) {
    Object.assign(editingChannel, channel)
    try {
      const cfg = JSON.parse(channel.config || '{}')
      Object.assign(channelConfig, cfg)
    } catch (e) { }
  } else {
    Object.assign(editingChannel, { id: null, name: '', type: 'EMAIL', isActive: 1 })
    Object.assign(channelConfig, { host: '', port: 465, username: '', password: '', recipients: '' })
  }
  channelDialogVisible.value = true
}

const onChannelTypeChange = () => {
  // Reset config based on type
  if (editingChannel.type === 'EMAIL') {
    Object.assign(channelConfig, { host: '', port: 465, username: '', password: '', recipients: '' })
  } else if (editingChannel.type === 'DINGTALK') {
    Object.assign(channelConfig, { webhook: '', secret: '' })
  } else if (editingChannel.type === 'WECHAT') {
    Object.assign(channelConfig, { webhook: '' })
  } else if (editingChannel.type === 'SMS') {
    Object.assign(channelConfig, { apiUrl: '', apiKey: '', phones: '' })
  } else {
    Object.assign(channelConfig, { displayDuration: 3600, level: 'warning' })
  }
}

const saveChannel = async () => {
  editingChannel.config = JSON.stringify(channelConfig)
  try {
    if (editingChannel.id) {
      await request.put('/alarm/channel', editingChannel)
    } else {
      await request.post('/alarm/channel', editingChannel)
    }
    ElMessage.success('保存成功')
    channelDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const updateChannelStatus = async (channel) => {
  await request.put('/alarm/channel', channel)
}

const deleteChannel = async (channel) => {
  await ElMessageBox.confirm('确定删除该通道吗？', '确认')
  await request.delete(`/alarm/channel/${channel.id}`)
  ElMessage.success('删除成功')
  loadData()
}

const showTemplateDialog = (template = null) => {
  if (template) {
    // 编辑时，如果没有 contentType 字段，默认设为 TEXT
    Object.assign(editingTemplate, { contentType: 'TEXT', ...template })
  } else {
    Object.assign(editingTemplate, { id: null, name: '', channelType: 'EMAIL', subject: '', content: '', contentType: 'TEXT', isDefault: 0 })
  }
  templateDialogVisible.value = true
}

const saveTemplate = async () => {
  try {
    if (editingTemplate.id) {
      await request.put('/alarm/template', editingTemplate)
    } else {
      await request.post('/alarm/template', editingTemplate)
    }
    ElMessage.success('保存成功')
    templateDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const deleteTemplate = async (template) => {
  await ElMessageBox.confirm('确定删除该模板吗？', '确认')
  await request.delete(`/alarm/template/${template.id}`)
  ElMessage.success('删除成功')
  loadData()
}

// ============ 活跃告警相关 ============

const getAlarmStatusType = (status) => {
  const map = { FIRING: 'danger', ACKNOWLEDGED: 'warning', SUPPRESSED: 'info', RESOLVED: 'success' }
  return map[status] || ''
}

const getAlarmStatusName = (status) => {
  const map = { FIRING: '触发中', ACKNOWLEDGED: '已确认', SUPPRESSED: '已抑制', RESOLVED: '已恢复' }
  return map[status] || status
}

const loadActiveAlarms = async () => {
  try {
    activeAlarms.value = await request.get('/alarm/active')
  } catch (e) {
    ElMessage.error('加载活跃告警失败')
  }
}

const acknowledgeAlarm = async (id) => {
  try {
    await request.post(`/alarm/active/${id}/acknowledge`)
    ElMessage.success('告警已确认')
    loadActiveAlarms()
  } catch (e) {
    ElMessage.error('确认告警失败')
  }
}

const showSuppressDialog = async (alarm) => {
  const { value } = await ElMessageBox.prompt('请输入抑制时长（分钟）', '抑制告警', {
    inputValue: '30',
    inputPattern: /^\d+$/,
    inputErrorMessage: '请输入正整数'
  })
  try {
    await request.post(`/alarm/active/${alarm.id}/suppress?minutes=${value}`)
    ElMessage.success(`已抑制 ${value} 分钟`)
    loadActiveAlarms()
  } catch (e) {
    ElMessage.error('抑制告警失败')
  }
}

// ============ WebSocket 连接 ============

const connectWebSocket = () => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/api/ws'),
    debug: (str) => console.log('[STOMP]', str),
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,

    onConnect: () => {
      wsConnected.value = true
      console.log('[告警WebSocket] 连接成功')

      stompClient.subscribe('/topic/alarm', (message) => {
        try {
          const data = JSON.parse(message.body)
          handleAlarmMessage(data)
        } catch (e) {
          console.error('[告警WebSocket] 消息解析失败:', e)
        }
      })
    },

    onDisconnect: () => {
      wsConnected.value = false
      console.log('[告警WebSocket] 连接断开')
    },

    onStompError: (frame) => {
      console.error('[告警WebSocket] STOMP错误:', frame.headers['message'])
    }
  })

  stompClient.activate()
}

const handleAlarmMessage = (data) => {
  switch (data.type || data.status) {
    case 'FIRING':
      // 重新加载活跃告警（不显示通知，App.vue已全局处理）
      loadActiveAlarms()
      break

    case 'RESOLVED':
      loadActiveAlarms()
      break

    case 'ACKNOWLEDGED':
      loadActiveAlarms()
      break
  }
}

const disconnectWebSocket = () => {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
  }
  wsConnected.value = false
}
// 定时刷新活跃告警（App.vue 已全局处理 WebSocket 通知）
let refreshTimer = null

onMounted(() => {
  loadData()
  loadActiveAlarms()

  // 定时刷新活跃告警
  refreshTimer = setInterval(() => {
    loadActiveAlarms()
  }, 5000)

  // 处理路由参数
  if (route.query.tab) {
    activeTab.value = route.query.tab
  }
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.alarm-config {
  padding: 20px;
  min-height: calc(100vh - 80px);
  background: var(--theme-bg);
}

.alarm-config :deep(.el-tabs--border-card) {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.alarm-config :deep(.el-tabs__header) {
  background: var(--el-fill-color);
  border-bottom: 1px solid var(--theme-border);
}

.alarm-config :deep(.el-tabs__item) {
  color: var(--theme-text-secondary);
}

.alarm-config :deep(.el-tabs__item.is-active) {
  color: var(--theme-accent);
  font-weight: 600;
}

.alarm-config :deep(.el-tabs__content) {
  padding: 20px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.section-header {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--theme-border);
  display: flex;
  align-items: center;
}

.alarm-badge {
  margin-left: 6px;
}

.alarm-badge :deep(.el-badge__content) {
  background: var(--el-color-danger);
}

.alarm-config :deep(.el-table) {
  background: transparent;
  --el-table-border-color: var(--theme-border);
  --el-table-header-bg-color: var(--el-fill-color);
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: var(--el-fill-color-light);
}

.alarm-config :deep(.el-table th.el-table__cell) {
  background: var(--el-fill-color);
  color: var(--theme-text);
  font-weight: 600;
}

.alarm-config :deep(.el-table td.el-table__cell) {
  color: var(--theme-text-secondary);
}

.alarm-config :deep(.el-dialog) {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
}

.alarm-config :deep(.el-dialog__header) {
  border-bottom: 1px solid var(--theme-border);
  padding: 16px 20px;
}

.alarm-config :deep(.el-dialog__title) {
  color: var(--theme-text);
  font-weight: 600;
}

.alarm-config :deep(.el-dialog__body) {
  padding: 20px;
}

.alarm-config :deep(.el-dialog__footer) {
  border-top: 1px solid var(--theme-border);
  padding: 12px 20px;
}

.alarm-config :deep(.el-form-item__label) {
  color: var(--theme-text);
}

.alarm-config :deep(.el-input__wrapper),
.alarm-config :deep(.el-textarea__inner) {
  background: var(--el-fill-color);
  border-color: var(--theme-border);
  box-shadow: none;
}

.alarm-config :deep(.el-input__wrapper:hover),
.alarm-config :deep(.el-textarea__inner:hover) {
  border-color: var(--theme-accent);
}

.alarm-config :deep(.el-input__inner),
.alarm-config :deep(.el-textarea__inner) {
  color: var(--theme-text);
}

.alarm-config :deep(.el-divider__text) {
  background: var(--theme-card-bg);
  color: var(--theme-text-secondary);
}

.alarm-config :deep(.el-alert--info) {
  background: var(--el-fill-color);
  border: 1px solid var(--theme-border);
}

.alarm-config :deep(.el-alert__description) {
  color: var(--theme-text-secondary);
}

.alarm-config :deep(.el-alert__description code) {
  background: var(--el-fill-color-light);
  color: var(--theme-accent);
  padding: 2px 6px;
  border-radius: 4px;
}

/* Select 下拉框 */
.alarm-config :deep(.el-select .el-input__wrapper) {
  background: var(--el-fill-color);
  border-color: var(--theme-border);
  box-shadow: none;
}

.alarm-config :deep(.el-select .el-input__inner) {
  color: var(--theme-text);
}

/* InputNumber 数字输入框 */
.alarm-config :deep(.el-input-number .el-input__wrapper) {
  background: var(--el-fill-color);
  border-color: var(--theme-border);
  box-shadow: none;
}

.alarm-config :deep(.el-input-number .el-input__inner) {
  color: var(--theme-text);
}

.alarm-config :deep(.el-input-number__decrease),
.alarm-config :deep(.el-input-number__increase) {
  background: var(--el-fill-color-light);
  border-color: var(--theme-border);
  color: var(--theme-text-secondary);
}

.alarm-config :deep(.el-input-number__decrease:hover),
.alarm-config :deep(.el-input-number__increase:hover) {
  color: var(--theme-accent);
}

/* Switch 开关 */
.alarm-config :deep(.el-switch__core) {
  border-color: var(--theme-border);
}

/* Button 按钮 */
.alarm-config :deep(.el-button--primary) {
  background: var(--theme-accent);
  border-color: var(--theme-accent);
}

.alarm-config :deep(.el-button--default) {
  background: var(--el-fill-color);
  border-color: var(--theme-border);
  color: var(--theme-text);
}

.alarm-config :deep(.el-button--default:hover) {
  border-color: var(--theme-accent);
  color: var(--theme-accent);
}

/* Tag 标签 */
.alarm-config :deep(.el-tag) {
  border-color: transparent;
}

/* Empty 空状态 */
.alarm-config :deep(.el-table__empty-text) {
  color: var(--theme-text-secondary);
}

/* Overlay 遮罩层 */
.alarm-config :deep(.el-overlay) {
  background: rgba(0, 0, 0, 0.5);
}

/* Popper 下拉菜单 */
.el-select__popper {
  background: var(--theme-card-bg) !important;
  border: 1px solid var(--theme-border) !important;
}

.el-select-dropdown__item {
  color: var(--theme-text) !important;
}

.el-select-dropdown__item:hover {
  background: var(--el-fill-color) !important;
}

.el-select-dropdown__item.is-selected {
  color: var(--theme-accent) !important;
}

/* Markdown 编辑器样式 */
.md-editor-wrapper {
  width: 100%;
}

.md-editor-wrapper :deep(.md-editor) {
  --md-bk-color: var(--el-fill-color) !important;
  --md-border-color: var(--theme-border) !important;
  --md-color: var(--theme-text) !important;
  border-radius: 8px;
  overflow: hidden;
}

.md-editor-wrapper :deep(.md-editor-dark) {
  --md-bk-color: var(--el-fill-color) !important;
  --md-border-color: var(--theme-border) !important;
  --md-color: var(--theme-text) !important;
}

.md-editor-wrapper :deep(.md-editor-toolbar) {
  background: var(--el-fill-color-light) !important;
  border-bottom: 1px solid var(--theme-border) !important;
}

.md-editor-wrapper :deep(.md-editor-content) {
  background: var(--el-fill-color) !important;
}

.md-editor-wrapper :deep(.md-editor-preview) {
  background: var(--el-fill-color-light) !important;
}
</style>
