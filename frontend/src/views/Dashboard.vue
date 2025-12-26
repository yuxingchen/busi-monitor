<template>
  <div class="dashboard-container" :class="{ 'fullscreen-mode': isFullscreen }">
    <!-- Header -->
    <header class="dashboard-header">
      <div class="header-left">
        <div class="logo-wrapper">
          <div class="logo-pulse"></div>
          <el-icon class="logo-icon">
            <Monitor />
          </el-icon>
        </div>
        <div class="title-group">
          <h1 class="main-title">业务数据监控中心</h1>
          <span class="sub-title">BUSINESS MONITOR CENTER</span>
        </div>
      </div>
      <div class="header-center">
        <div class="decoration-line left"></div>
        <div class="status-badge">
          <span class="pulse-dot"></span>
          系统运行中
        </div>
        <div class="decoration-line right"></div>
      </div>
      <div class="header-right">
        <div class="time-block">
          <div class="time-date">{{ currentDate }}</div>
          <div class="time-clock">{{ currentTime }}</div>
        </div>
        <el-button class="refresh-all-btn" circle @click="forceRefreshAllTasks" title="刷新全部任务">
          <el-icon>
            <Refresh />
          </el-icon>
        </el-button>
        <el-button class="fullscreen-btn" circle @click="toggleFullscreen">
          <el-icon>
            <FullScreen />
          </el-icon>
        </el-button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="dashboard-main">
      <!-- Key Metrics Section -->
      <section class="metrics-section" v-if="scalarTasks.length > 0">
        <div class="section-header">
          <div class="section-icon"><el-icon>
              <TrendCharts />
            </el-icon></div>
          <span class="section-title">核心指标监控</span>
          <div class="section-line"></div>
        </div>
        <div class="metrics-grid">
          <div v-for="(task, index) in scalarTasks" :key="task.id" class="metric-card"
            :style="{ animationDelay: `${index * 0.1}s` }">
            <div class="card-glow"></div>
            <div class="card-border"></div>
            <div class="card-content">
              <div class="metric-header">
                <span class="metric-label">{{ task.name }}</span>
                <span class="metric-status live">LIVE</span>
              </div>
              <div class="metric-body">
                <template v-if="isTaskLoading(task.id)">
                  <div class="loading-placeholder">
                    <div class="loading-spinner"></div>
                  </div>
                </template>
                <template v-else>
                  <span class="metric-value" :class="getValueClass(task)">
                    {{ formatValue(getLatestValue(task)) }}
                  </span>
                  <span class="metric-unit">{{ getUnit(task) }}</span>
                </template>
              </div>
              <div class="metric-chart" :id="'chart-' + task.id">
                <div v-if="isTaskLoading(task.id)" class="chart-loading">
                  <div class="loading-wave">
                    <span></span><span></span><span></span><span></span><span></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Charts Section -->
      <section class="charts-section" v-if="chartTasks.length > 0">
        <div class="section-header">
          <div class="section-icon"><el-icon>
              <DataAnalysis />
            </el-icon></div>
          <span class="section-title">数据趋势分析</span>
          <div class="section-line"></div>
        </div>
        <div class="charts-grid">
          <div v-for="(task, index) in chartTasks" :key="task.id" class="chart-card"
            :style="{ animationDelay: `${index * 0.15}s` }">
            <div class="card-header">
              <div class="header-left">
                <span class="chart-title">{{ task.name }}</span>
              </div>
              <div class="header-right">
                <span class="realtime-badge">
                  <span class="dot"></span>
                  实时更新
                </span>
              </div>
            </div>
            <div class="chart-body" :id="'chart-' + task.id">
              <div v-if="isTaskLoading(task.id)" class="chart-loading">
                <div class="loading-wave">
                  <span></span><span></span><span></span><span></span><span></span>
                </div>
                <p class="loading-text">数据加载中...</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Tables Section -->
      <section class="tables-section" v-if="tableTasks.length > 0">
        <div class="section-header">
          <div class="section-icon"><el-icon>
              <List />
            </el-icon></div>
          <span class="section-title">数据明细列表</span>
          <div class="section-line"></div>
        </div>
        <div class="tables-grid">
          <div v-for="(task, index) in tableTasks" :key="task.id" class="table-card"
            :style="{ animationDelay: `${index * 0.15}s` }">
            <div class="card-header">
              <span class="table-title">
                {{ task.name }}
                <span v-if="groupingState[task.id]?.groupField" class="group-indicator">
                  - 按「{{ getFieldAlias(task, groupingState[task.id].groupField) }}」分组
                </span>
              </span>
              <div class="header-actions">
                <!-- 分组模式下的图表切换按钮 -->
                <template v-if="groupingState[task.id]?.active">
                  <el-button-group size="small">
                    <el-button :type="groupingState[task.id].chartType === 'line' ? 'primary' : ''"
                      @click="setGroupChartType(task.id, 'line')">折线</el-button>
                    <el-button :type="groupingState[task.id].chartType === 'bar' ? 'primary' : ''"
                      @click="setGroupChartType(task.id, 'bar')">柱状</el-button>
                    <el-button :type="groupingState[task.id].chartType === 'horizontalBar' ? 'primary' : ''"
                      @click="setGroupChartType(task.id, 'horizontalBar')">条形</el-button>
                  </el-button-group>
                  <el-button size="small" @click="exitGroupingMode(task.id)" style="margin-left: 10px;">
                    返回列表
                  </el-button>
                </template>
                <template v-else>
                  <el-dropdown v-if="getDatasetData(task).length > 100"
                    @command="(val) => setDisplayLimit(task.id, val)" trigger="click" class="limit-dropdown">
                    <span class="limit-trigger">{{ getDisplayLimit(task.id) === getDatasetData(task).length ? '全部' :
                      getDisplayLimit(task.id) + ' 条' }}<el-icon class="trigger-arrow">
                        <ArrowDown />
                      </el-icon></span>
                    <template #dropdown>
                      <el-dropdown-menu class="limit-dropdown-menu">
                        <el-dropdown-item v-for="opt in getDisplayLimitOptions(task)" :key="opt.value"
                          :command="opt.value" :class="{ 'is-active': getDisplayLimit(task.id) === opt.value }">
                          {{ opt.label }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-tag size="small" effect="dark" round>共 {{ getDatasetData(task).length }} 条</el-tag>
                </template>
              </div>
            </div>

            <!-- 分组图表模式 -->
            <div v-if="groupingState[task.id]?.active" class="grouping-chart-wrapper">
              <div class="grouping-chart" :id="'group-chart-' + task.id"></div>
            </div>

            <!-- 列表模式 -->
            <div v-else class="table-wrapper">
              <!-- 加载中 -->
              <div v-if="isTaskLoading(task.id)" class="table-loading">
                <div class="loading-wave">
                  <span></span><span></span><span></span><span></span><span></span>
                </div>
                <p class="loading-text">数据加载中...</p>
              </div>
              <template v-else>
                <el-alert type="info" :closable="false" style="margin-bottom: 8px;">
                  💡 双击表头字段可按该字段进行分组分析
                </el-alert>
                <el-table :data="getDisplayedData(task)" :max-height="280" :show-header="true" class="custom-table">
                  <el-table-column v-for="col in getTableColumns(task)" :key="col" :prop="col"
                    :label="getFieldAlias(task, col)" :min-width="120">
                    <template #header>
                      <span class="clickable-header" @dblclick="enterGroupingMode(task, col)"
                        :title="'双击按「' + getFieldAlias(task, col) + '」分组'">
                        {{ getFieldAlias(task, col) }}
                      </span>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
            </div>
          </div>
        </div>
      </section>

      <!-- Pivot Tables Section -->
      <section class="pivot-section" v-if="pivotTasks.length > 0">
        <div class="section-header">
          <div class="section-icon"><el-icon>
              <DataAnalysis />
            </el-icon></div>
          <span class="section-title">二维透视表</span>
          <div class="section-line"></div>
        </div>
        <div class="pivot-grid">
          <div v-for="(task, index) in pivotTasks" :key="task.id" class="pivot-card"
            :style="{ animationDelay: `${index * 0.15}s` }">
            <div class="card-header">
              <span class="pivot-title">
                {{ task.name }}
                <span v-if="pivotState[task.id]?.active" class="filter-indicator">
                  - 筛选: {{ pivotState[task.id].filterValue }}
                </span>
              </span>
              <div class="header-actions">
                <!-- 筛选模式下的图表切换 -->
                <template v-if="pivotState[task.id]?.active">
                  <el-button-group size="small">
                    <el-button :type="pivotState[task.id].chartType === 'line' ? 'primary' : ''"
                      @click="setPivotChartType(task.id, 'line')">折线</el-button>
                    <el-button :type="pivotState[task.id].chartType === 'bar' ? 'primary' : ''"
                      @click="setPivotChartType(task.id, 'bar')">柱状</el-button>
                    <el-button :type="pivotState[task.id].chartType === 'horizontalBar' ? 'primary' : ''"
                      @click="setPivotChartType(task.id, 'horizontalBar')">条形</el-button>
                  </el-button-group>
                  <el-button size="small" @click="exitPivotFilter(task.id)" style="margin-left: 10px;">
                    返回透视表
                  </el-button>
                </template>
                <template v-else>
                  <el-dropdown v-if="getDatasetData(task).length > 100"
                    @command="(val) => setDisplayLimit(task.id, val)" trigger="click" class="limit-dropdown">
                    <span class="limit-trigger">{{ getDisplayLimit(task.id) === getDatasetData(task).length ? '全部' :
                      getDisplayLimit(task.id) + ' 条' }}<el-icon class="trigger-arrow">
                        <ArrowDown />
                      </el-icon></span>
                    <template #dropdown>
                      <el-dropdown-menu class="limit-dropdown-menu">
                        <el-dropdown-item v-for="opt in getDisplayLimitOptions(task)" :key="opt.value"
                          :command="opt.value" :class="{ 'is-active': getDisplayLimit(task.id) === opt.value }">
                          {{ opt.label }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-tag size="small" effect="dark" round>共 {{ getDatasetData(task).length }} 条</el-tag>
                </template>
              </div>
            </div>

            <!-- 筛选图表模式 -->
            <div v-if="pivotState[task.id]?.active" class="pivot-chart-wrapper">
              <div class="pivot-chart" :id="'pivot-chart-' + task.id"></div>
            </div>

            <!-- 透视表模式 -->
            <div v-else class="pivot-wrapper">
              <!-- 加载中 -->
              <div v-if="isTaskLoading(task.id)" class="table-loading">
                <div class="loading-wave">
                  <span></span><span></span><span></span><span></span><span></span>
                </div>
                <p class="loading-text">数据加载中...</p>
              </div>
              <template v-else>
                <el-alert type="info" :closable="false" style="margin-bottom: 8px;">
                  💡 点击行/列标题可筛选并切换图表展示
                </el-alert>
                <div class="pivot-table-container">
                  <table class="pivot-table">
                    <thead>
                      <tr>
                        <th class="corner-cell">
                          {{ getFieldAlias(task, getChartConfig(task).pivotRowField) }} \\ {{ getFieldAlias(task,
                            getChartConfig(task).pivotColField) }}
                        </th>
                        <th v-for="col in getPivotData(task).cols" :key="col" class="col-header clickable"
                          @click="enterPivotFilter(task, 'col', col)" :title="'点击筛选: ' + col">
                          {{ col }}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in getPivotData(task).rows" :key="row">
                        <td class="row-header clickable" @click="enterPivotFilter(task, 'row', row)"
                          :title="'点击筛选: ' + row">
                          {{ row }}
                        </td>
                        <td v-for="col in getPivotData(task).cols" :key="col" class="value-cell">
                          {{ formatPivotValue(getPivotData(task).matrix[row]?.[col]) }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </template>
            </div>
          </div>
        </div>
      </section>

      <!-- Empty State -->
      <div v-if="activeTasks.length === 0" class="empty-state">
        <el-icon class="empty-icon">
          <Warning />
        </el-icon>
        <p>暂无运行中的监控任务</p>
        <span>请前往任务配置页面添加监控任务</span>
      </div>
    </main>

    <!-- Footer -->
    <footer class="dashboard-footer">
      <div class="footer-stats">
        <span>监控任务: <strong>{{ activeTasks.length }}</strong></span>
        <span class="divider">|</span>
        <span>智能刷新: <strong>根据cron配置</strong></span>
      </div>
      <div class="footer-brand">Powered by Business Monitor System</div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, onUnmounted } from 'vue'
import request from '../api/request'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { Monitor, FullScreen, TrendCharts, DataAnalysis, List, Warning, ArrowDown } from '@element-plus/icons-vue'

const activeTasks = ref([])
const chartInstances = {}
const latestValues = ref({})
const datasetCache = ref({})
const pivotDataCache = ref({})  // 透视表计算结果缓存
const isFullscreen = ref(false)
const currentTime = ref(dayjs().format('HH:mm:ss'))
const currentDate = ref(dayjs().format('YYYY年MM月DD日'))
let clockTimer = null
const taskTimers = {}  // 每个任务的独立刷新定时器
const taskLoadingState = ref({})  // 任务加载状态

// 检查任务是否正在加载
const isTaskLoading = (taskId) => {
  return taskLoadingState.value[taskId] === true
}

// 本地缓存键名
const CACHE_KEY = 'dashboard_task_cache'
const CACHE_EXPIRY_KEY = 'dashboard_cache_expiry'

/**
 * 保存任务数据到本地缓存
 */
const saveCacheToLocal = (taskId, data, type) => {
  try {
    const cache = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}')
    cache[taskId] = { data, type, savedAt: Date.now() }
    localStorage.setItem(CACHE_KEY, JSON.stringify(cache))
  } catch (e) {
    console.warn('保存缓存失败:', e)
  }
}

/**
 * 保存下次刷新时间
 */
const saveNextRefreshTime = (taskId, nextRefreshMs) => {
  try {
    const expiry = JSON.parse(localStorage.getItem(CACHE_EXPIRY_KEY) || '{}')
    expiry[taskId] = Date.now() + nextRefreshMs
    localStorage.setItem(CACHE_EXPIRY_KEY, JSON.stringify(expiry))
  } catch (e) {
    console.warn('保存刷新时间失败:', e)
  }
}

/**
 * 检查缓存是否过期
 * @returns {object|null} 未过期返回缓存数据，否则返回null
 */
const getCacheIfValid = (taskId) => {
  try {
    const expiryStr = localStorage.getItem(CACHE_EXPIRY_KEY)
    const cacheStr = localStorage.getItem(CACHE_KEY)
    if (!expiryStr || !cacheStr) return null

    const expiry = JSON.parse(expiryStr)
    if (!expiry[taskId] || Date.now() >= expiry[taskId]) return null

    const cache = JSON.parse(cacheStr)
    if (cache[taskId]) {
      console.log(`[Dashboard] Task ${taskId} 使用本地缓存`)
      return cache[taskId]
    }
  } catch (e) {
    console.warn('读取缓存失败:', e)
  }
  return null
}

// 分组展示状态
const groupingState = ref({})

// 透视表状态管理
const pivotState = ref({})
// pivotState[taskId] = {
//   filterType: 'row' | 'col' | null,
//   filterValue: string,
//   chartType: 'line' | 'bar' | 'horizontalBar',
//   active: boolean,
//   filteredData: []
// }
const pivotChartInstances = {}
const groupChartInstances = {}

// 进入分组模式
const enterGroupingMode = async (task, groupField) => {
  const taskId = task.id
  // 获取数值字段（假设第一个数字类型的字段）
  const data = datasetCache.value[taskId] || []
  let valueField = null
  if (data.length > 0) {
    const firstRow = data[0]
    for (const [key, value] of Object.entries(firstRow)) {
      if (key !== groupField && typeof value === 'number') {
        valueField = key
        break
      }
    }
  }
  if (!valueField) {
    // 如果没有数值字段，用 COUNT
    valueField = groupField
  }

  groupingState.value[taskId] = {
    active: true,
    groupField,
    valueField,
    chartType: 'bar',
    data: []
  }

  // 获取分组数据
  try {
    const groupedData = await request.get(`/stats/task/${taskId}/grouped-data`, {
      params: { groupBy: groupField, valueField, aggMethod: 'SUM' }
    })
    groupingState.value[taskId].data = groupedData
    nextTick(() => renderGroupChart(taskId))
  } catch (e) {
    console.error('Failed to load grouped data:', e)
    // 降级：使用本地数据进行简单分组
    const localGrouped = groupLocalData(data, groupField, valueField)
    groupingState.value[taskId].data = localGrouped
    nextTick(() => renderGroupChart(taskId))
  }
}

// 本地分组（降级方案）
const groupLocalData = (data, groupField, valueField) => {
  const groups = {}
  data.forEach(row => {
    const key = row[groupField]
    if (!groups[key]) groups[key] = 0
    groups[key] += (typeof row[valueField] === 'number' ? row[valueField] : 1)
  })
  return Object.entries(groups).map(([group_key, agg_value]) => ({
    execution_time: dayjs().format('YYYY-MM-DD HH:mm'),
    group_key,
    agg_value
  }))
}

// 退出分组模式
const exitGroupingMode = (taskId) => {
  if (groupChartInstances[taskId]) {
    groupChartInstances[taskId].dispose()
    delete groupChartInstances[taskId]
  }
  delete groupingState.value[taskId]
}

// 设置图表类型
const setGroupChartType = (taskId, chartType) => {
  if (groupingState.value[taskId]) {
    groupingState.value[taskId].chartType = chartType
    nextTick(() => renderGroupChart(taskId))
  }
}

// 渲染分组图表
const renderGroupChart = (taskId) => {
  const state = groupingState.value[taskId]
  if (!state || !state.active) return

  const container = document.getElementById('group-chart-' + taskId)
  if (!container) return

  if (groupChartInstances[taskId]) {
    groupChartInstances[taskId].dispose()
  }

  const chart = echarts.init(container)
  groupChartInstances[taskId] = chart

  const data = state.data
  const chartType = state.chartType

  // 处理数据：按 group_key 分组，时间作为 X 轴
  const timeSet = [...new Set(data.map(d => d.execution_time))].sort()
  const groupSet = [...new Set(data.map(d => d.group_key))]

  const series = groupSet.map(groupKey => {
    const seriesData = timeSet.map(time => {
      const item = data.find(d => d.execution_time === time && d.group_key === groupKey)
      return item ? item.agg_value : 0
    })
    return {
      name: groupKey,
      type: chartType === 'horizontalBar' ? 'bar' : chartType,
      data: seriesData,
      smooth: chartType === 'line'
    }
  })

  const isHorizontal = chartType === 'horizontalBar'

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: chartType === 'line' ? 'line' : 'shadow' }
    },
    legend: {
      data: groupSet,
      textStyle: { color: '#7eb8da' },
      top: 0
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 40, containLabel: true },
    xAxis: {
      type: isHorizontal ? 'value' : 'category',
      data: isHorizontal ? undefined : timeSet,
      axisLine: { lineStyle: { color: '#405070' } },
      axisLabel: { color: '#7eb8da', rotate: isHorizontal ? 0 : 30 }
    },
    yAxis: {
      type: isHorizontal ? 'category' : 'value',
      data: isHorizontal ? timeSet : undefined,
      axisLine: { lineStyle: { color: '#405070' } },
      axisLabel: { color: '#7eb8da' },
      splitLine: { lineStyle: { color: 'rgba(64, 80, 112, 0.3)' } }
    },
    series
  }

  chart.setOption(option)
}

