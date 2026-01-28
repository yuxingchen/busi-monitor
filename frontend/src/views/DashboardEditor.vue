<template>
  <div class="dashboard-editor">
    <!-- Header -->
    <div class="editor-header">
      <div class="header-left">
        <el-select v-model="currentDashboardId" placeholder="选择大屏" @change="loadDashboard" style="width: 200px">
          <el-option v-for="d in dashboardList" :key="d.id" :label="d.name" :value="d.id">
            <span>{{ d.name }}</span>
            <el-tag v-if="d.isDefault" size="small" type="success" style="margin-left: 8px">默认</el-tag>
          </el-option>
        </el-select>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon>
            <Plus />
          </el-icon>新建大屏
        </el-button>
      </div>
      <div class="header-center">
        <h3>{{ currentDashboard?.name || '大屏编辑器' }}</h3>
        <el-tag v-if="hasChanges" type="warning">未保存</el-tag>
      </div>
      <div class="header-right">
        <el-button @click="togglePreview">
          <el-icon>
            <View />
          </el-icon>{{ isPreview ? '编辑' : '预览' }}
        </el-button>
        <el-button type="success" @click="saveLayout" :loading="saving">
          <el-icon>
            <Check />
          </el-icon>保存布局
        </el-button>
        <el-button @click="showSettingsDialog">
          <el-icon>
            <Setting />
          </el-icon>设置
        </el-button>
      </div>
    </div>

    <!-- Main Content -->
    <div class="editor-main">
      <!-- Widget Library Sidebar -->
      <div class="widget-library" v-if="!isPreview">
        <div class="library-header">
          <span>组件库</span>
          <el-tooltip content="从下方拖拽组件到画布">
            <el-icon>
              <InfoFilled />
            </el-icon>
          </el-tooltip>
        </div>

        <div class="library-section">
          <div class="section-title">监控任务</div>
          <div class="widget-list">
            <div v-for="task in availableTasks" :key="task.id" class="widget-item" draggable="true"
              @dragstart="onDragStart($event, 'TASK', task)">
              <el-icon>
                <DataLine />
              </el-icon>
              <div class="widget-info">
                <span class="widget-name">{{ task.name }}</span>
                <span class="widget-type">{{ task.resultType === 'SCALAR' ? '数值' : '列表' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="library-section">
          <div class="section-title">装饰组件</div>
          <div class="widget-list">
            <div class="widget-item" draggable="true" @dragstart="onDragStart($event, 'CLOCK', null)">
              <el-icon>
                <Clock />
              </el-icon>
              <span class="widget-name">时钟</span>
            </div>
            <div class="widget-item" draggable="true" @dragstart="onDragStart($event, 'TEXT', null)">
              <el-icon>
                <Document />
              </el-icon>
              <span class="widget-name">文本框</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Grid Canvas - 编辑模式 -->
      <div v-if="!isPreview" class="grid-canvas" @dragover.prevent @drop="onDrop">
        <grid-layout v-model:layout="layout" :col-num="currentDashboard?.gridCols || 24"
          :row-height="currentDashboard?.cellHeight || 60" :is-draggable="true" :is-resizable="true"
          :vertical-compact="false" :use-css-transforms="true"
          :margin="[currentDashboard?.widgetMargin || 10, currentDashboard?.widgetMargin || 10]"
          @layout-updated="onLayoutUpdated">
          <grid-item v-for="item in layout" :key="item.i" :x="item.x" :y="item.y" :w="item.w" :h="item.h" :i="item.i"
            class="grid-widget" :style="getWidgetStyle(item)">
            <div class="widget-content">
              <div class="widget-header">
                <span class="widget-title" :style="getTitleStyle(item)">{{ getWidgetTitle(item) }}</span>
                <div class="widget-actions">
                  <el-button size="small" text @click="openWidgetConfig(item)" title="样式配置">
                    <el-icon>
                      <Setting />
                    </el-icon>
                  </el-button>
                  <el-button size="small" text @click="editWidget(item)" title="编辑标题">
                    <el-icon>
                      <Edit />
                    </el-icon>
                  </el-button>
                  <el-button size="small" text type="danger" @click="removeWidget(item)" title="删除">
                    <el-icon>
                      <Delete />
                    </el-icon>
                  </el-button>
                </div>
              </div>
              <div class="widget-body">
                <!-- 任务组件占位 -->
                <div v-if="item.widgetType === 'TASK'" class="task-widget">
                  <span class="chart-placeholder-text">{{ item.title || item.sourceName }}</span>
                </div>
                <!-- 时钟组件 -->
                <div v-else-if="item.widgetType === 'CLOCK'" class="clock-widget">
                  <div class="clock-time">{{ currentTime }}</div>
                  <div class="clock-date">{{ currentDate }}</div>
                </div>
                <!-- 文本组件 -->
                <div v-else-if="item.widgetType === 'TEXT'" class="text-widget" @dblclick.stop="editTextWidget(item)">
                  <div class="text-content">{{ item.title || '双击编辑文本' }}</div>
                </div>
              </div>
            </div>
          </grid-item>
        </grid-layout>

        <!-- Empty State -->
        <div v-if="layout.length === 0" class="empty-canvas">
          <el-icon class="empty-icon">
            <Grid />
          </el-icon>
          <p>从左侧拖拽组件到此处</p>
        </div>
      </div>

      <!-- Preview Canvas - 预览模式 (完全独立的 DOM 结构) -->
      <div v-else class="preview-canvas">
        <div v-for="item in layout" :key="'preview-' + item.i" class="preview-widget"
          :style="getPreviewWidgetStyle(item)">
          <!-- SCALAR 任务 -->
          <template v-if="item.widgetType === 'TASK' && item.resultType === 'SCALAR'">
            <div class="scalar-display">
              <span class="scalar-value" :style="getContentStyle(item)">{{ getTaskValue(item) }}</span>
              <span class="scalar-label" :style="getTitleStyle(item)">{{ item.title || item.sourceName }}</span>
              <div class="mini-chart" :ref="el => setChartRef(item.i, el)"></div>
            </div>
          </template>
          <!-- DATASET 任务 - 根据 chartConfig.type 区分展示方式 -->
          <template v-else-if="item.widgetType === 'TASK'">
            <!-- 组件标题栏 -->
            <div class="widget-title-bar" :style="getTitleStyle(item)">
              <span class="widget-title-text">{{ item.title || item.sourceName }}</span>
            </div>
            <!-- table 类型显示普通表格 -->
            <div v-if="getTaskChartType(item) === 'table'" class="table-preview">
              <el-table :data="getTaskTableData(item)" :max-height="200" size="small" class="preview-table">
                <el-table-column v-for="col in getTaskTableColumns(item)" :key="col" :prop="col" :label="col"
                  :min-width="80" />
              </el-table>
            </div>
            <!-- pivot 类型显示二维透视表 -->
            <div v-else-if="getTaskChartType(item) === 'pivot'" class="pivot-preview">
              <div class="pivot-table-container">
                <table class="pivot-table">
                  <thead>
                    <tr>
                      <th class="corner-cell">{{ getPivotCornerLabel(item) }}</th>
                      <th v-for="col in getPivotTableData(item).cols" :key="col" class="col-header">{{ col }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in getPivotTableData(item).rows" :key="row">
                      <td class="row-header">{{ row }}</td>
                      <td v-for="col in getPivotTableData(item).cols" :key="col" class="value-cell">{{
                        formatPivotValue(getPivotTableData(item).matrix[row]?.[col]) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <!-- 其他类型显示图表 -->
            <div v-else class="chart-container" :ref="el => setChartRef(item.i, el)"></div>
          </template>
          <!-- 时钟 -->
          <template v-else-if="item.widgetType === 'CLOCK'">
            <div class="clock-widget">
              <div class="clock-time">{{ currentTime }}</div>
              <div class="clock-date">{{ currentDate }}</div>
            </div>
          </template>
          <!-- 文本 -->
          <template v-else-if="item.widgetType === 'TEXT'">
            <div class="text-widget">
              <div class="text-content">{{ item.title || '文本' }}</div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- Create Dashboard Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建大屏" width="400px">
      <el-form :model="newDashboard" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="newDashboard.name" placeholder="输入大屏名称" />
        </el-form-item>
        <el-form-item label="列数">
          <el-input-number v-model="newDashboard.gridCols" :min="12" :max="48" />
        </el-form-item>
        <el-form-item label="行数">
          <el-input-number v-model="newDashboard.gridRows" :min="6" :max="24" />
        </el-form-item>
        <el-form-item label="单元高度">
          <el-input-number v-model="newDashboard.cellHeight" :min="30" :max="120" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
        <el-form-item label="组件间距">
          <el-input-number v-model="newDashboard.widgetMargin" :min="0" :max="30" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createDashboard">创建</el-button>
      </template>
    </el-dialog>

    <!-- Settings Dialog -->
    <el-dialog v-model="settingsDialogVisible" title="大屏设置" width="400px">
      <el-form v-if="currentDashboard" :model="currentDashboard" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="currentDashboard.name" />
        </el-form-item>
        <el-form-item label="默认大屏">
          <el-switch v-model="currentDashboard.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="列数">
          <el-input-number v-model="currentDashboard.gridCols" :min="12" :max="48" />
        </el-form-item>
        <el-form-item label="行数">
          <el-input-number v-model="currentDashboard.gridRows" :min="6" :max="24" />
        </el-form-item>
        <el-form-item label="单元高度">
          <el-input-number v-model="currentDashboard.cellHeight" :min="30" :max="120" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
        <el-form-item label="组件间距">
          <el-input-number v-model="currentDashboard.widgetMargin" :min="0" :max="30" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="danger" @click="deleteDashboard">删除大屏</el-button>
        <el-button @click="settingsDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateDashboardSettings">保存</el-button>
      </template>
    </el-dialog>

    <!-- Widget Style Config Dialog -->
    <el-dialog v-model="styleConfigDialogVisible" title="组件样式配置" width="500px">
      <el-form v-if="currentConfigWidget" :model="currentConfigWidget.styleConfig" label-width="100px">
        <el-divider content-position="left">文字样式</el-divider>
        <el-form-item label="标题字号">
          <el-input-number v-model="currentConfigWidget.styleConfig.titleFontSize" :min="12" :max="48" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
        <el-form-item label="标题颜色">
          <el-color-picker v-model="currentConfigWidget.styleConfig.titleColor" show-alpha />
          <el-button v-if="currentConfigWidget.styleConfig.titleColor" size="small" link
            @click="currentConfigWidget.styleConfig.titleColor = ''">重置</el-button>
        </el-form-item>
        <el-form-item label="内容字号">
          <el-input-number v-model="currentConfigWidget.styleConfig.fontSize" :min="10" :max="72" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
        <el-form-item label="内容颜色">
          <el-color-picker v-model="currentConfigWidget.styleConfig.fontColor" show-alpha />
          <el-button v-if="currentConfigWidget.styleConfig.fontColor" size="small" link
            @click="currentConfigWidget.styleConfig.fontColor = ''">重置</el-button>
        </el-form-item>

        <el-divider content-position="left">边框样式</el-divider>
        <el-form-item label="显示边框">
          <el-switch v-model="currentConfigWidget.styleConfig.showBorder" />
        </el-form-item>
        <el-form-item label="边框颜色" v-if="currentConfigWidget.styleConfig.showBorder">
          <el-color-picker v-model="currentConfigWidget.styleConfig.borderColor" show-alpha />
        </el-form-item>
        <el-form-item label="边框宽度" v-if="currentConfigWidget.styleConfig.showBorder">
          <el-input-number v-model="currentConfigWidget.styleConfig.borderWidth" :min="0" :max="10" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>
        <el-form-item label="圆角大小">
          <el-input-number v-model="currentConfigWidget.styleConfig.borderRadius" :min="0" :max="30" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">px</span>
        </el-form-item>

        <el-divider content-position="left">背景样式</el-divider>
        <el-form-item label="背景颜色">
          <el-color-picker v-model="currentConfigWidget.styleConfig.backgroundColor" show-alpha />
          <el-button v-if="currentConfigWidget.styleConfig.backgroundColor" size="small" link
            @click="currentConfigWidget.styleConfig.backgroundColor = ''">重置</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="styleConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWidgetConfig">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View, Check, Setting, InfoFilled, DataLine, Clock, Document, Edit, Delete, Grid } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { toJsonClean } from '../api/utils'

// State
const dashboardList = ref([])
const currentDashboardId = ref(null)
const currentDashboard = ref(null)
const layout = ref([])
const originalLayout = ref([])
const availableTasks = ref([])
const isPreview = ref(false)
const saving = ref(false)
const createDialogVisible = ref(false)
const settingsDialogVisible = ref(false)

// 任务数据缓存
const taskDataCache = ref({})
const chartInstances = {}
const chartRefs = {}  // 存储预览模式图表 DOM 的 ref
let refreshTimer = null

// 组件样式配置
const styleConfigDialogVisible = ref(false)
const currentConfigWidget = ref(null)

// 默认样式配置
const defaultStyleConfig = {
  titleFontSize: 14,
  titleColor: '',
  fontSize: 24,
  fontColor: '',
  showBorder: true,
  borderColor: '',
  borderWidth: 1,
  borderRadius: 8,
  backgroundColor: ''
}

// 设置图表容器 ref
const setChartRef = (itemId, el) => {
  if (el) {
    chartRefs[itemId] = el
  } else {
    delete chartRefs[itemId]
  }
}

// 计算预览模式下 widget 的位置和尺寸样式（需要与 grid-layout 的 margin 一致）
const getPreviewWidgetStyle = (item) => {
  const cols = currentDashboard.value?.gridCols || 24
  const cellHeight = currentDashboard.value?.cellHeight || 60
  const margin = currentDashboard.value?.widgetMargin || 10 // 使用配置的间距，默认10

  // 计算每列宽度百分比
  const colWidthPercent = 100 / cols

  // 位置和尺寸（考虑间距）
  const leftPercent = item.x * colWidthPercent
  const widthPercent = item.w * colWidthPercent
  const top = item.y * (cellHeight + margin) + margin
  const height = item.h * cellHeight + (item.h - 1) * margin

  // 基础位置样式
  const positionStyle = {
    position: 'absolute',
    left: `calc(${leftPercent}% + ${margin}px)`,
    top: `${top}px`,
    width: `calc(${widthPercent}% - ${margin * 2}px)`,
    height: `${height}px`,
    boxSizing: 'border-box'
  }

  // 合并组件样式配置
  const cfg = item.styleConfig || {}

  // 边框
  if (cfg.showBorder === false) {
    positionStyle.border = 'none'
  } else if (cfg.borderColor || cfg.borderWidth) {
    positionStyle.border = `${cfg.borderWidth || 1}px solid ${cfg.borderColor || 'var(--theme-border)'}`
  }

  // 圆角
  if (cfg.borderRadius !== undefined) {
    positionStyle.borderRadius = `${cfg.borderRadius}px`
  }

  // 背景
  if (cfg.backgroundColor) {
    positionStyle.backgroundColor = cfg.backgroundColor
  }

  return positionStyle
}

const newDashboard = reactive({
  name: '',
  gridCols: 24,
  gridRows: 12,
  cellHeight: 60,
  widgetMargin: 10
})

const currentTime = ref(dayjs().format('HH:mm:ss'))
const currentDate = ref(dayjs().format('YYYY年MM月DD日'))
let clockTimer = null

// Computed - 只比较关键布局字段
const hasChanges = computed(() => {
  if (layout.value.length !== originalLayout.value.length) return true

  for (let i = 0; i < layout.value.length; i++) {
    const curr = layout.value[i]
    const orig = originalLayout.value[i]

    // 比较位置和尺寸
    if (curr.x !== orig.x || curr.y !== orig.y ||
      curr.w !== orig.w || curr.h !== orig.h) return true

    // 比较类型和来源
    if (curr.widgetType !== orig.widgetType ||
      curr.sourceId !== orig.sourceId ||
      curr.title !== orig.title) return true

    // 比较样式配置
    if (JSON.stringify(curr.styleConfig) !== JSON.stringify(orig.styleConfig)) return true
  }

  return false
})

// Methods
const loadDashboardList = async () => {
  try {
    dashboardList.value = await request.get('/dashboard')
    if (dashboardList.value.length > 0 && !currentDashboardId.value) {
      const defaultDash = dashboardList.value.find(d => d.isDefault) || dashboardList.value[0]
      currentDashboardId.value = defaultDash.id
      await loadDashboard()
    }
  } catch (e) {
    console.error('Failed to load dashboards', e)
  }
}

const loadDashboard = async () => {
  if (!currentDashboardId.value) return
  try {
    const res = await request.get(`/dashboard/${currentDashboardId.value}`)
    currentDashboard.value = res.dashboard

    // Convert widgets to grid layout format
    layout.value = (res.widgets || []).map(w => {
      // 从 displayConfig 解析 styleConfig
      let styleConfig = { ...defaultStyleConfig }
      if (w.displayConfig) {
        try {
          const cfg = JSON.parse(w.displayConfig)
          if (cfg.styleConfig) {
            styleConfig = { ...defaultStyleConfig, ...cfg.styleConfig }
          }
        } catch (e) { }
      }

      return {
        i: String(w.id),
        x: w.gridX,
        y: w.gridY,
        w: w.gridW,
        h: w.gridH,
        widgetType: w.widgetType,
        sourceId: w.sourceId,
        sourceName: w.sourceName,
        resultType: w.sourceResultType,
        title: w.title,
        displayConfig: w.displayConfig,
        styleConfig: styleConfig
      }
    })
    originalLayout.value = JSON.parse(JSON.stringify(layout.value))

    // 加载所有任务组件的数据
    if (isPreview.value) {
      await loadAllTaskData()
    }
  } catch (e) {
    console.error('Failed to load dashboard', e)
  }
}

const loadTasks = async () => {
  try {
    const tasks = await request.get('/task')
    availableTasks.value = tasks.filter(t => t.isActive)
  } catch (e) {
    console.error('Failed to load tasks', e)
  }
}

// 加载所有任务组件的数据
const loadAllTaskData = async () => {
  const taskWidgets = layout.value.filter(w => w.widgetType === 'TASK' && w.sourceId)
  for (const widget of taskWidgets) {
    await loadTaskData(widget)
  }
}

// 加载单个任务数据
const loadTaskData = async (widget) => {
  if (!widget.sourceId) return
  try {
    const res = await request.get(`/stats/${widget.sourceId}`)
    console.log('loadTaskData response for', widget.sourceId, res)
    if (res && res.length > 0) {
      const latest = res[0]
      const task = availableTasks.value.find(t => t.id === widget.sourceId)
      const chartConfig = task?.chartConfig ? JSON.parse(task.chartConfig) : {}

      // 基础数据
      let resultJson = latest.resultJson

      // 透视表类型：从动态表获取带 execution_time 的历史数据（与 Dashboard.vue 一致）
      if (chartConfig.type === 'pivot') {
        try {
          const pivotData = await request.get(`/stats/task/${widget.sourceId}/pivot-data`, { params: { limit: 1000 } })
          console.debug('Pivot data loaded:', widget.sourceId, pivotData?.length)
          resultJson = JSON.stringify(pivotData || [])
        } catch (e) {
          console.error('Pivot data error:', e)
          // 降级：使用普通数据
        }
      }

      taskDataCache.value[widget.sourceId] = {
        resultNumber: latest.resultNumber,
        resultJson: resultJson,
        executionTime: latest.executionTime,
        trendData: [...res].reverse().map(r => ({
          time: r.executionTime,
          value: r.resultNumber
        })),
        chartConfig: chartConfig
      }

      console.log('Widget resultType:', widget.resultType, 'cached:', taskDataCache.value[widget.sourceId])

      // 延迟渲染图表，确保DOM完全准备好
      setTimeout(() => {
        if (widget.resultType === 'SCALAR') {
          renderMiniChart(widget)
        } else {
          renderDatasetChart(widget)
        }
      }, 200)
    }
  } catch (e) {
    console.error('Failed to load task data', e)
  }
}

// 获取任务的图表类型
const getTaskChartType = (item) => {
  const cached = taskDataCache.value[item.sourceId]
  if (cached && cached.chartConfig) {
    return cached.chartConfig.type || 'bar'
  }
  return 'bar'
}

// 获取任务表格数据
const getTaskTableData = (item) => {
  const cached = taskDataCache.value[item.sourceId]
  if (cached && cached.resultJson) {
    try {
      const data = JSON.parse(cached.resultJson || '[]')
      return data.slice(0, 10) // 只显示前10条
    } catch (e) {
      return []
    }
  }
  return []
}

// 获取任务表格列
const getTaskTableColumns = (item) => {
  const data = getTaskTableData(item)
  if (data.length > 0) {
    return Object.keys(data[0]).filter(k => typeof data[0][k] !== 'object').slice(0, 5) // 最多5列
  }
  return []
}

// 获取字段别名
const getFieldAlias = (chartConfig, field) => {
  if (chartConfig && chartConfig.fieldAlias && chartConfig.fieldAlias[field]) {
    return chartConfig.fieldAlias[field]
  }
  return field
}

// 获取透视表的角落标签（行字段别名 \ 列字段别名）
const getPivotCornerLabel = (item) => {
  const cached = taskDataCache.value[item.sourceId]
  if (cached && cached.chartConfig) {
    const cfg = cached.chartConfig
    const rowField = cfg.pivotRowField || '行'
    const colField = cfg.pivotColField || '列'
    const rowAlias = getFieldAlias(cfg, rowField)
    const colAlias = getFieldAlias(cfg, colField)
    return `${rowAlias} \\ ${colAlias}`
  }
  return '行 \\ 列'
}

// 生成透视表数据（二维矩阵）
const getPivotTableData = (item) => {
  const cached = taskDataCache.value[item.sourceId]
  if (!cached || !cached.resultJson) {
    return { rows: [], cols: [], matrix: {} }
  }

  let data = []
  try {
    data = JSON.parse(cached.resultJson || '[]')
  } catch (e) {
    return { rows: [], cols: [], matrix: {} }
  }

  // 过滤无效数据
  data = data.filter(row => row && typeof row === 'object' && !row.info)
  if (!data.length) {
    return { rows: [], cols: [], matrix: {} }
  }

  const cfg = cached.chartConfig || {}
  const rowField = cfg.pivotRowField
  const colField = cfg.pivotColField || 'execution_time'
  const valueField = cfg.pivotValueField
  const aggMethod = cfg.pivotAggMethod || 'SUM'

  if (!rowField || !valueField) {
    // 如果没有配置，使用前两列作为行列，第三列作为值
    const keys = Object.keys(data[0]).filter(k => typeof data[0][k] !== 'object')
    return generateSimplePivot(data, keys[0], keys[1], keys[2] || keys[1])
  }

  // 生成透视表
  const rowSet = new Set()
  const colSet = new Set()
  const matrix = {}
  const counts = {}

  data.forEach(row => {
    const rowKey = String(row[rowField] ?? '未知')
    const colKey = String(row[colField] ?? '未知')
    const val = parseFloat(row[valueField]) || 0

    rowSet.add(rowKey)
    colSet.add(colKey)

    if (!matrix[rowKey]) {
      matrix[rowKey] = {}
      counts[rowKey] = {}
    }

    if (aggMethod === 'COUNT') {
      matrix[rowKey][colKey] = (matrix[rowKey][colKey] || 0) + 1
    } else if (aggMethod === 'SUM' || aggMethod === 'AVG') {
      matrix[rowKey][colKey] = (matrix[rowKey][colKey] || 0) + val
      counts[rowKey][colKey] = (counts[rowKey][colKey] || 0) + 1
    }
  })

  // 计算平均值
  if (aggMethod === 'AVG') {
    for (const r in matrix) {
      for (const c in matrix[r]) {
        if (counts[r][c]) {
          matrix[r][c] = matrix[r][c] / counts[r][c]
        }
      }
    }
  }

  return {
    rows: Array.from(rowSet).sort(),  // 不限制行数，显示完整数据
    cols: Array.from(colSet).sort(),   // 不限制列数
    matrix
  }
}

// 简单透视表生成（无配置时使用）
const generateSimplePivot = (data, rowField, colField, valueField) => {
  const rowSet = new Set()
  const colSet = new Set()
  const matrix = {}

  data.forEach(row => {
    const rowKey = String(row[rowField] ?? '未知')
    const colKey = String(row[colField] ?? '未知')
    const val = parseFloat(row[valueField]) || 0

    rowSet.add(rowKey)
    colSet.add(colKey)

    if (!matrix[rowKey]) matrix[rowKey] = {}
    matrix[rowKey][colKey] = (matrix[rowKey][colKey] || 0) + val
  })

  return {
    rows: Array.from(rowSet).sort(),
    cols: Array.from(colSet).sort(),
    matrix
  }
}

// 格式化透视表值
const formatPivotValue = (val) => {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'number') {
    if (val >= 10000) return (val / 10000).toFixed(1) + '万'
    if (Number.isInteger(val)) return val.toString()
    return val.toFixed(2)
  }
  return val
}

// 获取任务数值
const getTaskValue = (item) => {
  const cached = taskDataCache.value[item.sourceId]
  if (cached && cached.resultNumber !== undefined && cached.resultNumber !== null) {
    const val = cached.resultNumber
    if (Math.abs(val) >= 10000) {
      return (val / 10000).toFixed(2) + '万'
    }
    return typeof val === 'number' ? val.toFixed(2) : val
  }
  return '--'
}

// 渲染 SCALAR 迷你趋势图
const renderMiniChart = (widget) => {
  const cached = taskDataCache.value[widget.sourceId]
  if (!cached || !cached.trendData) {
    console.log('renderMiniChart: no cached data for', widget.sourceId)
    return
  }

  // 使用 chartRefs 获取 DOM 元素
  const dom = chartRefs[widget.i]
  if (!dom) {
    console.log('renderMiniChart: DOM ref not found for', widget.i)
    return
  }

  console.log('renderMiniChart: DOM size', dom.offsetWidth, dom.offsetHeight)

  // 如果尺寸为0，延迟重试
  if (dom.offsetWidth === 0 || dom.offsetHeight === 0) {
    console.log('renderMiniChart: DOM size is 0, retrying...')
    setTimeout(() => renderMiniChart(widget), 300)
    return
  }

  const chartKey = 'chart-' + widget.i
  if (chartInstances[chartKey]) chartInstances[chartKey].dispose()
  const chart = echarts.init(dom)
  chartInstances[chartKey] = chart

  const option = {
    backgroundColor: 'transparent',
    grid: { top: 5, bottom: 5, left: 5, right: 5 },
    xAxis: { type: 'category', show: false, data: cached.trendData.map(d => d.time) },
    yAxis: { type: 'value', show: false },
    series: [{
      type: 'line',
      data: cached.trendData.map(d => d.value),
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#00d4ff', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.3)' },
          { offset: 1, color: 'rgba(0,212,255,0)' }
        ])
      }
    }]
  }
  chart.setOption(option)
  chart.resize()
}

