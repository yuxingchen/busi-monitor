<template>
  <div class="server-asset">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 服务器列表 -->
      <el-tab-pane label="服务器列表" name="list">
        <div class="section-header">
          <el-button type="primary" @click="showAddDialog()">
            <el-icon>
              <Plus />
            </el-icon> 添加服务器
          </el-button>
          <el-button @click="showBatchDialog()">
            <el-icon>
              <DocumentAdd />
            </el-icon> 批量添加
          </el-button>
        </div>

        <el-table :data="servers" stripe>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="ip" label="IP地址" width="140" />
          <el-table-column prop="port" label="端口" width="80" />
          <el-table-column prop="username" label="用户名" width="100" />
          <el-table-column prop="lastCheckStatus" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.lastCheckStatus === 'ONLINE' ? 'success' : 'danger'" v-if="row.lastCheckStatus">
                {{ row.lastCheckStatus === 'ONLINE' ? '在线' : '离线' }}
              </el-tag>
              <el-tag type="info" v-else>未检测</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column label="操作" width="280">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="testConnection(row)" :loading="row.testing">测试</el-button>
              <el-button size="small" type="primary" @click="showTaskConfig(row)">监控配置</el-button>
              <el-button size="small" @click="showAddDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteServer(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 监控任务 -->
      <el-tab-pane label="监控任务" name="task">
        <div class="section-header">
          <el-select v-model="selectedServerId" placeholder="选择服务器" @change="loadServerTasks"
            style="width: 200px; margin-right: 10px;">
            <el-option v-for="s in servers" :key="s.id" :label="`${s.name} (${s.ip})`" :value="s.id" />
          </el-select>
          <el-button type="primary" @click="showTemplateSelector()" :disabled="!selectedServerId">
            <el-icon>
              <Plus />
            </el-icon> 添加监控
          </el-button>
        </div>

        <el-table :data="serverTasks" stripe v-if="selectedServerId">
          <el-table-column prop="name" label="监控项" min-width="40">
            <template #default="{ row }">
              <div class="task-name-cell">
                <MonitorIcon :type="getIconType(row)" :size="26" class="task-icon-svg" />
                <span class="task-name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="阈值规则" width="160">
            <template #default="{ row }">
              <span class="threshold-text">{{ formatThreshold(row.thresholdRule) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.lastRunStatus === 'SUCCESS' ? 'success' : row.lastRunStatus === 'FAILED' ? 'danger' : 'info'"
                size="small">
                {{ row.lastRunStatus || '未执行' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最新值" min-width="100">
            <template #default="{ row }">
              <span :class="getValueClass(row)">{{ row.lastRunValue || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="执行计划" width="100">
            <template #default="{ row }">
              <span class="cron-text">{{ formatCron(row.cronExpression) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="70">
            <template #default="{ row }">
              <el-switch v-model="row.isActive" size="small" :active-value="1" :inactive-value="0"
                @change="toggleTaskActive(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="runTask(row)" :loading="row.running">执行</el-button>
              <el-button size="small" @click="editTask(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteTask(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="请选择服务器" />
      </el-tab-pane>

      <!-- 服务器分组 -->
      <el-tab-pane label="分组管理" name="group">
        <div class="section-header">
          <el-button type="primary" @click="showGroupDialog()">
            <el-icon>
              <Plus />
            </el-icon> 添加分组
          </el-button>
        </div>
        <el-table :data="groups" stripe>
          <el-table-column prop="name" label="分组名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="showGroupDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteGroup(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加/编辑服务器对话框 -->
    <el-dialog v-model="addDialogVisible" :title="editingServer.id ? '编辑服务器' : '添加服务器'" width="500px">
      <el-form :model="editingServer" label-width="100px">
        <el-form-item label="服务器名称" required>
          <el-input v-model="editingServer.name" placeholder="如：Web服务器01" />
        </el-form-item>
        <el-form-item label="IP地址" required>
          <el-input v-model="editingServer.ip" placeholder="192.168.1.100" />
        </el-form-item>
        <el-form-item label="SSH端口">
          <el-input-number v-model="editingServer.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="editingServer.username" placeholder="root" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-radio-group v-model="editingServer.authType">
            <el-radio value="PASSWORD">密码</el-radio>
            <el-radio value="KEY">私钥</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="密码" v-if="editingServer.authType === 'PASSWORD'">
          <el-input v-model="editingServer.password" type="password" show-password placeholder="SSH密码" />
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="editingServer.groupId" placeholder="选择分组" clearable>
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveServer">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量添加对话框 -->
    <el-dialog v-model="batchDialogVisible" title="批量添加服务器" width="600px">
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="IP地址" required>
          <el-input v-model="batchForm.ipInput" type="textarea" :rows="3" placeholder="支持格式：
192.168.1.1 (单IP)
192.168.1.1,192.168.1.2 (多IP)
192.168.1.1-10 (IP段)
192.168.1.0/24 (CIDR)" />
        </el-form-item>
        <el-form-item label="SSH端口">
          <el-input-number v-model="batchForm.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="batchForm.username" placeholder="root" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="batchForm.password" type="password" show-password placeholder="所有服务器使用相同密码" />
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="batchForm.groupId" placeholder="选择分组" clearable>
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="batchAdd">批量添加</el-button>
      </template>
    </el-dialog>

    <!-- 分组对话框 -->
    <el-dialog v-model="groupDialogVisible" :title="editingGroup.id ? '编辑分组' : '添加分组'" width="400px">
      <el-form :model="editingGroup" label-width="80px">
        <el-form-item label="分组名称" required>
          <el-input v-model="editingGroup.name" placeholder="如：Web集群" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editingGroup.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板选择对话框 -->
    <el-dialog v-model="templateSelectorVisible" title="选择监控模板" width="900px" class="template-dialog">
      <el-tabs v-model="templateCategory" class="template-tabs">
        <el-tab-pane label="基础监控" name="BASIC">
          <div class="category-hint">服务器基础指标采集，通过SSH执行命令获取</div>
        </el-tab-pane>
        <el-tab-pane label="组件监控" name="COMPONENT">
          <div class="category-hint">常用中间件端口探测，检查服务是否在线</div>
        </el-tab-pane>
        <el-tab-pane label="应用监控" name="APPLICATION">
          <div class="category-hint">应用层监控，包括HTTP探测和日志分析</div>
        </el-tab-pane>
      </el-tabs>
      <div class="template-grid">
        <div v-for="t in filteredTemplates" :key="t.id"
          :class="['template-card', { 'selected': selectedTemplates.includes(t.id) }]" @click="toggleTemplate(t.id)">
          <div class="card-header">
            <el-checkbox :model-value="selectedTemplates.includes(t.id)" @click.stop="toggleTemplate(t.id)">
              <span class="card-title">{{ t.name }}</span>
            </el-checkbox>
          </div>
          <div class="card-body">
            <div class="card-desc">{{ t.description }}</div>
            <div class="card-info">
              <div class="info-item">
                <span class="info-label">采集方式:</span>
                <span class="info-value">{{ getCollectTypeLabel(t.collectType) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">告警阈值:</span>
                <span class="info-value threshold">{{ formatThreshold(t.defaultThreshold) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <span class="selected-count">已选择 {{ selectedTemplates.length }} 个模板</span>
          <div>
            <el-button @click="templateSelectorVisible = false">取消</el-button>
            <el-button type="primary" @click="addSelectedTemplates" :disabled="selectedTemplates.length === 0">
              确认添加
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 编辑任务对话框 -->
    <el-dialog v-model="taskEditVisible" title="编辑监控任务" width="600px">
      <el-form :model="editingTask" label-width="100px">
        <el-form-item label="任务名称">
          <el-input v-model="editingTask.name" />
        </el-form-item>
        <el-form-item label="采集脚本">
          <el-input v-model="editingTask.collectScript" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="执行频率">
          <el-select v-model="editingTask.cronExpression">
            <el-option label="每1分钟" value="0 */1 * * * ?" />
            <el-option label="每5分钟" value="0 */5 * * * ?" />
            <el-option label="每10分钟" value="0 */10 * * * ?" />
            <el-option label="每30分钟" value="0 */30 * * * ?" />
            <el-option label="每小时" value="0 0 * * * ?" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值规则">
          <el-row :gutter="10" style="width: 100%;">
            <el-col :span="8">
              <el-select v-model="thresholdConfig.operator" placeholder="条件" style="width: 100%;">
                <el-option label="大于" value=">" />
                <el-option label="大于等于" value=">=" />
                <el-option label="小于" value="<" />
                <el-option label="小于等于" value="<=" />
                <el-option label="等于" value="=" />
                <el-option label="不等于" value="!=" />
              </el-select>
            </el-col>
            <el-col :span="10">
              <el-input-number v-model="thresholdConfig.value" :precision="2" style="width: 100%;" placeholder="阈值" />
            </el-col>
            <el-col :span="6">
              <el-select v-model="thresholdConfig.level" placeholder="级别" style="width: 100%;">
                <el-option label="警告" value="WARNING" />
                <el-option label="严重" value="CRITICAL" />
              </el-select>
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editingTask.isActive" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="告警渠道">
          <el-select v-model="editingTask.alarmChannelIds" multiple placeholder="选择告警渠道（不选则使用默认）" style="width: 100%;"
            clearable>
            <el-option v-for="c in alarmChannels" :key="c.id" :label="`${c.name} (${getChannelTypeName(c.type)})`"
              :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警模板">
          <el-select v-model="editingTask.alarmTemplateId" placeholder="选择告警模板（用于告警消息格式）" style="width: 100%;" clearable
            @change="updateParsedParams">
            <el-option v-for="t in alarmTemplates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <!-- 脚本参数配置 -->
        <el-form-item label="脚本参数" v-if="parsedParams.length > 0">
          <div class="params-config">
            <div v-for="param in parsedParams" :key="param" class="param-row">
              <span class="param-name">${{ param }}</span>
              <el-input v-model="taskParams[param]" :placeholder="`请输入 ${param} 的值`" size="small" style="flex: 1;" />
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, DocumentAdd } from '@element-plus/icons-vue'
import { encryptPassword } from '../utils/crypto'
import { serverAssetApi, serverGroupApi, serverMonitorTaskApi } from '../api/server'
import { alarmChannelApi, alarmTemplateApi } from '../api/alarm'
import { monitorTemplateApi } from '../api/template'
import MonitorIcon from '../components/MonitorIcon.vue'
import { toJsonClean } from '../api/utils'

const activeTab = ref('list')
const servers = ref([])
const groups = ref([])
const templates = ref([])
const serverTasks = ref([])
const alarmChannels = ref([])
const alarmTemplates = ref([])
const selectedServerId = ref(null)

const addDialogVisible = ref(false)
const batchDialogVisible = ref(false)
const groupDialogVisible = ref(false)
const templateSelectorVisible = ref(false)
const taskEditVisible = ref(false)

const templateCategory = ref('BASIC')
const selectedTemplates = ref([])

const editingServer = reactive({
  id: null, name: '', ip: '', port: 22, username: 'root',
  authType: 'PASSWORD', password: '', groupId: null
})

const batchForm = reactive({
  ipInput: '', port: 22, username: 'root', password: '', groupId: null
})

const editingGroup = reactive({ id: null, name: '', description: '' })

const editingTask = reactive({
  id: null, name: '', collectScript: '', cronExpression: '0 */5 * * * ?',
  isActive: 1, alarmChannels: '', alarmChannelIds: [], thresholdRule: '', alarmTemplateId: null
})

const thresholdConfig = reactive({
  operator: '>',
  value: 0,
  level: 'WARNING'
})

// 任务参数配置
const taskParams = reactive({})  // 存储任务参数 { key: value }
const parsedParams = ref([])     // 解析出的参数列表 ['port', 'path', ...]

// 解析脚本中的 ${} 占位符
const parseScriptParams = (script) => {
  if (!script) return []
  const regex = /\$\{([^}]+)\}/g
  const params = new Set()
  let match
  while ((match = regex.exec(script)) !== null) {
    params.add(match[1])
  }
  return Array.from(params)
}

// 更新解析的参数列表（合并脚本参数和告警模板参数）
const updateParsedParams = () => {
  const scriptParams = parseScriptParams(editingTask.collectScript)
  let templateParams = []

  // 如果选择了告警模板，解析模板中的参数
  if (editingTask.alarmTemplateId) {
    const template = alarmTemplates.value.find(t => t.id === editingTask.alarmTemplateId)
    if (template && template.content) {
      templateParams = parseScriptParams(template.content)
    }
  }

  // 合并并去重，排除标准变量
  const standardVars = ['serverName', 'value', 'threshold', 'taskName', 'taskId', 'operator', 'triggerType', 'level', 'ip']
  const allParams = [...new Set([...scriptParams, ...templateParams])]
    .filter(p => !standardVars.includes(p))

  parsedParams.value = allParams
}

// 监听采集脚本变化，动态更新参数列表
watch(() => editingTask.collectScript, () => {
  updateParsedParams()
})

const filteredTemplates = computed(() => {
  return templates.value.filter(t => t.category === templateCategory.value)
})

const loadData = async () => {
  try {
    const [s, g, t, c, at] = await Promise.all([
      serverAssetApi.list(),
      serverGroupApi.list(),
      monitorTemplateApi.list(),
      alarmChannelApi.list(),
      alarmTemplateApi.list()
    ])
    servers.value = s
    groups.value = g
    templates.value = t
    alarmChannels.value = c.filter(ch => ch.isActive === 1)
    alarmTemplates.value = at
  } catch (e) { console.error(e) }
}

const loadServerTasks = async () => {
  if (!selectedServerId.value) return
  try {
    serverTasks.value = await serverMonitorTaskApi.listByServer(selectedServerId.value)
  } catch (e) { console.error(e) }
}

const showAddDialog = (server = null) => {
  if (server) {
    Object.assign(editingServer, server)
    editingServer.password = ''
  } else {
    Object.assign(editingServer, { id: null, name: '', ip: '', port: 22, username: 'root', authType: 'PASSWORD', password: '', groupId: null })
  }
  addDialogVisible.value = true
}

const showBatchDialog = () => {
  Object.assign(batchForm, { ipInput: '', port: 22, username: 'root', password: '', groupId: null })
  batchDialogVisible.value = true
}

const showGroupDialog = (group = null) => {
  if (group) {
    Object.assign(editingGroup, group)
  } else {
    Object.assign(editingGroup, { id: null, name: '', description: '' })
  }
  groupDialogVisible.value = true
}

const showTaskConfig = (server) => {
  selectedServerId.value = server.id
  activeTab.value = 'task'
  loadServerTasks()
}

const showTemplateSelector = () => {
  selectedTemplates.value = []
  templateSelectorVisible.value = true
}

const addSelectedTemplates = async () => {
  if (selectedTemplates.value.length === 0) {
    ElMessage.warning('请选择监控模板')
    return
  }
  try {
    const result = await serverMonitorTaskApi.batchAdd(selectedServerId.value, selectedTemplates.value)
    ElMessage.success(`成功添加 ${result.count} 个监控任务`)
    templateSelectorVisible.value = false
    loadServerTasks()
  } catch (e) { ElMessage.error('添加失败') }
}

const editTask = (task) => {
  Object.assign(editingTask, task)
  // 解析 alarmChannels 字符串为数组
  if (task.alarmChannels) {
    editingTask.alarmChannelIds = task.alarmChannels.split(',').map(id => parseInt(id))
  } else {
    editingTask.alarmChannelIds = []
  }
  // 解析 thresholdRule 为 thresholdConfig
  if (task.thresholdRule) {
    try {
      const rule = JSON.parse(task.thresholdRule)
      thresholdConfig.operator = rule.operator || '>'
      thresholdConfig.value = rule.value || 0
      thresholdConfig.level = rule.level || 'WARNING'
    } catch (e) {
      thresholdConfig.operator = '>'
      thresholdConfig.value = 0
      thresholdConfig.level = 'WARNING'
    }
  } else {
    thresholdConfig.operator = '>'
    thresholdConfig.value = 0
    thresholdConfig.level = 'WARNING'
  }
  // 解析已保存的参数
  Object.keys(taskParams).forEach(k => delete taskParams[k])
  if (task.params) {
    try {
      const savedParams = JSON.parse(task.params)
      Object.assign(taskParams, savedParams)
    } catch (e) {
      console.error('解析参数失败', e)
    }
  }
  updateParsedParams()
  taskEditVisible.value = true
}

const saveTask = async () => {
  try {
    // 将数组转回逗号分隔字符串
    editingTask.alarmChannels = editingTask.alarmChannelIds.length > 0
      ? editingTask.alarmChannelIds.join(',')
      : null
    // 将 thresholdConfig 序列化为 JSON 字符串
    editingTask.thresholdRule = toJsonClean({
      operator: thresholdConfig.operator,
      value: thresholdConfig.value,
      level: thresholdConfig.level
    })
    // 序列化参数
    if (Object.keys(taskParams).length > 0) {
      editingTask.params = toJsonClean(taskParams)
    } else {
      editingTask.params = null
    }
    // 排除前端辅助字段，只发送后端需要的字段
    const { alarmChannelIds, ...taskData } = editingTask
    await serverMonitorTaskApi.update(taskData)
    ElMessage.success('保存成功')
    taskEditVisible.value = false
    loadServerTasks()
  } catch (e) { ElMessage.error('保存失败') }
}

const runTask = async (task) => {
  task.running = true
  try {
    // request拦截器已自动解包响应，成功时返回的就是 { runTime, value }
    const result = await serverMonitorTaskApi.run(task.id)
    ElMessage.success(`执行成功: ${result.value}`)
    loadServerTasks()
  } catch (e) {
    // 失败时拦截器已显示错误消息
    console.error('执行失败', e)
  }
  finally { task.running = false }
}

const deleteTask = async (task) => {
  await ElMessageBox.confirm('确定删除该监控任务吗？', '确认')
  await serverMonitorTaskApi.delete(task.id)
  ElMessage.success('删除成功')
  loadServerTasks()
}

const saveServer = async () => {
  try {
    // 构建请求数据，密码需要加密传输
    const data = { ...editingServer }
    if (data.password) {
      data.password = encryptPassword(data.password)
    }
    if (editingServer.id) {
      await serverAssetApi.update(data)
    } else {
      await serverAssetApi.create(data)
    }
    ElMessage.success('保存成功')
    addDialogVisible.value = false
    loadData()
  } catch (e) { ElMessage.error('保存失败') }
}

const batchAdd = async () => {
  try {
    // 构建请求数据，密码需要加密传输
    const data = { ...batchForm }
    if (data.password) {
      data.password = encryptPassword(data.password)
    }
    const result = await serverAssetApi.batchAdd(data)
    ElMessage.success(`成功添加 ${result.successCount}/${result.total} 台服务器`)
    if (result.failedIps && result.failedIps.length > 0) {
      ElMessage.warning(`失败: ${result.failedIps.join(', ')}`)
    }
    batchDialogVisible.value = false
    loadData()
  } catch (e) { ElMessage.error('批量添加失败') }
}

const deleteServer = async (server) => {
  await ElMessageBox.confirm('确定删除该服务器吗？', '确认')
  await serverAssetApi.delete(server.id)
  ElMessage.success('删除成功')
  loadData()
}

const testConnection = async (server) => {
  server.testing = true
  try {
    const result = await serverAssetApi.testConnection(server.id)
    if (result.connected) {
      ElMessage.success(`连接成功: ${result.systemInfo?.hostname || server.ip}`)
    } else {
      ElMessage.error('连接失败')
    }
    loadData()
  } catch (e) { ElMessage.error('测试失败') }
  finally { server.testing = false }
}

const saveGroup = async () => {
  try {
    if (editingGroup.id) {
      await serverGroupApi.update(editingGroup)
    } else {
      await serverGroupApi.create(editingGroup)
    }
    ElMessage.success('保存成功')
    groupDialogVisible.value = false
    loadData()
  } catch (e) { ElMessage.error('保存失败') }
}

const deleteGroup = async (group) => {
  await ElMessageBox.confirm('确定删除该分组吗？', '确认')
  await serverGroupApi.delete(group.id)
  ElMessage.success('删除成功')
  loadData()
}

// 格式化阈值规则
const formatThreshold = (thresholdJson) => {
  if (!thresholdJson) return '-'
  try {
    const t = typeof thresholdJson === 'string' ? JSON.parse(thresholdJson) : thresholdJson
    const opMap = { '>': '大于', '<': '小于', '>=': '大于等于', '<=': '小于等于', '=': '等于', '!=': '不等于' }
    return `${opMap[t.operator] || t.operator} ${t.value}`
  } catch { return '-' }
}

// 格式化 Cron 表达式
const formatCron = (cron) => {
  if (!cron) return '-'
  const patterns = {
    '0 * * * * ?': '每分钟',
    '0 */5 * * * ?': '每5分钟',
    '0 */10 * * * ?': '每10分钟',
    '0 */15 * * * ?': '每15分钟',
    '0 */30 * * * ?': '每30分钟',
    '0 0 * * * ?': '每小时',
    '0 0 0 * * ?': '每天',
  }
  return patterns[cron] || cron
}

// 切换任务启用状态
const toggleTaskActive = async (task) => {
  try {
    await serverMonitorTaskApi.updateById(task.id, { isActive: task.isActive })
    ElMessage.success(task.isActive ? '任务已启用' : '任务已禁用')
  } catch (e) {
    task.isActive = !task.isActive // 回滚
    ElMessage.error('操作失败')
  }
}

// 获取采集方式标签
const getCollectTypeLabel = (type) => {
  const map = { 'SSH_SCRIPT': 'SSH脚本', 'API': 'API接口', 'SQL': 'SQL查询' }
  return map[type] || type
}

// 获取渠道类型名称
const getChannelTypeName = (type) => {
  const map = { 'EMAIL': '邮件', 'DINGTALK': '钉钉', 'SMS': '短信', 'WEBHOOK': 'Webhook', 'ANNOUNCEMENT': '系统公告' }
  return map[type] || type
}

// 切换模板选中状态
const toggleTemplate = (id) => {
  const idx = selectedTemplates.value.indexOf(id)
  if (idx >= 0) {
    selectedTemplates.value.splice(idx, 1)
  } else {
    selectedTemplates.value.push(id)
  }
}

// 根据阈值判断值的样式
const getValueClass = (row) => {
  if (!row.lastRunValue || !row.thresholdRule) return ''
  try {
    const t = typeof row.thresholdRule === 'string' ? JSON.parse(row.thresholdRule) : row.thresholdRule
    const val = parseFloat(row.lastRunValue)
    const threshold = t.value
    let isAbnormal = false
    switch (t.operator) {
      case '>': isAbnormal = val > threshold; break
      case '<': isAbnormal = val < threshold; break
      case '>=': isAbnormal = val >= threshold; break
      case '<=': isAbnormal = val <= threshold; break
      case '=': isAbnormal = val === threshold; break
      case '!=': isAbnormal = val !== threshold; break
    }
    return isAbnormal ? 'value-abnormal' : 'value-normal'
  } catch { return '' }
}

// 根据监控名称返回图标类型
const getIconType = (row) => {
  const name = row.name || ''
  const template = row.templateName || ''
  const text = name + template

  // 基础监控
  if (text.includes('CPU')) return 'cpu'
  if (text.includes('内存') || text.includes('Memory')) return 'memory'
  if (text.includes('磁盘') || text.includes('Disk')) return 'disk'
  if (text.includes('网络') || text.includes('TCP')) return 'network'
  if (text.includes('负载') || text.includes('Load')) return 'load'

  // 组件监控
  if (text.includes('MySQL')) return 'mysql'
  if (text.includes('Redis')) return 'redis'
  if (text.includes('Nginx')) return 'nginx'
  if (text.includes('MongoDB')) return 'mongodb'
  if (text.includes('Kafka')) return 'kafka'
  if (text.includes('ES') || text.includes('Elasticsearch')) return 'elasticsearch'
  if (text.includes('Zookeeper')) return 'zookeeper'
  if (text.includes('RabbitMQ')) return 'rabbitmq'
  if (text.includes('MinIO')) return 'minio'
  if (text.includes('Hadoop')) return 'hadoop'
  if (text.includes('FTP')) return 'ftp'

  // 应用监控
  if (text.includes('HTTP')) return 'http'
  if (text.includes('日志') || text.includes('Log')) return 'log'

  return 'default'
}

onMounted(() => { loadData() })
</script>

<style scoped>
.server-asset {
  padding: 20px;
  min-height: calc(100vh - 80px);
  background: var(--theme-bg);
}

.section-header {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--theme-border);
}

.server-asset :deep(.el-tabs--border-card) {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
}

/* 任务名称单元格 */
.task-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-icon {
  font-size: 22px;
  line-height: 1;
  flex-shrink: 0;
}

.task-name {
  font-weight: 600;
  font-size: 14px;
}

/* 阈值文本 */
.threshold-text {
  font-size: 13px;
  color: var(--el-color-warning);
  font-weight: 500;
}

/* 值状态样式 */
.value-normal {
  color: var(--el-color-success);
  font-weight: 600;
}

.value-abnormal {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* 模板选择对话框 */
.category-hint {
  font-size: 13px;
  color: var(--theme-text-secondary);
  padding: 8px 0 16px 0;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  max-height: 400px;
  overflow-y: auto;
  padding: 4px;
}

.template-card {
  background: var(--el-bg-color);
  border: 2px solid var(--theme-border);
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-card:hover {
  border-color: var(--el-color-primary-light-3);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.template-card.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.card-header {
  margin-bottom: 10px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.card-body {
  padding-left: 24px;
}

.card-desc {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin-bottom: 10px;
  line-height: 1.4;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-item {
  display: flex;
  font-size: 12px;
}

.info-label {
  color: var(--theme-text-secondary);
  width: 65px;
  flex-shrink: 0;
}

.info-value {
  color: var(--theme-text);
}

.info-value.threshold {
  color: var(--el-color-warning);
  font-weight: 500;
}

/* 对话框底部 */
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.selected-count {
  color: var(--el-color-primary);
  font-weight: 500;
}

/* 参数配置样式 */
.params-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid var(--theme-border);
}

.param-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.param-name {
  min-width: 120px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border-radius: 4px;
  text-align: center;
  font-weight: 500;
  height: 24px;
  line-height: 24px;
  padding: 0 12px;
  box-sizing: border-box;
}
</style>