// Computed Grouping
const scalarTasks = computed(() => activeTasks.value.filter(t => t.resultType === 'SCALAR'))
const chartTasks = computed(() => activeTasks.value.filter(t => t.resultType === 'DATASET' && !['table', 'pivot'].includes(getChartType(t))))
const tableTasks = computed(() => activeTasks.value.filter(t => t.resultType === 'DATASET' && getChartType(t) === 'table'))
const pivotTasks = computed(() => activeTasks.value.filter(t => t.resultType === 'DATASET' && getChartType(t) === 'pivot'))

// 生成透视表数据
const getPivotData = (task) => {
  const taskId = task.id

  // 检查缓存是否有效（基于datasetCache的版本和displayLimit）
  const limit = getDisplayLimit(taskId)
  const currentDataHash = JSON.stringify({ data: datasetCache.value[taskId]?.slice(0, 5), limit }) // hash包含limit
  if (pivotDataCache.value[taskId]?.hash === currentDataHash && pivotDataCache.value[taskId]?.data) {
    return pivotDataCache.value[taskId].data
  }

  const cfg = getChartConfig(task)
  let data = getDisplayedData(task)  // 使用限制后的数据

  // 过滤掉占位符数据（如 {info: "Data too large..."}）
  data = data.filter(row => !row.info)

  if (!data.length) {
    const emptyResult = { rows: [], cols: [], matrix: {} }
    pivotDataCache.value[taskId] = { hash: currentDataHash, data: emptyResult }
    return emptyResult
  }
  if (!cfg.pivotRowField || !cfg.pivotValueField) {
    const emptyResult = { rows: [], cols: [], matrix: {} }
    pivotDataCache.value[taskId] = { hash: currentDataHash, data: emptyResult }
    return emptyResult
  }

  const rowField = cfg.pivotRowField
  let colField = cfg.pivotColField || 'execution_time'
  const valueField = cfg.pivotValueField
  const aggMethod = cfg.pivotAggMethod || 'SUM'

  // 检查数据中是否存在次分组字段，如果不存在则使用 "汇总" 作为默认列
  const sampleRow = data[0]
  const hasColField = sampleRow && (colField in sampleRow)
  if (!hasColField) {
    colField = null // 标记为无列字段
  }

  const rowSet = new Set()
  const colSet = new Set()
  const matrix = {}  // matrix[row][col] = value
  const counts = {}  // for AVG

  data.forEach(row => {
    const rowKey = String(row[rowField] || '未知')
    const colKey = colField ? String(row[colField] || '未知') : '汇总'
    const val = parseFloat(row[valueField]) || 0

    rowSet.add(rowKey)
    colSet.add(colKey)

    if (!matrix[rowKey]) matrix[rowKey] = {}
    if (!counts[rowKey]) counts[rowKey] = {}

    if (aggMethod === 'COUNT') {
      matrix[rowKey][colKey] = (matrix[rowKey][colKey] || 0) + 1
    } else if (aggMethod === 'SUM') {
      matrix[rowKey][colKey] = (matrix[rowKey][colKey] || 0) + val
    } else if (aggMethod === 'AVG') {
      matrix[rowKey][colKey] = (matrix[rowKey][colKey] || 0) + val
      counts[rowKey][colKey] = (counts[rowKey][colKey] || 0) + 1
    }
  })

  // Calculate AVG
  if (aggMethod === 'AVG') {
    for (const r in matrix) {
      for (const c in matrix[r]) {
        if (counts[r][c]) matrix[r][c] = matrix[r][c] / counts[r][c]
      }
    }
  }

  const result = {
    rows: Array.from(rowSet).sort(),
    cols: Array.from(colSet).sort(),
    matrix
  }

  // 缓存结果
  pivotDataCache.value[taskId] = { hash: currentDataHash, data: result }
  return result
}