// 渲染 DATASET 图表
const renderDatasetChart = (widget) => {
  const cached = taskDataCache.value[widget.sourceId]
  if (!cached || !cached.resultJson) {
    console.log('renderDatasetChart: no cached data for', widget.sourceId)
    return
  }

  // table/pivot 类型使用表格展示，不渲染图表
  const cfg = cached.chartConfig || {}
  if (cfg.type === 'table' || cfg.type === 'pivot') {
    console.log('renderDatasetChart: skipping table/pivot type, using el-table')
    return
  }

  // 使用 chartRefs 获取 DOM 元素
  const dom = chartRefs[widget.i]
  if (!dom) {
    console.log('renderDatasetChart: DOM ref not found for', widget.i)
    return
  }

  console.log('renderDatasetChart: DOM size', dom.offsetWidth, dom.offsetHeight)

  // 如果尺寸为0，延迟重试
  if (dom.offsetWidth === 0 || dom.offsetHeight === 0) {
    console.log('renderDatasetChart: DOM size is 0, retrying...')
    setTimeout(() => renderDatasetChart(widget), 300)
    return
  }

  let data = []
  try {
    data = JSON.parse(cached.resultJson || '[]')
  } catch (e) {
    console.log('renderDatasetChart: parse error', e)
    return
  }

  if (!data.length) {
    console.log('renderDatasetChart: no data')
    return
  }

  // 注意: cfg 已在上方定义

  // 安全获取原始值（非对象）
  const safeValue = (val) => {
    if (val === null || val === undefined) return ''
    if (typeof val === 'object') {
      // 对象类型返回 JSON 或空
      try { return JSON.stringify(val) } catch { return '' }
    }
    return val
  }

  const safeString = (val) => {
    const v = safeValue(val)
    return typeof v === 'string' ? v : String(v)
  }

  const safeNumber = (val) => {
    const v = safeValue(val)
    return typeof v === 'number' ? v : (parseFloat(v) || 0)
  }

  // 智能推断 X 和 Y 轴
  const keys = Object.keys(data[0])
  let xKey = cfg.xAxis
  let yKey = cfg.yAxis

  // 如果没有配置轴或者是 pivot/table 类型，自动推断
  if (!xKey || cfg.type === 'pivot' || cfg.type === 'table') {
    // 找第一个非对象类型的字段作为 X 轴
    xKey = keys.find(k => {
      const v = data[0][k]
      return v !== null && v !== undefined && typeof v !== 'object'
    }) || keys[0]
  }
  if (!yKey || cfg.type === 'pivot' || cfg.type === 'table') {
    // 找第一个数值类型的作为 Y 轴
    yKey = keys.find(k => typeof data[0][k] === 'number') || keys[1] || keys[0]
  }

  // 对于 pivot/table，使用柱状图
  const chartType = (cfg.type === 'pivot' || cfg.type === 'table') ? 'bar' : (cfg.type || 'bar')

  console.log('renderDatasetChart: rendering', chartType, 'xKey:', xKey, 'yKey:', yKey)

  try {
    const chartKey = 'chart-' + widget.i
    if (chartInstances[chartKey]) {
      chartInstances[chartKey].dispose()
      delete chartInstances[chartKey]
    }

    const chart = echarts.init(dom)
    chartInstances[chartKey] = chart

    const option = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(0,20,40,0.9)', borderColor: '#00d4ff' },
      grid: { top: 30, bottom: 40, left: 50, right: 20 },
      xAxis: {
        type: 'category',
        data: data.map(d => safeString(d[xKey])),
        axisLine: { lineStyle: { color: '#1a3a5c' } },
        axisLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 10, rotate: 30, interval: 0 }
      },
      yAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: '#1a3a5c' } },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
        axisLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 10 }
      },
      series: [{
        type: chartType,
        data: data.map(d => safeNumber(d[yKey])),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' },
            { offset: 1, color: '#0066ff' }
          ])
        }
      }]
    }
    chart.setOption(option)
    chart.resize()
  } catch (e) {
    console.error('renderDatasetChart error:', e)
  }
}

