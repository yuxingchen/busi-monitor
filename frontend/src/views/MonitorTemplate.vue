<template>
  <div class="monitor-template">
    <el-tabs v-model="activeCategory" type="border-card">
      <el-tab-pane label="基础监控" name="BASIC">
        <div class="template-grid">
          <div v-for="t in basicTemplates" :key="t.id" class="template-card">
            <MonitorIcon :type="getIconType(t)" :size="36" class="template-icon-svg" />
            <div class="template-info">
              <div class="template-name">{{ t.name }}</div>
              <div class="template-desc">{{ t.description }}</div>
              <div class="template-threshold">
                <span class="label">阈值:</span>
                <span class="value">{{ formatThreshold(t.defaultThreshold) }}</span>
              </div>
            </div>
            <el-button type="primary" size="small" @click="useTemplate(t)">使用</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="组件监控" name="COMPONENT">
        <div class="template-grid">
          <div v-for="t in componentTemplates" :key="t.id" class="template-card">
            <MonitorIcon :type="getIconType(t)" :size="36" class="template-icon-svg" />
            <div class="template-info">
              <div class="template-name">{{ t.name }}</div>
              <div class="template-desc">{{ t.description }}</div>
              <div class="template-threshold">
                <span class="label">阈值:</span>
                <span class="value">{{ formatThreshold(t.defaultThreshold) }}</span>
              </div>
            </div>
            <el-button type="primary" size="small" @click="useTemplate(t)">使用</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="应用监控" name="APPLICATION">
        <div class="template-grid">
          <div v-for="t in appTemplates" :key="t.id" class="template-card">
            <MonitorIcon :type="getIconType(t)" :size="36" class="template-icon-svg" />
            <div class="template-info">
              <div class="template-name">{{ t.name }}</div>
              <div class="template-desc">{{ t.description }}</div>
              <div class="template-threshold">
                <span class="label">阈值:</span>
                <span class="value">{{ formatThreshold(t.defaultThreshold) }}</span>
              </div>
            </div>
            <el-button type="primary" size="small" @click="useTemplate(t)">使用</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 使用模板对话框 -->
    <el-dialog v-model="useDialogVisible" :title="'配置: ' + selectedTemplate.name" width="600px">
      <el-form label-width="100px">
        <el-form-item label="选择服务器" required>
          <el-select v-model="taskConfig.serverIds" multiple placeholder="选择目标服务器">
            <el-option v-for="s in servers" :key="s.id" :label="`${s.name} (${s.ip})`" :value="s.id" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">监控参数</el-divider>

        <el-form-item label="采集脚本">
          <el-input v-model="taskConfig.collectScript" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item label="阈值">
          <el-input-number v-model="taskConfig.thresholdValue" />
        </el-form-item>

        <el-form-item label="执行频率">
          <el-select v-model="taskConfig.cronExpression">
            <el-option label="每1分钟" value="0 */1 * * * ?" />
            <el-option label="每5分钟" value="0 */5 * * * ?" />
            <el-option label="每10分钟" value="0 */10 * * * ?" />
            <el-option label="每30分钟" value="0 */30 * * * ?" />
            <el-option label="每小时" value="0 0 * * * ?" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">告警配置</el-divider>

        <el-form-item label="告警模板">
          <el-select v-model="taskConfig.alarmTemplateId" placeholder="选择告警模板" clearable style="width: 100%;">
            <el-option v-for="at in alarmTemplates" :key="at.id" :label="at.name" :value="at.id">
              <el-tooltip :content="at.content" placement="right" :show-after="300">
                <span style="display: block; width: 100%;">{{ at.name }}</span>
              </el-tooltip>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="告警通道">
          <el-checkbox-group v-model="taskConfig.channelIds">
            <el-checkbox v-for="c in channels" :key="c.id" :value="c.id">{{ c.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="useDialogVisible = false">取消</el-button>
        <el-button type="success" @click="testScript">测试脚本</el-button>
        <el-button type="primary" @click="createTask">创建任务</el-button>
      </template>
    </el-dialog>

    <!-- 测试结果对话框 -->
    <el-dialog v-model="testResultVisible" title="测试结果" width="500px">
      <pre class="test-output">{{ testOutput }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import MonitorIcon from '../components/MonitorIcon.vue'

const activeCategory = ref('BASIC')
const templates = ref([])
const servers = ref([])
const channels = ref([])
const alarmTemplates = ref([])

const useDialogVisible = ref(false)
const testResultVisible = ref(false)
const testOutput = ref('')

const selectedTemplate = ref({})
const taskConfig = reactive({
  serverIds: [],
  collectScript: '',
  thresholdValue: 50,
  cronExpression: '0 */5 * * * ?',
  alarmTemplateId: null,
  channelIds: []
})

const basicTemplates = computed(() => templates.value.filter(t => t.category === 'BASIC'))
const componentTemplates = computed(() => templates.value.filter(t => t.category === 'COMPONENT'))
const appTemplates = computed(() => templates.value.filter(t => t.category === 'APPLICATION'))

const loadData = async () => {
  try {
    const [t, s, c, at] = await Promise.all([
      request.get('/monitor-template'),
      request.get('/server-asset'),
      request.get('/alarm/channel/active'),
      request.get('/alarm/template')
    ])
    templates.value = t
    servers.value = s
    channels.value = c
    alarmTemplates.value = at
  } catch (e) { console.error(e) }
}

const useTemplate = (template) => {
  selectedTemplate.value = template

  // 解析默认阈值
  let threshold = 50
  try {
    const th = JSON.parse(template.defaultThreshold || '{}')
    threshold = th.value || 50
  } catch (e) { }

  Object.assign(taskConfig, {
    serverIds: [],
    collectScript: template.collectScript || '',
    thresholdValue: threshold,
    cronExpression: template.defaultCron || '0 */5 * * * ?',
    alarmTemplateId: template.alarmTemplateId || null,
    channelIds: []
  })

  useDialogVisible.value = true
}

const testScript = async () => {
  if (taskConfig.serverIds.length === 0) {
    ElMessage.warning('请选择服务器')
    return
  }

  try {
    const serverId = taskConfig.serverIds[0]
    const result = await request.post(`/server-asset/${serverId}/execute`, {
      script: taskConfig.collectScript
    })

    if (result.status === 'success') {
      testOutput.value = result.output
      testResultVisible.value = true
      ElMessage.success('脚本执行成功')
    } else {
      ElMessage.error(result.message)
    }
  } catch (e) {
    ElMessage.error('测试失败')
  }
}

const createTask = async () => {
  ElMessage.info('任务创建功能开发中...')
  useDialogVisible.value = false
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

// 根据模板名称返回图标类型
const getIconType = (t) => {
  const name = t.name || ''

  // 基础监控
  if (name.includes('CPU')) return 'cpu'
  if (name.includes('内存') || name.includes('Memory')) return 'memory'
  if (name.includes('磁盘') || name.includes('Disk')) return 'disk'
  if (name.includes('网络') || name.includes('TCP')) return 'network'
  if (name.includes('负载') || name.includes('Load')) return 'load'

  // 组件监控
  if (name.includes('MySQL')) return 'mysql'
  if (name.includes('Redis')) return 'redis'
  if (name.includes('Nginx')) return 'nginx'
  if (name.includes('MongoDB')) return 'mongodb'
  if (name.includes('Kafka')) return 'kafka'
  if (name.includes('ES') || name.includes('Elasticsearch')) return 'elasticsearch'
  if (name.includes('Zookeeper')) return 'zookeeper'
  if (name.includes('RabbitMQ')) return 'rabbitmq'
  if (name.includes('MinIO')) return 'minio'
  if (name.includes('Hadoop')) return 'hadoop'
  if (name.includes('FTP')) return 'ftp'

  // 应用监控
  if (name.includes('HTTP')) return 'http'
  if (name.includes('日志') || name.includes('Log')) return 'log'

  return 'default'
}

onMounted(() => { loadData() })
</script>

<style scoped>
.monitor-template {
  padding: 20px;
  min-height: calc(100vh - 80px);
  background: var(--theme-bg);
}

.monitor-template :deep(.el-tabs--border-card) {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 10px;
}

.template-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--el-fill-color);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.template-card:hover {
  border-color: var(--theme-accent);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.template-icon {
  font-size: 32px;
}

.template-info {
  flex: 1;
}

.template-name {
  font-weight: 600;
  color: var(--theme-text);
  margin-bottom: 4px;
}

.template-desc {
  font-size: 12px;
  color: var(--theme-text-secondary);
  margin-bottom: 6px;
}

.template-threshold {
  font-size: 12px;
  display: flex;
  gap: 4px;
}

.template-threshold .label {
  color: var(--theme-text-secondary);
}

.template-threshold .value {
  color: var(--el-color-warning);
  font-weight: 500;
}

.test-output {
  background: var(--el-fill-color);
  padding: 15px;
  border-radius: 8px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
  color: var(--theme-text);
}
</style>
