/**
 * 枚举转换工具
 * 基于后端 com.monitor.backend.enums 包下的枚举定义
 * 提供统一的枚举中英文转换方法
 */

// ========== 枚举定义 ==========

/**
 * 告警渠道类型
 * @see AlarmChannelType.java
 */
export const AlarmChannelType = {
  EMAIL: '邮件',
  SMS: '短信',
  DINGTALK: '钉钉',
  WECHAT: '微信',
  ANNOUNCEMENT: '公告'
}

/**
 * 告警级别
 * @see AlarmLevel.java
 */
export const AlarmLevel = {
  INFO: '信息',
  WARNING: '警告',
  CRITICAL: '严重'
}

/**
 * 告警状态
 * @see AlarmStatus.java
 */
export const AlarmStatus = {
  FIRING: '触发中',
  ACKNOWLEDGED: '已确认',
  SUPPRESSED: '已抑制',
  RESOLVED: '已恢复'
}

/**
 * 告警触发类型
 * @see AlarmTriggerType.java
 */
export const AlarmTriggerType = {
  ROW_COUNT: '行数统计',
  FIELD_VALUE: '字段值',
  FIELD_AGG: '字段聚合',
  THRESHOLD: '阈值触发',
  YOY: '同比',
  MOM: '环比'
}

/**
 * 比较操作符
 * @see CompareOperator.java
 */
export const CompareOperator = {
  '>': '大于',
  '>=': '大于等于',
  '<': '小于',
  '<=': '小于等于',
  '=': '等于',
  '!=': '不等于',
  'CONTAINS': '包含',
  'NOT_CONTAINS': '不包含'
}

/**
 * 执行状态
 * @see ExecutionStatus.java
 */
export const ExecutionStatus = {
  PENDING: '待执行',
  RUNNING: '执行中',
  STOPPED: '已停止',
  BATCH_STARTING: '批处理启动中',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消'
}

/**
 * SQL JOIN类型
 * @see JoinType.java
 */
export const JoinType = {
  INNER: '内连接',
  LEFT: '左连接',
  RIGHT: '右连接',
  FULL: '全连接'
}

/**
 * 循环源类型
 * @see LoopSourceType.java
 */
export const LoopSourceType = {
  VARIABLE: '变量遍历',
  CONSTANT: '常量分割'
}

/**
 * 监控任务类型
 * @see MonitorTaskType.java
 */
export const MonitorTaskType = {
  MONITOR_TASK: '监控任务',
  SERVER_TASK: '服务器任务',
  WORKFLOW: '工作流任务'
}

/**
 * 工作流步骤类型
 * @see WorkflowStepType.java
 */
export const WorkflowStepType = {
  SQL: 'SQL查询',
  CONSTANT: '常量定义',
  LOOP: '循环执行',
  TASK_REF: '任务引用',
  MEMORY_JOIN: '内存连接',
  OUTPUT: '输出'
}

/**
 * 聚合计算方法
 * @see AggregateMethod.java
 */
export const AggregateMethod = {
  SUM: '求和',
  COUNT: '计数',
  AVG: '平均值',
  MAX: '最大值',
  MIN: '最小值'
}

/**
 * 告警内容格式
 * @see AlarmContentType.java
 */
export const AlarmContentType = {
  TEXT: '纯文本',
  MARKDOWN: 'Markdown'
}

// ========== 枚举键常量（用于代码中替代硬编码字符串）==========

/** 告警渠道类型键 */
export const ChannelTypeKey = {
  EMAIL: 'EMAIL',
  SMS: 'SMS',
  DINGTALK: 'DINGTALK',
  WECHAT: 'WECHAT',
  ANNOUNCEMENT: 'ANNOUNCEMENT'
}

/** 告警级别键 */
export const AlarmLevelKey = {
  INFO: 'INFO',
  WARNING: 'WARNING',
  CRITICAL: 'CRITICAL'
}

/** 告警状态键 */
export const AlarmStatusKey = {
  FIRING: 'FIRING',
  ACKNOWLEDGED: 'ACKNOWLEDGED',
  SUPPRESSED: 'SUPPRESSED',
  RESOLVED: 'RESOLVED'
}

/** 执行状态键 */
export const ExecutionStatusKey = {
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  STOPPED: 'STOPPED',
  BATCH_STARTING: 'BATCH_STARTING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED'
}

/** 内容格式键 */
export const ContentTypeKey = {
  TEXT: 'TEXT',
  MARKDOWN: 'MARKDOWN'
}

// ========== 选项列表（用于下拉选择框）==========

/** 告警渠道类型选项列表 */
export const channelTypeOptions = Object.entries(AlarmChannelType).map(([value, label]) => ({ value, label }))

/** 告警级别选项列表 */
export const alarmLevelOptions = Object.entries(AlarmLevel).map(([value, label]) => ({ value, label }))

/** 告警状态选项列表 */
export const alarmStatusOptions = Object.entries(AlarmStatus).map(([value, label]) => ({ value, label }))

/** 内容格式选项列表 */
export const contentTypeOptions = Object.entries(AlarmContentType).map(([value, label]) => ({ value, label }))

// ========== 枚举映射表 ==========

const ENUM_MAP = {
  AlarmChannelType,
  AlarmLevel,
  AlarmStatus,
  AlarmTriggerType,
  CompareOperator,
  ExecutionStatus,
  JoinType,
  LoopSourceType,
  MonitorTaskType,
  WorkflowStepType,
  AggregateMethod,
  AlarmContentType
}

// ========== 通用转换方法 ==========