const onDragStart = (event, widgetType, source) => {
  event.dataTransfer.setData('widgetType', widgetType)
  event.dataTransfer.setData('sourceId', source?.id || '')
  event.dataTransfer.setData('sourceName', source?.name || '')
  event.dataTransfer.setData('resultType', source?.resultType || '')
}

const onDrop = (event) => {
  const widgetType = event.dataTransfer.getData('widgetType')
  const sourceId = event.dataTransfer.getData('sourceId')
  const sourceName = event.dataTransfer.getData('sourceName')
  const resultType = event.dataTransfer.getData('resultType')

  // Calculate grid position from drop coordinates
  const rect = event.currentTarget.getBoundingClientRect()
  const x = Math.floor((event.clientX - rect.left) / ((rect.width) / (currentDashboard.value?.gridCols || 24)))
  const y = Math.floor((event.clientY - rect.top) / (currentDashboard.value?.cellHeight || 60))

  // Add new widget to layout
  const newItem = {
    i: 'new-' + Date.now(),
    x: Math.min(x, (currentDashboard.value?.gridCols || 24) - 4),
    y: y,
    w: widgetType === 'CLOCK' ? 3 : 4,
    h: widgetType === 'CLOCK' ? 2 : 3,
    widgetType,
    sourceId: sourceId ? Number(sourceId) : null,
    sourceName,
    resultType,
    title: '',
    styleConfig: { ...defaultStyleConfig }  // 添加默认样式配置
  }

  layout.value.push(newItem)
}