// 点击透视表单元格/标题进入筛选模式
const enterPivotFilter = (task, filterType, filterValue) => {
  const taskId = task.id
  const cfg = getChartConfig(task)
  const data = getDatasetData(task)

  const rowField = cfg.pivotRowField
  const colField = cfg.pivotColField || 'execution_time'
  const valueField = cfg.pivotValueField

  // 根据筛选条件过滤数据
  let filteredData
  if (filterType === 'row') {
    filteredData = data.filter(d => String(d[rowField]) === filterValue)
  } else {
    filteredData = data.filter(d => String(d[colField]) === filterValue)
  }

  pivotState.value[taskId] = {
    active: true,
    filterType,
    filterValue,
    chartType: 'bar',
    filteredData,
    groupField: filterType === 'row' ? colField : rowField,
    valueField
  }

  nextTick(() => renderPivotFilterChart(taskId))
}

// 退出筛选模式
const exitPivotFilter = (taskId) => {
  if (pivotChartInstances[taskId]) {
    pivotChartInstances[taskId].dispose()
    delete pivotChartInstances[taskId]
  }
  pivotState.value[taskId] = { active: false }
}

// 切换筛选图表类型
const setPivotChartType = (taskId, chartType) => {
  if (pivotState.value[taskId]) {
    pivotState.value[taskId].chartType = chartType
    nextTick(() => renderPivotFilterChart(taskId))
  }
}

