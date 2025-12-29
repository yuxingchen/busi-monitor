<template>
  <div class="page-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-info">
        <h3 class="header-title">监控任务配置</h3>
        <p class="header-desc">配置SQL监控任务、执行周期和可视化规则</p>
      </div>
      <el-button type="primary" class="add-btn" @click="handleAdd">
        <el-icon>
          <Plus />
        </el-icon>新增任务
      </el-button>
    </div>

    <!-- Task Table -->
    <div class="table-card">
      <el-table :data="tableData" class="custom-table">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="name" label="任务名称" min-width="180" />
        <el-table-column prop="cronExpression" label="执行周期" width="180">
          <template #default="{ row }">
            <div class="cron-display">
              <span class="cron-readable">{{ cronToReadable(row.cronExpression) }}</span>
              <code class="cron-code-small">{{ row.cronExpression }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="resultType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.resultType === 'SCALAR' ? 'primary' : 'success'" size="small" effect="dark">
              {{ row.resultType === 'SCALAR' ? '数值' : '列表' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="90" align="center">
          <template #default="{ row }">
            <div class="status-badge" :class="{ active: row.isActive }">
              <span class="status-dot"></span>
              {{ row.isActive ? '运行' : '停用' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="300" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" class="btn-config" @click="handleEdit(row)">配置</el-button>
              <el-button size="small" class="btn-execute" @click="handleExecute(row)" :loading="row.executing">
                执行
              </el-button>
              <el-button size="small" :class="row.isActive ? 'btn-pause' : 'btn-start'" @click="toggleActive(row)">
                {{ row.isActive ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑任务' : '新增任务'" width="70%" top="5vh">
      <el-form :model="form" label-width="120px">
        <!-- 任务类型选择 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="任务类型">
              <el-radio-group v-model="chartConfig.sourceType">
                <el-radio label="SQL">常规SQL任务</el-radio>
                <el-radio label="WORKFLOW">工作流任务</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="任务名称">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="chartConfig.sourceType !== 'WORKFLOW'">
            <el-form-item label="数据源">
              <el-select v-model="form.datasourceId" placeholder="选择数据源" style="width: 100%">
                <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-else>
            <el-form-item label="选择工作流">
              <el-select v-model="chartConfig.workflowId" placeholder="选择工作流" style="width: 100%">
                <el-option v-for="wf in workflows" :key="wf.id" :label="wf.name" :value="wf.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="执行周期">
              <div class="cron-config">
                <el-select v-model="cronPreset" @change="applyCronPreset" placeholder="快速选择" style="width: 140px;">
                  <el-option label="每30秒" value="0/30 * * * * ?" />
                  <el-option label="每分钟" value="0 * * * * ?" />
                  <el-option label="每5分钟" value="0 0/5 * * * ?" />
                  <el-option label="每10分钟" value="0 0/10 * * * ?" />
                  <el-option label="每30分钟" value="0 0/30 * * * ?" />
                  <el-option label="每小时" value="0 0 * * * ?" />
                  <el-option label="每天0点" value="0 0 0 * * ?" />
                  <el-option label="每天8点" value="0 0 8 * * ?" />
                  <el-option label="自定义" value="custom" />
                </el-select>
                <el-input v-model="form.cronExpression" placeholder="Cron 表达式" style="width: 160px; margin-left: 10px;"
                  @input="cronPreset = 'custom'" />
                <div class="cron-preview-inline" v-if="form.cronExpression">
                  <el-icon>
                    <Clock />
                  </el-icon>
                  <span>{{ cronToReadable(form.cronExpression) }}</span>
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结果存储">
              <el-switch v-model="form.isStoreData" :active-value="1" :inactive-value="0" active-text="开启详细数据落库" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 数据存储配置（存储模式或工作流模式时显示） -->
        <el-row :gutter="20" v-if="form.isStoreData === 1 || chartConfig.sourceType === 'WORKFLOW'">
          <el-col :span="8">
            <el-form-item label="结果表名">
              <el-input v-model="chartConfig.outputTable" :placeholder="`monitor_task_result_${form.id || 'N'}`"
                :disabled="chartConfig.sourceType === 'WORKFLOW'">
                <template #prepend>表名</template>
              </el-input>
              <div class="form-tip-block" v-if="chartConfig.sourceType === 'WORKFLOW'">
                <el-tag size="small" type="success">已关联工作流输出表</el-tag>
              </div>
              <div class="form-tip-block" v-else>留空则使用默认名称</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="查询限制">
              <el-input-number v-model="chartConfig.queryLimit" :min="0" :max="100000" placeholder="不限制"
                style="width: 100%" />
              <div class="form-tip-block">0或空表示不限制</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="索引字段">
              <el-select v-model="chartConfig.indexFields" multiple placeholder="选择需要建立索引的字段" style="width: 100%">
                <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
              </el-select>
              <div class="form-tip-block">选择经常用于查询的字段可提升性能</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="结果类型">
          <el-radio-group v-model="form.resultType">
            <el-radio label="SCALAR">数值型 (仅取第一行第一列)</el-radio>
            <el-radio label="DATASET">列表型 (完整结果集)</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="SQL 脚本">
          <el-input v-model="form.sqlScript" type="textarea" :rows="4"
            placeholder="SELECT count(*) FROM orders WHERE create_time > '${lastRunTime}'"
            style="font-family: monospace;" />
          <div class="sql-help">
            <el-button type="primary" size="small" @click="testSql">试运行 SQL</el-button>
            <el-popover placement="top-start" :width="400" trigger="hover">
              <template #reference>
                <el-button size="small" text type="info">
                  <el-icon>
                    <QuestionFilled />
                  </el-icon> 占位符说明
                </el-button>
              </template>
              <div class="placeholder-help">
                <p><strong>支持的动态占位符：</strong></p>
                <table class="help-table">
                  <tbody>
                    <tr>
                      <td><code>${now}</code></td>
                      <td>当前时间</td>
                    </tr>
                    <tr>
                      <td><code>${today}</code></td>
                      <td>今天日期</td>
                    </tr>
                    <tr>
                      <td><code>${yesterday}</code></td>
                      <td>昨天日期</td>
                    </tr>
                    <tr>
                      <td><code>${lastRunTime}</code></td>
                      <td>上次执行时间</td>
                    </tr>
                    <tr>
                      <td><code>${todayStart}</code></td>
                      <td>今天 00:00:00</td>
                    </tr>
                    <tr>
                      <td><code>${todayEnd}</code></td>
                      <td>今天 23:59:59</td>
                    </tr>
                  </tbody>
                </table>
                <p style="margin-top: 8px; color: var(--theme-text-secondary); font-size: 12px;">
                  示例：<code>WHERE create_time > '${lastRunTime}'</code>
                </p>
              </div>
            </el-popover>
          </div>
        </el-form-item>

        <!-- Test Result Preview Area (仅试运行后显示) -->
        <div v-if="testResult" class="result-section">
          <div class="result-header">
            <strong class="result-title">SQL 返回预览 (Top 1)</strong>
            <el-tag size="small" effect="dark">共 {{ testResult.length }} 条</el-tag>
          </div>
          <el-table :data="[testResult[0]]" size="small" class="result-table">
            <el-table-column v-for="key in resultFields" :key="key" :prop="key" :label="key" min-width="120" />
          </el-table>
        </div>

        <!-- 高级配置区域 (编辑已保存任务或试运行后显示) -->
        <div v-if="form.id || testResult" class="result-section">
          <el-divider content-position="left">高级配置</el-divider>

          <el-tabs type="border-card" class="config-tabs">
            <!-- 1. 字段映射 -->
            <el-tab-pane label="字段映射 (中文别名)">
              <el-alert title="为 SQL 字段设置中文别名，将在仪表盘中显示" type="info" :closable="false" style="margin-bottom: 10px;" />
              <el-table :data="fieldMappingList" size="small" border>
                <el-table-column prop="field" label="原始字段 (SQL Key)" width="180" />
                <el-table-column label="显示名称 (中文别名)">
                  <template #default="scope">
                    <el-input v-model="chartConfig.fieldAlias[scope.row.field]" placeholder="请输入中文名" size="small" />
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 2. 可视化图表 -->
            <el-tab-pane label="图表设置" v-if="form.resultType === 'DATASET'">
              <el-form-item label="图表类型">
                <el-radio-group v-model="chartConfig.type">
                  <el-radio-button label="table">明细表格</el-radio-button>
                  <el-radio-button label="bar">柱状图</el-radio-button>
                  <el-radio-button label="line">折线图</el-radio-button>
                  <el-radio-button label="pie">饼图</el-radio-button>
                  <el-radio-button label="pivot">二维透视表</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <!-- 二维透视表配置 -->
              <template v-if="chartConfig.type === 'pivot'">
                <el-divider content-position="left">透视表配置</el-divider>
                <el-form-item label="主分组（行）">
                  <el-select v-model="chartConfig.pivotRowField" placeholder="选择主分组字段"
                    @change="onPivotFieldChange('pivotRowField', $event)">
                    <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                  </el-select>
                  <span class="form-tip">如：产品名称</span>
                </el-form-item>
                <el-form-item label="次分组（列）">
                  <el-select v-model="chartConfig.pivotColField" placeholder="选择次分组字段"
                    @change="onPivotFieldChange('pivotColField', $event)">
                    <el-option label="执行时间" value="execution_time" />
                    <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                  </el-select>
                  <span class="form-tip">如：时间段、区域</span>
                </el-form-item>
                <el-form-item label="数值字段">
                  <el-select v-model="chartConfig.pivotValueField" placeholder="选择数值字段"
                    @change="onPivotFieldChange('pivotValueField', $event)">
                    <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                  </el-select>
                </el-form-item>
                <el-form-item label="聚合方式">
                  <el-radio-group v-model="chartConfig.pivotAggMethod">
                    <el-radio-button label="SUM">求和</el-radio-button>
                    <el-radio-button label="COUNT">计数</el-radio-button>
                    <el-radio-button label="AVG">平均</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </template>

              <!-- 普通图表配置 -->
              <div v-else-if="chartConfig.type !== 'table'">
                <el-form-item label="类别轴 (X轴)">
                  <el-select v-model="chartConfig.xAxis">
                    <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                  </el-select>
                </el-form-item>
                <el-form-item label="数值轴 (Y轴)">
                  <el-select v-model="chartConfig.yAxis">
                    <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                  </el-select>
                </el-form-item>
              </div>
              <div v-else>
                <el-form-item label="显示列">
                  <el-checkbox-group v-model="chartConfig.columns">
                    <el-checkbox v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                  </el-checkbox-group>
                </el-form-item>
              </div>

              <!-- 历史数据分组配置 (非透视表时可用) -->
              <template v-if="chartConfig.type !== 'pivot'">
                <el-divider content-position="left">历史数据分组展示</el-divider>
                <el-form-item label="启用分组">
                  <el-switch v-model="chartConfig.enableGrouping" />
                </el-form-item>
                <template v-if="chartConfig.enableGrouping">
                  <el-form-item label="分组字段">
                    <el-select v-model="chartConfig.groupByField" placeholder="选择分组字段">
                      <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                    </el-select>
                    <span class="form-tip">按此字段分组，时间作为X轴</span>
                  </el-form-item>
                  <el-form-item label="数值字段">
                    <el-select v-model="chartConfig.valueField" placeholder="选择数值字段">
                      <el-option v-for="field in resultFields" :key="field" :label="fieldAlias(field)" :value="field" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="聚合方式">
                    <el-radio-group v-model="chartConfig.aggregateMethod">
                      <el-radio-button label="SUM">求和</el-radio-button>
                      <el-radio-button label="COUNT">计数</el-radio-button>
                      <el-radio-button label="AVG">平均</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="展示类型">
                    <el-radio-group v-model="chartConfig.displayType">
                      <el-radio-button label="line">折线图</el-radio-button>
                      <el-radio-button label="bar">柱状图</el-radio-button>
                      <el-radio-button label="table">数据表</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                </template>
              </template>
            </el-tab-pane>

            <!-- 3. 报警规则 -->
            <el-tab-pane label="报警规则">
              <div style="display: flex; align-items: center; gap: 10px;">
                <span>当</span>
                <el-select v-model="alarmRule.field" placeholder="选择字段" style="width: 150px"
                  v-if="form.resultType === 'DATASET'">
                  <el-option v-for="field in resultFields" :key="field" :label="field" :value="field" />
                </el-select>
                <span v-else>查询结果</span>

                <el-select v-model="alarmRule.operator" placeholder="操作符" style="width: 100px">
                  <el-option label="大于 (>)" value=">" />
                  <el-option label="大于等于 (>=)" value=">=" />
                  <el-option label="小于 (<)" value="<" />
                  <el-option label="小于等于 (<=)" value="<=" />
                  <el-option label="等于 (=)" value="=" />
                </el-select>

                <el-input-number v-model="alarmRule.value" placeholder="阈值" style="width: 150px" />
                <span>时触发报警</span>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存配置</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed, watch } from 'vue'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, QuestionFilled, Clock } from '@element-plus/icons-vue'

const tableData = ref([])
const dataSources = ref([])
const workflows = ref([])  // 工作流列表
const dialogVisible = ref(false)
const testResult = ref(null)
const cronPreset = ref('')

// Cron 表达式转可读描述
const cronToReadable = (cron) => {
  if (!cron) return ''
  const parts = cron.trim().split(/\s+/)
  if (parts.length < 6) return cron

  const [sec, min, hour, day, month, week] = parts

  // 常见模式匹配
  if (sec.startsWith('0/') && min === '*') return `每${sec.slice(2)}秒`
  if (sec === '0' && min.startsWith('0/') && hour === '*') return `每${min.slice(2)}分钟`
  if (sec === '0' && min === '*' && hour === '*') return '每分钟'
  if (sec === '0' && min === '0' && hour.startsWith('0/')) return `每${hour.slice(2)}小时`
  if (sec === '0' && min === '0' && hour === '*') return '每小时整点'
  if (sec === '0' && min === '0' && /^\d+$/.test(hour) && day === '*') return `每天 ${hour}:00`
  if (sec === '0' && /^\d+$/.test(min) && /^\d+$/.test(hour) && day === '*') return `每天 ${hour}:${min.padStart(2, '0')}`

  // 更复杂的模式
  if (day !== '*' && day !== '?') return `每月${day}日执行`
  if (week !== '*' && week !== '?') return `每周${week}执行`

  return cron // 无法解析则返回原始表达式
}

// 应用预设 Cron
const applyCronPreset = (value) => {
  if (value !== 'custom') {
    form.cronExpression = value
  }
}

const form = reactive({
  id: null,
  name: '',
  datasourceId: null,
  sqlScript: '',
  cronExpression: '',
  isActive: 1,
  resultType: 'SCALAR',
  isStoreData: 0,
  alarmThresholdRule: '',
  chartConfig: ''
})

const chartConfig = reactive({
  type: 'table',
  xAxis: '',
  yAxis: '',
  columns: [],
  fieldAlias: {}, // Map: key -> Chinese Name
  // 任务来源配置
  sourceType: 'SQL',  // SQL=常规任务, WORKFLOW=工作流任务
  workflowId: null,   // 工作流ID（WORKFLOW类型时使用）
  queryLimit: 0,      // 查询限制（0=不限制）
  // 分组配置
  enableGrouping: false,
  groupByField: '',
  valueField: '',
  aggregateMethod: 'SUM',
  displayType: 'line',
  // 二维透视表配置
  pivotRowField: '',
  pivotColField: 'execution_time',
  pivotValueField: '',
  pivotAggMethod: 'SUM',
  // 数据存储配置
  outputTable: '',
  indexFields: []
})

const alarmRule = reactive({
  field: '',
  operator: '>',
  value: 0
})

const loadData = async () => {
  try {
    const [tasks, dss, wfs] = await Promise.all([
      request.get('/task'),
      request.get('/datasource'),
      request.get('/workflows')  // 加载工作流列表
    ])
    tableData.value = tasks
    dataSources.value = dss
    workflows.value = wfs || []
  } catch (e) { }
}

// 监听工作流选择变化，自动填充表名和SQL
watch(() => chartConfig.workflowId, (newId) => {
  if (newId && chartConfig.sourceType === 'WORKFLOW') {
    const wf = workflows.value.find(w => w.id === newId)
    if (wf) {
      const tableName = wf.outputTable || `monitor_wf_result_${newId}`
      chartConfig.outputTable = tableName
      form.sqlScript = `SELECT * FROM ${tableName} LIMIT 1`
    }
  }
})

const resultFields = computed(() => {
  // 优先从测试结果获取字段
  if (testResult.value && testResult.value.length > 0) {
    return Object.keys(testResult.value[0])
  }
  // 如果没有测试结果，从已保存的 fieldAlias 中获取字段
  if (chartConfig.fieldAlias && Object.keys(chartConfig.fieldAlias).length > 0) {
    return Object.keys(chartConfig.fieldAlias).filter(k => k !== 'execution_time')
  }
  // 从已保存的透视表配置中获取字段
  const savedFields = new Set()
  if (chartConfig.pivotRowField) savedFields.add(chartConfig.pivotRowField)
  if (chartConfig.pivotValueField) savedFields.add(chartConfig.pivotValueField)
  if (chartConfig.xAxis) savedFields.add(chartConfig.xAxis)
  if (chartConfig.yAxis) savedFields.add(chartConfig.yAxis)
  if (chartConfig.groupByField) savedFields.add(chartConfig.groupByField)
  if (chartConfig.valueField) savedFields.add(chartConfig.valueField)
  return Array.from(savedFields)
})

// Helper for table mapping
const fieldMappingList = computed(() => {
  return resultFields.value.map(f => ({ field: f }))
})

const fieldAlias = (field) => {
  return (chartConfig.fieldAlias && chartConfig.fieldAlias[field])
    ? `${chartConfig.fieldAlias[field]} (${field})`
    : field
}

// 透视表字段选择时自动设置别名
const onPivotFieldChange = (fieldType, value) => {
  // 硬编码的特殊字段别名
  const specialFieldAlias = {
    'execution_time': '执行时间'
  }

  // 如果是特殊字段，自动设置别名
  if (specialFieldAlias[value]) {
    chartConfig.fieldAlias[value] = specialFieldAlias[value]
  }
  // 否则，如果字段已经有别名（从 resultFields 选择的），保持不变
  // 如果没有别名，不做处理（让用户在字段映射中手动设置）
}

const handleAdd = () => {
  Object.assign(form, {
    id: null, name: '', datasourceId: null, sqlScript: '', cronExpression: '0/30 * * * * ?',
    isActive: 1, resultType: 'SCALAR', isStoreData: 0, chartConfig: '', alarmThresholdRule: ''
  })

  // Reset refined configs
  Object.assign(chartConfig, {
    type: 'bar', xAxis: '', yAxis: '', columns: [], fieldAlias: {},
    outputTable: '', indexFields: []
  })
  Object.assign(alarmRule, { field: '', operator: '>', value: 0 })

  testResult.value = null
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)

  // Parse Chart Config
  if (row.chartConfig) {
    try {
      const cfg = JSON.parse(row.chartConfig)
      // 合并所有配置字段
      chartConfig.type = cfg.type || 'table'
      chartConfig.xAxis = cfg.xAxis || ''
      chartConfig.yAxis = cfg.yAxis || ''
      chartConfig.columns = cfg.columns || []
      chartConfig.fieldAlias = cfg.fieldAlias || {}
      // 透视表配置
      chartConfig.pivotRowField = cfg.pivotRowField || ''
      chartConfig.pivotColField = cfg.pivotColField || 'execution_time'
      chartConfig.pivotValueField = cfg.pivotValueField || ''
      chartConfig.pivotAggMethod = cfg.pivotAggMethod || 'SUM'
      // 分组配置
      chartConfig.enableGrouping = cfg.enableGrouping || false
      chartConfig.groupByField = cfg.groupByField || ''
      chartConfig.valueField = cfg.valueField || ''
      chartConfig.aggregateMethod = cfg.aggregateMethod || 'SUM'
      chartConfig.displayType = cfg.displayType || 'line'
      // 数据存储配置
      chartConfig.outputTable = cfg.outputTable || ''
      chartConfig.indexFields = cfg.indexFields || []
      // 工作流配置回填
      chartConfig.sourceType = cfg.sourceType || 'SQL'
      chartConfig.workflowId = cfg.workflowId || null
      chartConfig.queryLimit = cfg.queryLimit || 0
    } catch (e) { }
  } else {
    Object.assign(chartConfig, {
      type: 'table', xAxis: '', yAxis: '', columns: [], fieldAlias: {},
      pivotRowField: '', pivotColField: 'execution_time', pivotValueField: '', pivotAggMethod: 'SUM',
      enableGrouping: false, groupByField: '', valueField: '', aggregateMethod: 'SUM', displayType: 'line',
      outputTable: '', indexFields: []
    })
  }

  // Parse Alarm Rule
  if (row.alarmThresholdRule) {
    try {
      const rule = JSON.parse(row.alarmThresholdRule)
      Object.assign(alarmRule, rule)
    } catch (e) { }
  } else {
    Object.assign(alarmRule, { field: '', operator: '>', value: 0 })
  }

  testResult.value = null
  dialogVisible.value = true

  // If editing, user might want to see fields immediately? 
  // We can't unless we re-run SQL. Just keep it empty until they click 'Test Run'
}

const toggleActive = async (row) => {
  row.isActive = row.isActive ? 0 : 1
  await request.put('/task', row)
  ElMessage.success('状态更新成功')
}

const handleExecute = async (row) => {
  row.executing = true
  try {
    const res = await request.post(`/task/${row.id}/execute`)
    if (res.success) {
      ElMessage.success(res.message || '任务执行成功')
    } else {
      ElMessage.error(res.message || '任务执行失败')
    }
  } catch (e) {
    ElMessage.error('执行失败: ' + (e.message || '未知错误'))
  } finally {
    row.executing = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确认删除?', 'Warning', { type: 'warning' })
    .then(async () => {
      await request.delete(`/task/${row.id}`)
      loadData()
    })
}

const testSql = async () => {
  if (!form.datasourceId || !form.sqlScript) return
  try {
    const res = await request.post('/task/test-sql', {
      datasourceId: form.datasourceId,
      sql: form.sqlScript
    })
    if (res.error) {
      ElMessage.error(res.error)
    } else {
      if (Array.isArray(res)) {
        testResult.value = res
      } else {
        testResult.value = [{ value: res }]
      }
      ElMessage.success('SQL 执行成功')
    }
  } catch (e) { }
}

const handleSave = async () => {
  form.chartConfig = JSON.stringify(chartConfig)

  // Minimal alarm rule save
  if (alarmRule.operator) {
    form.alarmThresholdRule = JSON.stringify(alarmRule)
  }

  if (form.id) {
    await request.put('/task', form)
  } else {
    await request.post('/task', form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container {
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.header-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.header-desc {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin: 0;
}

.add-btn {
  background: linear-gradient(135deg, var(--theme-accent), var(--el-color-primary)) !important;
  border: none !important;
  padding: 10px 20px !important;
}

.table-card {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 20px;
  transition: background 0.3s ease;
}

.custom-table {
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
  --el-table-row-hover-bg-color: var(--el-fill-color-light) !important;
  --el-table-border-color: var(--el-border-color-lighter) !important;
  --el-table-text-color: var(--el-text-color-regular) !important;
}

:deep(.el-table) {
  background: transparent !important;
}

:deep(.el-table th.el-table__cell) {
  background: var(--el-fill-color) !important;
  color: var(--theme-accent) !important;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-light) !important;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid var(--el-border-color-lighter) !important;
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

.cron-code {
  font-family: 'Roboto Mono', monospace;
  font-size: 12px;
  color: var(--theme-text-secondary);
  background: var(--el-fill-color-light);
  padding: 3px 8px;
  border-radius: 4px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  background: rgba(255, 193, 7, 0.15);
  color: #ffc107;
}

.status-badge.active {
  background: rgba(0, 255, 136, 0.15);
  color: #00ff88;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.action-btns {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.btn-config {
  background: var(--el-fill-color-light) !important;
  border: 1px solid var(--el-border-color) !important;
  color: var(--theme-accent) !important;
}

.btn-start {
  background: rgba(0, 255, 136, 0.1) !important;
  border: 1px solid rgba(0, 255, 136, 0.3) !important;
  color: #00ff88 !important;
}

.btn-pause {
  background: rgba(255, 193, 7, 0.1) !important;
  border: 1px solid rgba(255, 193, 7, 0.3) !important;
  color: #ffc107 !important;
}

.btn-execute {
  background: rgba(0, 168, 255, 0.1) !important;
  border: 1px solid rgba(0, 168, 255, 0.3) !important;
  color: #00a8ff !important;
}

.btn-delete {
  background: rgba(255, 71, 87, 0.1) !important;
  border: 1px solid rgba(255, 71, 87, 0.3) !important;
  color: #ff4757 !important;
}

:deep(.el-dialog) {
  background: var(--el-bg-color-overlay) !important;
  border: 1px solid var(--theme-border) !important;
  border-radius: 12px !important;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 20px;
}

:deep(.el-dialog__title) {
  color: var(--theme-text) !important;
  font-weight: 600;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-form-item__label) {
  color: var(--theme-text-secondary) !important;
}

:deep(.el-input__wrapper) {
  background: var(--el-fill-color-blank) !important;
  border: 1px solid var(--el-border-color) !important;
  box-shadow: none !important;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--el-border-color-light) !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--theme-accent) !important;
}

:deep(.el-input__inner) {
  color: var(--theme-text) !important;
}

:deep(.el-textarea__inner) {
  background: var(--el-fill-color-blank) !important;
  border: 1px solid var(--el-border-color) !important;
  color: var(--theme-text) !important;
}

:deep(.el-select .el-input__wrapper) {
  background: var(--el-fill-color-blank) !important;
}

:deep(.el-tabs--border-card) {
  background: var(--el-fill-color-light) !important;
  border: 1px solid var(--el-border-color-light) !important;
}

:deep(.el-tabs__header) {
  background: var(--el-fill-color) !important;
  border-bottom: 1px solid var(--el-border-color-light) !important;
}

:deep(.el-tabs__item) {
  color: var(--theme-text-secondary) !important;
}

:deep(.el-tabs__item.is-active) {
  color: var(--theme-accent) !important;
  background: transparent !important;
}

/* SQL Result Section Theme */
.result-section {
  background: var(--el-fill-color-light);
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  transition: background 0.3s ease;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.result-title {
  color: var(--theme-accent);
  font-size: 14px;
}

.result-table {
  margin-bottom: 20px;
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
  --el-table-header-bg-color: var(--el-fill-color) !important;
  --el-table-row-hover-bg-color: var(--el-fill-color-light) !important;
  --el-table-border-color: var(--el-border-color-light) !important;
  --el-table-text-color: var(--el-text-color-regular) !important;
  --el-table-header-text-color: var(--theme-accent) !important;
}

.config-tabs {
  background: var(--el-fill-color-lighter) !important;
  border: 1px solid var(--el-border-color-light) !important;
  border-radius: 8px !important;
}

:deep(.config-tabs .el-tabs__header) {
  background: var(--el-fill-color) !important;
  border-bottom: 1px solid var(--el-border-color-light) !important;
  margin: 0 !important;
}

:deep(.config-tabs .el-tabs__content) {
  padding: 15px;
}

:deep(.el-alert--info) {
  background: var(--el-color-primary-light-9) !important;
  border: 1px solid var(--el-color-primary-light-7) !important;
}

:deep(.el-alert__title) {
  color: var(--theme-text-secondary) !important;
}

:deep(.el-divider__text) {
  background: transparent !important;
  color: var(--theme-text-secondary) !important;
}

:deep(.el-divider) {
  border-color: var(--el-border-color-light) !important;
}

/* SQL Help Section */
.sql-help {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.placeholder-help p {
  margin: 0 0 8px 0;
  color: var(--theme-text);
}

.help-table {
  width: 100%;
  border-collapse: collapse;
}

.help-table td {
  padding: 4px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.help-table td:first-child {
  width: 120px;
}

.help-table code {
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Roboto Mono', monospace;
  color: var(--theme-accent);
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* Cron 可读性优化样式 */
.cron-display {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cron-readable {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text);
}

.cron-code-small {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  font-family: 'Roboto Mono', monospace;
}

.cron-config {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.cron-preview-inline {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--theme-accent);
  white-space: nowrap;
}

.cron-preview-inline .el-icon {
  font-size: 14px;
}

.form-tip-block {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