const onLayoutUpdated = () => {
  // Layout was updated by drag/resize
}

const getWidgetTitle = (item) => {
  if (item.title) return item.title
  if (item.sourceName) return item.sourceName
  if (item.widgetType === 'CLOCK') return '时钟'
  if (item.widgetType === 'TEXT') return '文本'
  return '组件'
}

const editWidget = (item) => {
  ElMessageBox.prompt('输入组件标题', '编辑组件', {
    inputValue: item.title || item.sourceName || ''
  }).then(({ value }) => {
    item.title = value
  }).catch(() => { })
}

// 打开组件样式配置对话框
const openWidgetConfig = (item) => {
  // 确保组件有 styleConfig
  if (!item.styleConfig) {
    item.styleConfig = { ...defaultStyleConfig }
  }
  currentConfigWidget.value = item
  styleConfigDialogVisible.value = true
}

// 保存组件样式配置
const saveWidgetConfig = () => {
  styleConfigDialogVisible.value = false
  ElMessage.success('样式配置已应用')
}

// 获取组件动态样式
const getWidgetStyle = (item) => {
  const cfg = item.styleConfig || {}
  const style = {}

  // 边框
  if (cfg.showBorder === false) {
    style.border = 'none'
  } else if (cfg.borderColor || cfg.borderWidth) {
    style.border = `${cfg.borderWidth || 1}px solid ${cfg.borderColor || 'var(--theme-border)'}`
  }

  // 圆角
  if (cfg.borderRadius !== undefined && cfg.borderRadius !== 8) {
    style.borderRadius = `${cfg.borderRadius}px`
  }

  // 背景
  if (cfg.backgroundColor) {
    style.backgroundColor = cfg.backgroundColor
  }

  return style
}