// 渲染筛选后的图表
const renderPivotFilterChart = (taskId) => {
  const state = pivotState.value[taskId]
  if (!state || !state.active) return

  const container = document.getElementById('pivot-chart-' + taskId)
  if (!container) return

  if (pivotChartInstances[taskId]) {
    pivotChartInstances[taskId].dispose()
  }

  const chart = echarts.init(container)
  pivotChartInstances[taskId] = chart

  const { filteredData, groupField, valueField, chartType } = state

  // 按 groupField 聚合
  const groups = {}
  filteredData.forEach(row => {
    const key = String(row[groupField] || '未知')
    groups[key] = (groups[key] || 0) + (parseFloat(row[valueField]) || 0)
  })

  const xData = Object.keys(groups)
  const yData = Object.values(groups)

  const isHorizontal = chartType === 'horizontalBar'

  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 30, containLabel: true },
    xAxis: {
      type: isHorizontal ? 'value' : 'category',
      data: isHorizontal ? undefined : xData,
      axisLine: { lineStyle: { color: '#405070' } },
      axisLabel: { color: '#7eb8da', rotate: isHorizontal ? 0 : 30 }
    },
    yAxis: {
      type: isHorizontal ? 'category' : 'value',
      data: isHorizontal ? xData : undefined,
      axisLine: { lineStyle: { color: '#405070' } },
      axisLabel: { color: '#7eb8da' },
      splitLine: { lineStyle: { color: 'rgba(64, 80, 112, 0.3)' } }
    },
    series: [{
      type: chartType === 'line' ? 'line' : 'bar',
      data: yData,
      itemStyle: { color: '#00d4ff' },
      smooth: chartType === 'line'
    }]
  }

  chart.setOption(option)
}

const getLatestValue = (task) => latestValues.value[task.id] ?? 0
const getDatasetData = (task) => datasetCache.value[task.id] || []

// 每个任务的显示条数限制
const displayLimit = ref({})

// 获取任务的显示条数限制
const getDisplayLimit = (taskId) => {
  return displayLimit.value[taskId] || 100  // 默认显示100条
}

// 设置任务的显示条数
const setDisplayLimit = (taskId, limit) => {
  displayLimit.value[taskId] = limit
}

