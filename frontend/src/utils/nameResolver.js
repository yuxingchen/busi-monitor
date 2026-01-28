/**
 * ID到名称转换服务
 * 提供任务ID、渠道ID、数据源ID等到对应名称的转换
 * 使用缓存机制减少API调用
 */

import request from '../api/request'

// ========== 缓存存储 ==========

const cache = {
  tasks: new Map(),          // 任务ID -> 任务名称
  channels: new Map(),       // 渠道ID -> 渠道名称
  datasources: new Map(),    // 数据源ID -> 数据源名称
  workflows: new Map(),      // 工作流ID -> 工作流名称
  serverGroups: new Map(),   // 服务器分组ID -> 分组名称
  servers: new Map(),        // 服务器ID -> 服务器名称
  templates: new Map()       // 模板ID -> 模板名称
}

// 标记是否已预加载
const preloaded = {
  tasks: false,
  channels: false,
  datasources: false,
  workflows: false,
  serverGroups: false,
  servers: false,
  templates: false
}

// ========== 缓存管理 ==========

/**
 * 清除指定类型的缓存
 * @param {keyof cache} entityType - 实体类型
 */
export function clearCache(entityType) {
  if (cache[entityType]) {
    cache[entityType].clear()
    preloaded[entityType] = false
  }
}

/**
 * 清除所有缓存
 */
export function clearAllCache() {
  Object.keys(cache).forEach(key => {
    cache[key].clear()
    preloaded[key] = false
  })
}

/**
 * 更新单条缓存
 * @param {keyof cache} entityType
 * @param {number|string} id
 * @param {string} name
 */
export function updateCache(entityType, id, name) {
  if (cache[entityType]) {
    cache[entityType].set(String(id), name)
  }
}

/**
 * 删除单条缓存
 * @param {keyof cache} entityType
 * @param {number|string} id
 */
export function removeFromCache(entityType, id) {
  if (cache[entityType]) {
    cache[entityType].delete(String(id))
  }
}

// ========== 预加载方法 ==========

/**
 * 预加载指定类型的所有数据到缓存
 * @param {keyof cache} entityType
 */
export async function preloadNames(entityType) {
  if (preloaded[entityType]) return
  
  try {
    let list = []
    switch (entityType) {
      case 'tasks':
        list = await request.get('/task')
        list.forEach(item => cache.tasks.set(String(item.id), item.name))
        break
      case 'channels':
        list = await request.get('/alarm/channel')
        list.forEach(item => cache.channels.set(String(item.id), item.name))
        break
      case 'datasources':
        list = await request.get('/datasource')
        list.forEach(item => cache.datasources.set(String(item.id), item.name))
        break
      case 'workflows':
        list = await request.get('/workflow')
        list.forEach(item => cache.workflows.set(String(item.id), item.name))
        break
      case 'serverGroups':
        list = await request.get('/server-group')
        list.forEach(item => cache.serverGroups.set(String(item.id), item.name))
        break
      case 'servers':
        list = await request.get('/server-asset')
        list.forEach(item => cache.servers.set(String(item.id), item.name))
        break
      case 'templates':
        list = await request.get('/alarm/template')
        list.forEach(item => cache.templates.set(String(item.id), item.name))
        break
    }
    preloaded[entityType] = true
  } catch (e) {
    console.error(`[nameResolver] 预加载 ${entityType} 失败:`, e)
  }
}

// ========== 名称获取方法 ==========

/**
 * 获取任务名称
 * @param {number|string} taskId
 * @returns {Promise<string>}
 */
export async function getTaskName(taskId) {
  if (!taskId) return '-'
  const id = String(taskId)
  
  if (cache.tasks.has(id)) {
    return cache.tasks.get(id)
  }
  
  // 尝试预加载所有任务
  await preloadNames('tasks')
  return cache.tasks.get(id) ?? `任务#${id}`
}

/**
 * 获取渠道名称
 * @param {number|string} channelId
 * @returns {Promise<string>}
 */
export async function getChannelName(channelId) {
  if (!channelId) return '-'
  const id = String(channelId)
  
  if (cache.channels.has(id)) {
    return cache.channels.get(id)
  }
  
  await preloadNames('channels')
  return cache.channels.get(id) ?? `渠道#${id}`
}

/**
 * 获取数据源名称
 * @param {number|string} datasourceId
 * @returns {Promise<string>}
 */
export async function getDatasourceName(datasourceId) {
  if (!datasourceId) return '-'
  const id = String(datasourceId)
  
  if (cache.datasources.has(id)) {
    return cache.datasources.get(id)
  }
  
  await preloadNames('datasources')
  return cache.datasources.get(id) ?? `数据源#${id}`
}

/**
 * 获取工作流名称
 * @param {number|string} workflowId
 * @returns {Promise<string>}
 */
export async function getWorkflowName(workflowId) {
  if (!workflowId) return '-'
  const id = String(workflowId)
  
  if (cache.workflows.has(id)) {
    return cache.workflows.get(id)
  }
  
  await preloadNames('workflows')
  return cache.workflows.get(id) ?? `工作流#${id}`
}

/**
 * 获取服务器分组名称
 * @param {number|string} groupId
 * @returns {Promise<string>}
 */
export async function getServerGroupName(groupId) {
  if (!groupId) return '-'
  const id = String(groupId)
  
  if (cache.serverGroups.has(id)) {
    return cache.serverGroups.get(id)
  }
  
  await preloadNames('serverGroups')
  return cache.serverGroups.get(id) ?? `分组#${id}`
}

/**
 * 获取服务器名称
 * @param {number|string} serverId
 * @returns {Promise<string>}
 */
export async function getServerName(serverId) {
  if (!serverId) return '-'
  const id = String(serverId)
  
  if (cache.servers.has(id)) {
    return cache.servers.get(id)
  }
  
  await preloadNames('servers')
  return cache.servers.get(id) ?? `服务器#${id}`
}

/**
 * 获取模板名称
 * @param {number|string} templateId
 * @returns {Promise<string>}
 */
export async function getTemplateName(templateId) {
  if (!templateId) return '-'
  const id = String(templateId)
  
  if (cache.templates.has(id)) {
    return cache.templates.get(id)
  }
  
  await preloadNames('templates')
  return cache.templates.get(id) ?? `模板#${id}`
}

// ========== 同步获取方法（仅从缓存读取） ==========

/**
 * 同步获取任务名称（仅从缓存，需先预加载）
 */
export const getTaskNameSync = (taskId) => 
  cache.tasks.get(String(taskId)) ?? `任务#${taskId}`

/**
 * 同步获取渠道名称
 */
export const getChannelNameSync = (channelId) => 
  cache.channels.get(String(channelId)) ?? `渠道#${channelId}`

/**
 * 同步获取数据源名称
 */
export const getDatasourceNameSync = (datasourceId) => 
  cache.datasources.get(String(datasourceId)) ?? `数据源#${datasourceId}`

/**
 * 同步获取工作流名称
 */
export const getWorkflowNameSync = (workflowId) => 
  cache.workflows.get(String(workflowId)) ?? `工作流#${workflowId}`

/**
 * 同步获取服务器分组名称
 */
export const getServerGroupNameSync = (groupId) => 
  cache.serverGroups.get(String(groupId)) ?? `分组#${groupId}`

/**
 * 同步获取服务器名称
 */
export const getServerNameSync = (serverId) => 
  cache.servers.get(String(serverId)) ?? `服务器#${serverId}`

/**
 * 同步获取模板名称
 */
export const getTemplateNameSync = (templateId) => 
  cache.templates.get(String(templateId)) ?? `模板#${templateId}`