// 获取标题样式
const getTitleStyle = (item) => {
  const cfg = item.styleConfig || {}
  const style = {}
  if (cfg.titleFontSize && cfg.titleFontSize !== 14) {
    style.fontSize = `${cfg.titleFontSize}px`
  }
  if (cfg.titleColor) {
    style.color = cfg.titleColor
  }
  return style
}

// 获取内容样式
const getContentStyle = (item) => {
  const cfg = item.styleConfig || {}
  const style = {}
  if (cfg.fontSize && cfg.fontSize !== 24) {
    style.fontSize = `${cfg.fontSize}px`
  }
  if (cfg.fontColor) {
    style.color = cfg.fontColor
  }
  return style
}

const removeWidget = (item) => {
  ElMessageBox.confirm('确定删除此组件?', '提示', { type: 'warning' })
    .then(() => {
      const index = layout.value.findIndex(l => l.i === item.i)
      if (index > -1) layout.value.splice(index, 1)
    })
    .catch(() => { })
}

// 双击编辑文本组件
const editTextWidget = (item) => {
  ElMessageBox.prompt('输入文本内容', '编辑文本', {
    inputValue: item.title || '',
    inputType: 'textarea',
    inputPlaceholder: '请输入文本内容...'
  }).then(({ value }) => {
    item.title = value
  }).catch(() => { })
}