// 动态计算显示条数选项
const getDisplayLimitOptions = (task) => {
  const total = getDatasetData(task).length
  if (total <= 100) {
    return [{ value: total, label: `全部 (${total})` }]
  } else if (total <= 1000) {
    const options = [
      { value: 50, label: '50 条' },
      { value: 100, label: '100 条' },
      { value: 200, label: '200 条' },
      { value: 500, label: '500 条' }
    ].filter(o => o.value <= total)
    options.push({ value: total, label: `全部 (${total})` })
    return options
  } else {
    // 超过1000条，动态计算
    const step = Math.ceil(total / 10 / 100) * 100  // 每步约为总量的1/10，向上取整到100
    const options = []
    for (let i = step; i < total; i += step) {
      options.push({ value: i, label: `${i} 条` })
    }
    // 先截取再添加全部选项，确保全部选项始终存在
    const limitedOptions = options.slice(0, 7)  // 最多7个条数选项
    limitedOptions.push({ value: total, label: `全部 (${total})` })
    return limitedOptions
  }
}

// 获取限制后的显示数据
const getDisplayedData = (task) => {
  const allData = getDatasetData(task)
  const limit = getDisplayLimit(task.id)
  return allData.slice(0, limit)
}

const formatValue = (val) => {
  if (typeof val === 'number') {
    if (val >= 10000) return (val / 10000).toFixed(1) + '万'
    if (val >= 1000) return val.toLocaleString()
    return val
  }
  return val
}

const formatPivotValue = (val) => {
  if (val === undefined || val === null) return '-'
  if (typeof val === 'number') {
    return val >= 1000 ? val.toLocaleString() : (Number.isInteger(val) ? val : val.toFixed(2))
  }
  return val
}

const getValueClass = (task) => {
  const val = getLatestValue(task)
  if (val > 1000) return 'high'
  if (val > 100) return 'medium'
  return 'normal'
}

const getUnit = (task) => {
  // Can be extended based on task config
  return ''
}

/**
 * 解析cron表达式获取刷新间隔（毫秒）
 * 规则：
 * 1. cron间隔 ≤ 30分钟：按cron间隔刷新
 * 2. cron间隔 > 30分钟：计算下次执行时间 + 30分钟后刷新
 * 3. 无配置：默认30分钟刷新
 */
const getCronRefreshInfo = (cron) => {
  const DEFAULT_INTERVAL = 30 * 60 * 1000  // 30分钟

  if (!cron || cron.trim() === '') {
    return { interval: DEFAULT_INTERVAL, nextRefresh: null }
  }

  const parts = cron.trim().split(/\s+/)
  if (parts.length < 5) {
    return { interval: DEFAULT_INTERVAL, nextRefresh: null }
  }

  const [minute, hour, dayOfMonth, month, dayOfWeek] = parts
  let intervalMinutes = null

  // 解析分钟字段
  if (minute.startsWith('*/')) {
    // */N 格式：每N分钟
    intervalMinutes = parseInt(minute.slice(2))
  } else if (minute === '*' && hour === '*') {
    // * * * * * ：每分钟
    intervalMinutes = 1
  } else if (/^\d+$/.test(minute) && hour === '*') {
    // N * * * * ：每小时的第N分钟
    intervalMinutes = 60
  } else if (/^\d+$/.test(minute) && /^\d+$/.test(hour)) {
    // N M * * * ：每天特定时间，计算到下次执行的时间
    const now = dayjs()
    const targetMinute = parseInt(minute)
    const targetHour = parseInt(hour)
    let nextRun = now.hour(targetHour).minute(targetMinute).second(0)

    if (nextRun.isBefore(now)) {
      nextRun = nextRun.add(1, 'day')
    }

    // 执行时间 + 30分钟后刷新
    const refreshTime = nextRun.add(30, 'minute')
    const msUntilRefresh = refreshTime.diff(now)

    return { interval: null, nextRefresh: msUntilRefresh > 0 ? msUntilRefresh : DEFAULT_INTERVAL }
  }

  // 如果解析到了间隔
  if (intervalMinutes !== null) {
    if (intervalMinutes <= 30) {
      // 小于等于30分钟，按cron间隔刷新
      return { interval: intervalMinutes * 60 * 1000, nextRefresh: null }
    } else {
      // 大于30分钟，计算下次执行时间 + 30分钟
      const now = dayjs()
      const currentMinute = now.minute()
      const nextRunMinute = Math.ceil(currentMinute / intervalMinutes) * intervalMinutes
      let nextRun = now.minute(nextRunMinute).second(0)
      if (nextRun.isBefore(now) || nextRun.minute() >= 60) {
        nextRun = now.add(1, 'hour').minute(0).second(0)
      }
      const refreshTime = nextRun.add(30, 'minute')
      const msUntilRefresh = refreshTime.diff(now)
      return { interval: null, nextRefresh: msUntilRefresh > 0 ? msUntilRefresh : DEFAULT_INTERVAL }
    }
  }

  return { interval: DEFAULT_INTERVAL, nextRefresh: null }
}

/**
 * 启动任务独立刷新定时器
 */
const startTaskTimer = (task) => {
  // 清除旧定时器
  if (taskTimers[task.id]) {
    clearTimeout(taskTimers[task.id])
  }

  const { interval, nextRefresh } = getCronRefreshInfo(task.cron)
  const refreshMs = nextRefresh || interval

  // 保存下次刷新时间到本地
  saveNextRefreshTime(task.id, refreshMs)

  console.log(`[Dashboard] Task ${task.name} (${task.id}) 刷新间隔: ${Math.round(refreshMs / 1000)}秒`)

  const scheduleRefresh = () => {
    taskTimers[task.id] = setTimeout(() => {
      loadTaskStats(task, true)  // 定时刷新时强制请求
      saveNextRefreshTime(task.id, refreshMs)  // 更新下次刷新时间
      scheduleRefresh()  // 递归调度下一次
    }, refreshMs)
  }

  scheduleRefresh()
}

const loadTasks = async () => {
  try {
    const tasks = await request.get('/task')
    activeTasks.value = tasks.filter(t => t.isActive)

    // 清除所有旧定时器
    Object.keys(taskTimers).forEach(id => {
      clearTimeout(taskTimers[id])
      delete taskTimers[id]
    })

    // 初始化所有任务的loading状态
    activeTasks.value.forEach(task => {
      taskLoadingState.value[task.id] = true
    })

    // 分批加载任务数据，避免主线程阻塞
    let index = 0
    const loadNext = () => {
      if (index >= activeTasks.value.length) return
      const task = activeTasks.value[index]
      loadTaskStats(task, false)  // 优先用缓存
      startTaskTimer(task)  // 启动定时器
      index++
      if (index < activeTasks.value.length) {
        requestAnimationFrame(loadNext)
      }
    }
    requestAnimationFrame(loadNext)
  } catch (e) { console.error(e) }
}

const getChartConfig = (task) => {
  try {
    return JSON.parse(task.chartConfig || '{}')
  } catch (e) { return {} }
}

const getChartType = (task) => getChartConfig(task).type || 'line'
const getTableColumns = (task) => getChartConfig(task).columns || []
const getFieldAlias = (task, field) => {
  const cfg = getChartConfig(task)
  // 用户定义的别名优先
  if (cfg.fieldAlias && cfg.fieldAlias[field]) {
    return cfg.fieldAlias[field]
  }
  // 默认别名映射
  const defaultAlias = {
    'execution_time': '执行时间',
    'execution_id': '执行ID',
    'create_time': '创建时间',
    'update_time': '更新时间'
  }
  return defaultAlias[field] || field
}