/**
 * 通用枚举转换方法
 * @param {string} enumType - 枚举类型名称
 * @param {string} value - 枚举值
 * @param {string} [defaultValue] - 默认值，未找到时返回
 * @returns {string} 中文标签
 */
export function getEnumLabel(enumType, value, defaultValue = value) {
  const enumObj = ENUM_MAP[enumType]
  if (!enumObj) {
    console.warn(`[enums] 未知枚举类型: ${enumType}`)
    return defaultValue
  }
  return enumObj[value] ?? defaultValue
}

/**
 * 获取枚举的选项列表（用于下拉框）
 * @param {string} enumType - 枚举类型名称
 * @returns {Array<{value: string, label: string}>}
 */
export function getEnumOptions(enumType) {
  const enumObj = ENUM_MAP[enumType]
  if (!enumObj) {
    console.warn(`[enums] 未知枚举类型: ${enumType}`)
    return []
  }
  return Object.entries(enumObj).map(([value, label]) => ({ value, label }))
}

// ========== 便捷方法 ==========

/** 获取告警渠道类型名称 */
export const getChannelTypeName = (type) => AlarmChannelType[type] ?? type

/** 获取告警级别名称 */
export const getAlarmLevelName = (level) => AlarmLevel[level] ?? level

/** 获取告警状态名称 */
export const getAlarmStatusName = (status) => AlarmStatus[status] ?? status

/** 获取告警触发类型名称 */
export const getTriggerTypeName = (type) => AlarmTriggerType[type] ?? type

/** 获取比较操作符名称 */
export const getOperatorName = (op) => CompareOperator[op] ?? op

/** 获取执行状态名称 */
export const getExecutionStatusName = (status) => ExecutionStatus[status] ?? status

/** 获取JOIN类型名称 */
export const getJoinTypeName = (type) => JoinType[type] ?? type

/** 获取循环源类型名称 */
export const getLoopSourceTypeName = (type) => LoopSourceType[type] ?? type

/** 获取监控任务类型名称 */
export const getMonitorTaskTypeName = (type) => MonitorTaskType[type] ?? type

/** 获取工作流步骤类型名称 */
export const getWorkflowStepTypeName = (type) => WorkflowStepType[type] ?? type

/** 获取聚合方法名称 */
export const getAggregateMethodName = (method) => AggregateMethod[method] ?? method

/** 获取内容格式名称 */
export const getContentTypeName = (type) => AlarmContentType[type] ?? type

// ========== 样式映射 ==========

/**
 * 获取渠道类型的 Tag 样式
 * @param {string} type
 * @returns {'primary'|'success'|'warning'|'danger'|'info'|''}
 */
export const getChannelTypeTag = (type) => {
  const map = {
    EMAIL: 'primary',
    SMS: 'success',
    DINGTALK: 'warning',
    WECHAT: 'success',
    ANNOUNCEMENT: 'info'
  }
  return map[type] ?? ''
}

/**
 * 获取告警状态的 Tag 样式
 * @param {string} status
 * @returns {'primary'|'success'|'warning'|'danger'|'info'|''}
 */
export const getAlarmStatusTag = (status) => {
  const map = {
    FIRING: 'danger',
    ACKNOWLEDGED: 'warning',
    SUPPRESSED: 'info',
    RESOLVED: 'success'
  }
  return map[status] ?? ''
}

/**
 * 获取告警级别的 Tag 样式
 * @param {string} level
 * @returns {'primary'|'success'|'warning'|'danger'|'info'|''}
 */
export const getAlarmLevelTag = (level) => {
  const map = {
    INFO: 'info',
    WARNING: 'warning',
    CRITICAL: 'danger'
  }
  return map[level] ?? ''
}

/**
 * 获取执行状态的 Tag 样式
 * @param {string} status
 * @returns {'primary'|'success'|'warning'|'danger'|'info'|''}
 */
export const getExecutionStatusTag = (status) => {
  const map = {
    PENDING: 'info',
    RUNNING: 'primary',
    STOPPED: 'warning',
    BATCH_STARTING: 'primary',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] ?? ''
}

// ========== 默认配置常量 ==========

/**
 * 告警通道表单默认值
 */
export const DEFAULT_CHANNEL = {
  id: null,
  name: '',
  type: 'EMAIL',
  isActive: 1,
  config: ''
}

/**
 * 各通道类型的配置默认值
 */
export const DEFAULT_CHANNEL_CONFIG = {
  // 邮件通道
  EMAIL: { host: '', port: 465, username: '', password: '', recipients: '' },
  // 钉钉通道
  DINGTALK: { webhook: '', secret: '' },
  // 企业微信通道
  WECHAT: { webhook: '' },
  // 短信通道
  SMS: { apiUrl: '', phones: '', paramsTemplate: '', signMethod: 'NONE', signKey: '', signFields: [] },
  // 系统公告通道
  ANNOUNCEMENT: { displayDuration: 3600, level: 'warning' }
}

/**
 * 获取指定通道类型的默认配置（返回副本避免污染）
 * @param {string} type 通道类型
 * @returns {Object} 默认配置对象
 */
export const getDefaultChannelConfig = (type) => {
  return { ...DEFAULT_CHANNEL_CONFIG[type] } || {}
}

/**
 * 获取所有通道配置的完整默认值（用于完全重置）
 * @returns {Object}
 */
export const getFullChannelConfigDefaults = () => ({
  // EMAIL
  host: '', port: 465, username: '', password: '', recipients: '',
  // DINGTALK & WECHAT
  webhook: '', secret: '',
  // SMS
  apiUrl: '', phones: '', paramsTemplate: '', signMethod: 'NONE', signKey: '', signFields: [],
  // ANNOUNCEMENT
  displayDuration: 3600, level: 'warning'
})
