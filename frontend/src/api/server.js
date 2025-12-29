/**
 * 服务器相关API
 * @typedef {import('./types').components['schemas']['ServerAssetRequest']} ServerAssetRequest
 * @typedef {import('./types').components['schemas']['BatchAddServerRequest']} BatchAddServerRequest
 * @typedef {import('./types').components['schemas']['ServerGroupRequest']} ServerGroupRequest
 * @typedef {import('./types').components['schemas']['ServerMonitorTaskRequest']} ServerMonitorTaskRequest
 */

import request from './request'
import { pick, arrayToString } from './utils'

// ========== 字段白名单 ==========

const SERVER_FIELDS = ['id', 'name', 'ip', 'port', 'username', 'authType', 'password', 'privateKey', 'groupId', 'tags']
const BATCH_SERVER_FIELDS = ['ipInput', 'name', 'username', 'password', 'port', 'groupId']
const GROUP_FIELDS = ['id', 'name', 'parentId', 'description', 'isActive']
const TASK_FIELDS = ['id', 'name', 'serverId', 'templateId', 'collectScript', 'params', 'thresholdRule', 'cronExpression', 'alarmChannels', 'alarmTemplateId', 'isActive']

// ========== 服务器资产 API ==========

export const serverAssetApi = {
  list: () => request.get('/server-asset'),
  getById: (id) => request.get(`/server-asset/${id}`),
  listByGroup: (groupId) => request.get(`/server-asset/group/${groupId}`),
  
  /** @param {ServerAssetRequest} data */
  create: (data) => request.post('/server-asset', pick(data, SERVER_FIELDS)),
  
  /** @param {ServerAssetRequest} data */
  update: (data) => request.put('/server-asset', pick(data, SERVER_FIELDS)),
  
  delete: (id) => request.delete(`/server-asset/${id}`),
  
  /** @param {BatchAddServerRequest} data */
  batchAdd: (data) => request.post('/server-asset/batch', pick(data, BATCH_SERVER_FIELDS)),
  
  testConnection: (id) => request.post(`/server-asset/${id}/test`),
  executeScript: (id, script) => request.post(`/server-asset/${id}/execute`, { script })
}

// ========== 服务器分组 API ==========

export const serverGroupApi = {
  list: () => request.get('/server-group'),
  
  /** @param {ServerGroupRequest} data */
  create: (data) => request.post('/server-group', pick(data, GROUP_FIELDS)),
  
  /** @param {ServerGroupRequest} data */
  update: (data) => request.put('/server-group', pick(data, GROUP_FIELDS)),
  
  delete: (id) => request.delete(`/server-group/${id}`)
}

// ========== 服务器监控任务 API ==========

export const serverMonitorTaskApi = {
  list: () => request.get('/server-monitor-task'),
  getById: (id) => request.get(`/server-monitor-task/${id}`),
  listByServer: (serverId) => request.get(`/server-monitor-task/server/${serverId}`),
  
  /** @param {ServerMonitorTaskRequest} data */
  create: (data) => request.post('/server-monitor-task', pick(data, TASK_FIELDS)),
  
  /** 
   * 更新任务（自动转换 alarmChannelIds 为 alarmChannels）
   * @param {ServerMonitorTaskRequest & {alarmChannelIds?: number[]}} data 
   */
  update: (data) => {
    const payload = pick(data, TASK_FIELDS)
    // 如果前端使用 alarmChannelIds，自动转换
    if (data.alarmChannelIds && !data.alarmChannels) {
      payload.alarmChannels = arrayToString(data.alarmChannelIds)
    }
    return request.put('/server-monitor-task', payload)
  },
  
  /** 部分更新 */
  updateById: (id, data) => request.put(`/server-monitor-task/${id}`, pick(data, TASK_FIELDS)),
  
  delete: (id) => request.delete(`/server-monitor-task/${id}`),
  
  batchAdd: (serverId, templateIds) => request.post('/server-monitor-task/batch', { serverId, templateIds }),
  
  run: (id) => request.post(`/server-monitor-task/${id}/run`)
}