const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
  if (isFullscreen.value) {
    document.documentElement.requestFullscreen().catch(() => { })
  } else {
    document.exitFullscreen().catch(() => { })
  }
  // Resize charts after fullscreen toggle
  setTimeout(() => {
    Object.values(chartInstances).forEach(chart => chart?.resize())
  }, 300)
}

// 强制刷新单个任务
const forceRefreshTask = (task) => {
  console.log(`[Dashboard] 强制刷新任务: ${task.name}`)
  // 清除该任务的透视表缓存
  delete pivotDataCache.value[task.id]
  // 强制重新加载
  loadTaskStats(task, true)
}

// 强制刷新所有任务
const forceRefreshAllTasks = () => {
  console.log('[Dashboard] 强制刷新所有任务')
  // 清除所有透视表缓存
  pivotDataCache.value = {}
  // 强制刷新每个任务
  activeTasks.value.forEach(task => {
    loadTaskStats(task, true)
  })
}

/**
 * 加载任务数据（支持本地缓存）
 * @param task 任务对象
 * @param forceRefresh 是否强制刷新（跳过缓存）
 */
const loadTaskStats = async (task, forceRefresh = false) => {
  // 检查本地缓存（非强制刷新时）
  if (!forceRefresh) {
    const cached = getCacheIfValid(task.id)
    if (cached) {
      taskLoadingState.value[task.id] = false
      if (cached.type === 'dataset') {
        datasetCache.value[task.id] = cached.data
        const chartType = getChartType(task)
        if (chartType !== 'table' && chartType !== 'pivot') {
          nextTick(() => renderChart(task, cached.data))
        }
      } else if (cached.type === 'scalar') {
        latestValues.value[task.id] = cached.data.value
        if (cached.data.trend) {
          nextTick(() => renderMiniChart(task, cached.data.trend))
        }
      }
      return  // 使用缓存，不请求服务器
    }
  }

  // 设置加载中状态
  taskLoadingState.value[task.id] = true

  try {
    const res = await request.get(`/stats/${task.id}`)
    if (res && res.length > 0) {
      const latest = res[0]
      if (task.resultType === 'DATASET') {
        const chartType = getChartType(task)

        // 透视表类型：从动态表获取带 execution_time 的历史数据
        if (chartType === 'pivot') {
          try {
            const pivotData = await request.get(`/stats/task/${task.id}/pivot-data`, {})
            console.debug('Pivot data loaded:', task.id, pivotData?.length)
            datasetCache.value[task.id] = pivotData || []
            saveCacheToLocal(task.id, pivotData || [], 'dataset')
          } catch (e) {
            console.error('Pivot data error:', e)
            const data = JSON.parse(latest.resultJson || '[]')
            datasetCache.value[task.id] = data
            saveCacheToLocal(task.id, data, 'dataset')
          }
        } else {
          const data = JSON.parse(latest.resultJson || '[]')
          datasetCache.value[task.id] = data
          saveCacheToLocal(task.id, data, 'dataset')
          if (chartType !== 'table') {
            nextTick(() => renderChart(task, data))
          }
        }
      } else {
        latestValues.value[task.id] = latest.resultNumber
        const trendData = [...res].reverse().map(r => ({
          time: r.executionTime,
          value: r.resultNumber
        }))
        saveCacheToLocal(task.id, { value: latest.resultNumber, trend: trendData }, 'scalar')
        nextTick(() => renderMiniChart(task, trendData))
      }
    }
  } catch (e) { console.error(e) } finally {
    taskLoadingState.value[task.id] = false
  }
}

const renderChart = (task, data) => {
  const dom = document.getElementById('chart-' + task.id)
  if (!dom) return

  if (chartInstances[task.id]) chartInstances[task.id].dispose()
  const chart = echarts.init(dom)
  const cfg = getChartConfig(task)

  const xLabel = getFieldAlias(task, cfg.xAxis)
  const yLabel = getFieldAlias(task, cfg.yAxis)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0,20,40,0.9)',
      borderColor: '#00d4ff',
      borderWidth: 1,
      textStyle: { color: '#fff' }
    },
    grid: { top: 50, bottom: 30, left: 60, right: 30 },
    xAxis: {
      type: 'category',
      data: data.map(item => item[cfg.xAxis]),
      name: xLabel,
      nameTextStyle: { color: '#7eb8da' },
      axisLine: { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#7eb8da' },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      name: yLabel,
      nameTextStyle: { color: '#7eb8da' },
      splitLine: { lineStyle: { color: 'rgba(30,80,120,0.3)' } },
      axisLine: { show: false },
      axisLabel: { color: '#7eb8da' }
    },
    series: [{
      name: yLabel,
      data: data.map(item => item[cfg.yAxis]),
      type: cfg.type || 'bar',
      barMaxWidth: 35,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#00f2fe' },
          { offset: 0.5, color: '#4facfe' },
          { offset: 1, color: '#00d4ff' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      emphasis: {
        itemStyle: {
          shadowBlur: 20,
          shadowColor: 'rgba(0,212,255,0.5)'
        }
      }
    }]
  }

  if (cfg.type === 'line') {
    option.series[0].smooth = true
    option.series[0].lineStyle = { width: 3, color: '#00d4ff' }
    option.series[0].areaStyle = {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(0,212,255,0.4)' },
        { offset: 1, color: 'rgba(0,212,255,0.05)' }
      ])
    }
    option.series[0].symbol = 'circle'
    option.series[0].symbolSize = 8
    option.series[0].itemStyle = { color: '#00d4ff', borderColor: '#fff', borderWidth: 2 }
  }

  if (cfg.type === 'pie') {
    option.xAxis = undefined
    option.yAxis = undefined
    option.grid = undefined
    option.tooltip = { trigger: 'item' }
    option.series = [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '50%'],
      data: data.map((item, i) => ({
        name: item[cfg.xAxis],
        value: item[cfg.yAxis],
        itemStyle: { color: ['#00d4ff', '#4facfe', '#00f2fe', '#43e97b', '#38f9d7'][i % 5] }
      })),
      label: { color: '#fff', fontSize: 12 },
      labelLine: { lineStyle: { color: '#4a6fa5' } },
      emphasis: {
        scale: true,
        scaleSize: 10
      }
    }]
  }

  chart.setOption(option)
  chartInstances[task.id] = chart
}