const togglePreview = async () => {
  if (!isPreview.value) {
    // 进入预览模式
    isPreview.value = true
    // 等待 DOM 更新后加载数据并启动刷新
    await nextTick()
    setTimeout(async () => {
      await loadAllTaskData()
      startRefreshTimer()
    }, 300)
  } else {
    // 退出预览模式：先停止刷新
    stopRefreshTimer()

    // 安全清理图表实例
    for (const domId of Object.keys(chartInstances)) {
      try {
        if (chartInstances[domId] && !chartInstances[domId].isDisposed()) {
          chartInstances[domId].dispose()
        }
      } catch (e) {
        console.warn('Chart dispose warning:', e)
      }
      delete chartInstances[domId]
    }

    // 延迟切换状态，让清理完成
    await nextTick()
    isPreview.value = false
  }
}

const startRefreshTimer = () => {
  stopRefreshTimer()
  refreshTimer = setInterval(() => {
    loadAllTaskData()
  }, 30000) // 每30秒刷新一次
}

const stopRefreshTimer = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const saveLayout = async () => {
  if (!currentDashboardId.value) return
  saving.value = true
  try {
    const widgets = layout.value.map(item => {
      // 将 styleConfig 序列化到 displayConfig
      const displayConfig = {
        ...(item.displayConfig ? JSON.parse(item.displayConfig) : {}),
        styleConfig: item.styleConfig || {}
      }

      return {
        widgetType: item.widgetType,
        sourceId: item.sourceId,
        title: item.title,
        gridX: item.x,
        gridY: item.y,
        gridW: item.w,
        gridH: item.h,
        displayConfig: toJsonClean(displayConfig)
      }
    })

    await request.post(`/dashboard/${currentDashboardId.value}/layout`, widgets)
    ElMessage.success('布局保存成功')
    await loadDashboard() // Reload to get real IDs，loadDashboard 会同步 originalLayout
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const showCreateDialog = () => {
  newDashboard.name = ''
  newDashboard.gridCols = 24
  newDashboard.gridRows = 12
  newDashboard.cellHeight = 60
  createDialogVisible.value = true
}

const createDashboard = async () => {
  if (!newDashboard.name) {
    ElMessage.warning('请输入大屏名称')
    return
  }
  try {
    const created = await request.post('/dashboard', newDashboard)
    dashboardList.value.push(created)
    currentDashboardId.value = created.id
    await loadDashboard()
    createDialogVisible.value = false
    ElMessage.success('创建成功')
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

const showSettingsDialog = () => {
  if (currentDashboard.value) {
    settingsDialogVisible.value = true
  }
}

const updateDashboardSettings = async () => {
  try {
    await request.put('/dashboard', currentDashboard.value)
    await loadDashboardList()
    settingsDialogVisible.value = false
    ElMessage.success('设置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const deleteDashboard = async () => {
  ElMessageBox.confirm('确定删除此大屏及其所有组件?', '警告', { type: 'error' })
    .then(async () => {
      await request.delete(`/dashboard/${currentDashboardId.value}`)
      currentDashboardId.value = null
      currentDashboard.value = null
      layout.value = []
      settingsDialogVisible.value = false
      await loadDashboardList()
      ElMessage.success('删除成功')
    })
    .catch(() => { })
}

// Lifecycle
onMounted(() => {
  loadDashboardList()
  loadTasks()
  clockTimer = setInterval(() => {
    currentTime.value = dayjs().format('HH:mm:ss')
    currentDate.value = dayjs().format('YYYY年MM月DD日')
  }, 1000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  stopRefreshTimer()
  Object.values(chartInstances).forEach(c => c?.dispose())
})
</script>

<style scoped>
.dashboard-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-bg);
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: var(--theme-card-bg);
  border-bottom: 1px solid var(--theme-border);
}

.header-left,
.header-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-center h3 {
  margin: 0;
  color: var(--theme-text);
}

.editor-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.widget-library {
  width: 240px;
  background: var(--theme-card-bg);
  border-right: 1px solid var(--theme-border);
  overflow-y: auto;
  flex-shrink: 0;
}

.library-header {
  padding: 15px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--theme-border);
  font-weight: 600;
  color: var(--theme-text);
}

.library-section {
  padding: 10px;
}

.section-title {
  font-size: 12px;
  color: var(--theme-text-secondary);
  margin-bottom: 8px;
  padding: 0 5px;
}

.widget-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.widget-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.widget-item:hover {
  background: var(--el-fill-color);
  transform: translateX(4px);
}

.widget-item:active {
  cursor: grabbing;
}

.widget-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.widget-name {
  font-size: 13px;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.widget-type {
  font-size: 11px;
  color: var(--theme-text-secondary);
}

.grid-canvas {
  flex: 1;
  padding: 20px;
  overflow: auto;
  background: var(--theme-bg);
  position: relative;
}

/* 预览画布 - 独立于 grid-layout */
.preview-canvas {
  flex: 1;
  padding: 20px;
  overflow: auto;
  background: var(--theme-bg);
  position: relative;
  min-height: 500px;
}

.preview-widget {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 预览模式标题栏 */
.widget-title-bar {
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.widget-title-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-text);
}

.preview-widget .scalar-display,
.preview-widget .chart-container,
.preview-widget .clock-widget,
.preview-widget .text-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px;
}

.preview-widget .chart-container {
  min-height: 120px;
}

.preview-widget .table-preview {
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 5px;
}

.preview-table {
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
  font-size: 12px;
}

/* 透视表预览样式 */
.pivot-preview {
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 5px;
}

.pivot-table-container {
  width: 100%;
  overflow-x: auto;
}

.pivot-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.pivot-table th,
.pivot-table td {
  padding: 6px 10px;
  text-align: center;
  border: 1px solid var(--theme-border);
  white-space: nowrap;
}

.pivot-table .corner-cell {
  background: var(--el-fill-color);
  color: var(--theme-text-secondary);
  font-weight: 500;
  font-size: 11px;
}

.pivot-table .col-header {
  background: var(--el-fill-color-light);
  color: var(--theme-accent);
  font-weight: 600;
}

.pivot-table .row-header {
  background: var(--el-fill-color-lighter);
  color: var(--theme-accent);
  font-weight: 500;
  text-align: left;
}

.pivot-table .value-cell {
  background: var(--el-bg-color);
  color: var(--theme-text-secondary);
}

.grid-canvas.preview-mode {
  background: linear-gradient(135deg, #030b15 0%, #0a1525 100%);
}

.grid-widget {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  overflow: hidden;
}

.widget-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--theme-border);
}

.widget-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.widget-actions {
  display: flex;
  gap: 4px;
}

.widget-body {
  flex: 1;
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100px;
  overflow: hidden;
}

.task-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.scalar-display {
  text-align: center;
  width: 100%;
}

.scalar-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--theme-accent);
  display: block;
}

.scalar-label {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.mini-chart {
  width: 100%;
  height: 60px;
  margin-top: 8px;
  min-height: 60px;
}

.chart-container {
  width: 100%;
  height: 100%;
  min-height: 150px;
  flex: 1;
}

.chart-placeholder-text {
  color: var(--theme-text-secondary);
  font-size: 12px;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.chart-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-text-secondary);
}

.clock-widget {
  text-align: center;
}

.clock-time {
  font-size: 32px;
  font-weight: 600;
  color: var(--theme-accent);
}

.clock-date {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.text-widget {
  text-align: center;
  color: var(--theme-text);
}

.empty-canvas {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: var(--theme-text-secondary);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

/* Grid Layout Styles */
:deep(.vue-grid-layout) {
  min-height: 100%;
}

:deep(.vue-grid-item.vue-grid-placeholder) {
  background: var(--theme-accent);
  opacity: 0.3;
  border-radius: 8px;
}

:deep(.vue-grid-item > .vue-resizable-handle) {
  background: none;
}

:deep(.vue-grid-item > .vue-resizable-handle::after) {
  content: '';
  position: absolute;
  right: 3px;
  bottom: 3px;
  width: 10px;
  height: 10px;
  border-right: 2px solid var(--theme-accent);
  border-bottom: 2px solid var(--theme-accent);
}
</style>