const renderMiniChart = (task, trendData) => {
  const dom = document.getElementById('chart-' + task.id)
  if (!dom) return
  if (chartInstances[task.id]) chartInstances[task.id].dispose()
  const chart = echarts.init(dom)

  const option = {
    backgroundColor: 'transparent',
    grid: { top: 5, bottom: 5, left: 5, right: 5 },
    xAxis: { type: 'category', data: trendData.map(d => d.time), show: false, boundaryGap: false },
    yAxis: { type: 'value', show: false },
    series: [{
      data: trendData.map(d => d.value),
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2, color: '#00f2fe' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,242,254,0.35)' },
          { offset: 1, color: 'rgba(0,242,254,0.05)' }
        ])
      }
    }]
  }
  chart.setOption(option)
  chartInstances[task.id] = chart
}

let resizeHandler = null

onMounted(() => {
  loadTasks()  // 立即加载（启动或重启后）

  // 时钟定时器
  clockTimer = setInterval(() => {
    currentTime.value = dayjs().format('HH:mm:ss')
    currentDate.value = dayjs().format('YYYY年MM月DD日')
  }, 1000)

  // Handle window resize with debounce
  let resizeTimeout = null
  resizeHandler = () => {
    if (resizeTimeout) clearTimeout(resizeTimeout)
    resizeTimeout = setTimeout(() => {
      Object.values(chartInstances).forEach(chart => chart?.resize())
    }, 200)
  }
  window.addEventListener('resize', resizeHandler)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  // 清除所有任务定时器
  Object.values(taskTimers).forEach(t => clearTimeout(t))
  // 移除resize事件监听
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  // 销毁所有图表实例
  Object.values(chartInstances).forEach(chart => chart?.dispose())
})
</script>

<style scoped>
/* CSS Variables */
:root {
  --primary-gradient: linear-gradient(135deg, #00d4ff 0%, #4facfe 50%, #00f2fe 100%);
  --dark-bg: #030b15;
  --card-bg: rgba(10, 30, 50, 0.85);
  --border-color: rgba(0, 212, 255, 0.2);
  --text-primary: #ffffff;
  --text-secondary: #7eb8da;
  --accent: #00d4ff;
}

/* Keyframes */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }
}

@keyframes loadingWave {

  0%,
  40%,
  100% {
    transform: scaleY(0.4);
  }

  20% {
    transform: scaleY(1);
  }
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

@keyframes glow {

  0%,
  100% {
    box-shadow: 0 0 15px rgba(0, 212, 255, 0.3);
  }

  50% {
    box-shadow: 0 0 30px rgba(0, 212, 255, 0.6);
  }
}

@keyframes borderFlow {
  0% {
    background-position: 0% 50%;
  }

  50% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0% 50%;
  }
}

/* Container */
.dashboard-container {
  min-height: 100vh;
  background: var(--theme-bg);
  color: var(--theme-text);
  display: flex;
  flex-direction: column;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  overflow-x: hidden;
  transition: background 0.3s ease;
}

.fullscreen-mode {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
}

/* Header */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 30px;
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

.logo-wrapper {
  position: relative;
  width: 45px;
  height: 45px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-pulse {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(0, 212, 255, 0.3) 0%, transparent 70%);
  animation: pulse 2s ease-in-out infinite;
}

.logo-icon {
  font-size: 28px;
  color: #00f2fe;
  filter: drop-shadow(0 0 10px rgba(0, 212, 255, 0.5));
  position: relative;
  z-index: 1;
}

.title-group {
  display: flex;
  flex-direction: column;
}

.main-title {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(90deg, #00f2fe, #4facfe, #00d4ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
  letter-spacing: 2px;
}

.sub-title {
  font-size: 10px;
  color: #4a6fa5;
  letter-spacing: 3px;
  margin-top: 2px;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 20px;
}

.decoration-line {
  width: 80px;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.5), transparent);
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: rgba(0, 212, 255, 0.1);
  border: 1px solid rgba(0, 212, 255, 0.3);
  border-radius: 20px;
  font-size: 12px;
  color: #00f2fe;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #00f2fe;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
  box-shadow: 0 0 10px #00f2fe;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.refresh-all-btn,
.fullscreen-btn {
  background: rgba(0, 212, 255, 0.2) !important;
  border: 1px solid rgba(0, 212, 255, 0.4) !important;
  color: #00d4ff !important;
  transition: all 0.3s ease;
}

.refresh-all-btn:hover,
.fullscreen-btn:hover {
  background: rgba(0, 212, 255, 0.4) !important;
  border-color: #00d4ff !important;
  transform: scale(1.1);
}

.refresh-btn {
  cursor: pointer;
  color: #7eb8da;
  font-size: 16px;
  transition: all 0.3s ease;
  margin-right: 8px;
}

.refresh-btn:hover {
  color: #00d4ff;
  transform: rotate(180deg);
}

.metric-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Dropdown 触发器样式 */
.limit-dropdown {
  margin-right: 8px;
}

.limit-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: rgba(0, 212, 255, 0.1);
  border: 1px solid rgba(0, 212, 255, 0.4);
  border-radius: 14px;
  color: #00d4ff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.limit-trigger:hover {
  background: rgba(0, 212, 255, 0.18);
  border-color: rgba(0, 212, 255, 0.6);
  box-shadow: 0 0 10px rgba(0, 212, 255, 0.2);
}

.trigger-arrow {
  font-size: 12px;
  color: rgba(0, 212, 255, 0.7);
  transition: transform 0.2s ease;
}

.time-block {
  text-align: right;
}

.time-date {
  font-size: 12px;
  color: #7eb8da;
}

.time-clock {
  font-size: 24px;
  font-weight: 600;
  font-family: 'Roboto Mono', monospace;
  color: #00f2fe;
  text-shadow: 0 0 15px rgba(0, 212, 255, 0.5);
}

.fullscreen-btn {
  background: rgba(0, 212, 255, 0.1) !important;
  border: 1px solid rgba(0, 212, 255, 0.3) !important;
  color: #00f2fe !important;
  transition: all 0.3s !important;
}

.fullscreen-btn:hover {
  background: rgba(0, 212, 255, 0.2) !important;
  box-shadow: 0 0 15px rgba(0, 212, 255, 0.4) !important;
}

/* Main Content */
.dashboard-main {
  flex: 1;
  padding: 20px 30px;
  overflow-y: auto;
}

/* Section Headers */
.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.section-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  color: var(--theme-accent);
  font-size: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text);
  letter-spacing: 1px;
}

.section-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--el-border-color), transparent);
}

/* Metrics Grid */
.metrics-section {
  margin-bottom: 30px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.metric-card {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

.card-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(ellipse at center, rgba(0, 212, 255, 0.1) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
}

.metric-card:hover .card-glow {
  opacity: 1;
}

.card-border {
  position: absolute;
  inset: 0;
  border-radius: 12px;
  padding: 1px;
  background: linear-gradient(135deg, var(--el-border-color), transparent, var(--el-border-color));
  background-size: 200% 200%;
  animation: borderFlow 3s ease infinite;
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
}

.card-content {
  position: relative;
  background: var(--theme-card-bg);
  border-radius: 12px;
  padding: 20px;
  height: 100%;
  backdrop-filter: blur(10px);
  transition: background 0.3s ease;
}

.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.metric-label {
  font-size: 14px;
  color: var(--theme-text-secondary);
}

.metric-status {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(0, 255, 136, 0.1);
  color: #00ff88;
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.metric-body {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-bottom: 15px;
}

.metric-value {
  font-size: 36px;
  font-weight: 700;
  font-family: 'Roboto Mono', monospace;
  color: var(--theme-accent);
}

.metric-value.normal {
  color: var(--theme-accent);
}

.metric-value.medium {
  color: var(--el-color-primary);
}

.metric-value.high {
  color: #00ff88;
}

.metric-unit {
  font-size: 14px;
  color: var(--theme-text-secondary);
}

.metric-chart {
  height: 50px;
  width: 100%;
}

/* Charts Grid */
.charts-section {
  margin-bottom: 30px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(450px, 1fr));
  gap: 20px;
}

.chart-card {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 20px;
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
  transition: all 0.3s;
}

.chart-card:hover {
  border-color: var(--el-border-color);
  box-shadow: 0 5px 30px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
}

.realtime-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #00ff88;
}

.realtime-badge .dot {
  width: 6px;
  height: 6px;
  background: #00ff88;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

.chart-body {
  height: 280px;
  width: 100%;
}

/* Tables Grid */
.tables-section {
  margin-bottom: 30px;
}

.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(450px, 1fr));
  gap: 20px;
}

.table-card {
  background: var(--theme-card-bg);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 20px;
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
  transition: all 0.3s;
}

.table-card:hover {
  border-color: var(--el-border-color);
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
}

.table-wrapper {
  margin-top: 15px;
}

/* Custom Table Styles */
.custom-table {
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
  --el-table-header-bg-color: var(--el-fill-color) !important;
  --el-table-row-hover-bg-color: var(--el-fill-color-light) !important;
  --el-table-border-color: var(--el-border-color-light) !important;
  --el-table-text-color: var(--el-text-color-regular) !important;
  --el-table-header-text-color: var(--theme-accent) !important;
}

:deep(.el-table) {
  background: transparent !important;
}

:deep(.el-table th.el-table__cell) {
  background: var(--el-fill-color) !important;
  font-weight: 600;
  font-size: 13px;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid var(--el-border-color-lighter) !important;
}

:deep(.el-table--enable-row-hover .el-table__body tr:hover > td.el-table__cell) {
  background: var(--el-fill-color-light) !important;
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: var(--theme-text-secondary);
}

.empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
  margin-bottom: 15px;
}

.empty-state p {
  font-size: 16px;
  margin: 0 0 5px 0;
}

.empty-state span {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

/* Footer */
.dashboard-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 30px;
  background: var(--theme-header-bg);
  border-top: 1px solid var(--theme-border);
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  transition: background 0.3s ease;
}

.footer-stats {
  display: flex;
  gap: 10px;
}

.footer-stats strong {
  color: var(--theme-text-secondary);
}

.footer-stats .divider {
  color: var(--el-border-color);
}

.footer-brand {
  font-style: italic;
}

/* Grouping Mode Styles */
.clickable-header {
  cursor: pointer;
  transition: color 0.2s ease;
}

.clickable-header:hover {
  color: var(--theme-accent);
}

.group-indicator {
  font-size: 12px;
  color: var(--theme-accent);
  font-weight: normal;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.grouping-chart-wrapper {
  padding: 15px;
}

.grouping-chart {
  width: 100%;
  height: 320px;
}

/* Pivot Table Styles */
.pivot-section {
  margin-bottom: 30px;
}

.pivot-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}

.pivot-card {
  background: var(--theme-card-bg);
  border-radius: 16px;
  border: 1px solid var(--theme-border);
  padding: 0;
  animation: fadeInUp 0.6s ease forwards;
  opacity: 0;
  overflow: hidden;
}

.pivot-card .card-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--theme-border);
  background: var(--el-fill-color);
}

.pivot-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text);
}

.filter-indicator {
  font-size: 13px;
  color: var(--theme-accent);
  font-weight: normal;
}

.pivot-wrapper {
  padding: 20px;
}

.pivot-table-container {
  overflow-x: auto;
}

.pivot-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.pivot-table th,
.pivot-table td {
  padding: 10px 15px;
  text-align: center;
  border: 1px solid var(--theme-border);
}

.pivot-table .corner-cell {
  background: var(--el-fill-color);
  color: var(--theme-accent);
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
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

.pivot-table .clickable {
  cursor: pointer;
  transition: all 0.2s ease;
}

.pivot-table .clickable:hover {
  background: var(--el-color-primary-light-9) !important;
  color: var(--theme-accent);
}

.pivot-chart-wrapper {
  padding: 20px;
}

.pivot-chart {
  width: 100%;
  height: 320px;
}

/* Loading 动画样式 */
.chart-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 100px;
}

.loading-wave {
  display: flex;
  align-items: flex-end;
  height: 30px;
  gap: 3px;
}

.loading-wave span {
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #00d4ff, #4facfe);
  border-radius: 2px;
  animation: loadingWave 1s ease-in-out infinite;
}

.loading-wave span:nth-child(1) {
  animation-delay: 0s;
}

.loading-wave span:nth-child(2) {
  animation-delay: 0.1s;
}

.loading-wave span:nth-child(3) {
  animation-delay: 0.2s;
}

.loading-wave span:nth-child(4) {
  animation-delay: 0.3s;
}

.loading-wave span:nth-child(5) {
  animation-delay: 0.4s;
}

.loading-text {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.loading-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 3px solid rgba(0, 212, 255, 0.2);
  border-top-color: #00d4ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.table-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  min-height: 150px;
}
</style>

<!-- 全局样式用于下拉菜单弹出框 -->
<style>
/* Dropdown 弹出菜单样式 - 亮色主题 */
.el-dropdown__popper {
  background: rgba(255, 255, 255, 0.98) !important;
  border: 1px solid rgba(0, 180, 230, 0.35) !important;
  border-radius: 10px !important;
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12), 0 0 10px rgba(0, 180, 230, 0.08);
  padding: 4px 0;
}

.limit-dropdown-menu {
  background: transparent !important;
  border: none !important;
}

.limit-dropdown-menu .el-dropdown-menu__item {
  color: #334455;
  font-size: 13px;
  padding: 8px 16px;
  transition: all 0.15s ease;
}

.limit-dropdown-menu .el-dropdown-menu__item:hover,
.limit-dropdown-menu .el-dropdown-menu__item:focus {
  background: rgba(0, 180, 230, 0.1) !important;
  color: #0099cc;
}

.limit-dropdown-menu .el-dropdown-menu__item.is-active {
  color: #0088bb;
  font-weight: 600;
  background: rgba(0, 180, 230, 0.08);
}

.el-dropdown__popper .el-popper__arrow::before {
  background: rgba(255, 255, 255, 0.98) !important;
  border-color: rgba(0, 180, 230, 0.35) !important;
}
</style>
