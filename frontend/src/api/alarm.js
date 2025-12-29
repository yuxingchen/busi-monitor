/**
 * 告警相关API
 * @typedef {import('./types').components['schemas']['AlarmChannelRequest']} AlarmChannelRequest
 * @typedef {import('./types').components['schemas']['AlarmTemplateRequest']} AlarmTemplateRequest
 */

import request from './request'
import { pick } from './utils'

// ========== 字段白名单（与后端DTO严格对应）==========

/** @type {(keyof AlarmChannelRequest)[]} */
const CHANNEL_FIELDS = ['id', 'name', 'type', 'config', 'isActive']

/** @type {(keyof AlarmTemplateRequest)[]} */
const TEMPLATE_FIELDS = ['id', 'name', 'contentType', 'content', 'isActive']

// ========== 告警渠道 API ==========

export const alarmChannelApi = {
  /** 获取所有渠道 */
  list: () => request.get('/alarm/channel'),
  
  /** 获取启用的渠道 */
  listActive: () => request.get('/alarm/channel/active'),
  
  /** 
   * 创建渠道 
   * @param {AlarmChannelRequest} data 
   */
  create: (data) => request.post('/alarm/channel', pick(data, CHANNEL_FIELDS)),
  
  /** 
   * 更新渠道 
   * @param {AlarmChannelRequest} data 
   */
  update: (data) => request.put('/alarm/channel', pick(data, CHANNEL_FIELDS)),
  
  /** 删除渠道 */
  delete: (id) => request.delete(`/alarm/channel/${id}`)
}

// ========== 告警模板 API ==========

export const alarmTemplateApi = {
  /** 获取所有模板 */
  list: () => request.get('/alarm/template'),
  
  /** 
   * 创建模板 
   * @param {AlarmTemplateRequest} data 
   */
  create: (data) => request.post('/alarm/template', pick(data, TEMPLATE_FIELDS)),
  
  /** 
   * 更新模板 
   * @param {AlarmTemplateRequest} data 
   */
  update: (data) => request.put('/alarm/template', pick(data, TEMPLATE_FIELDS)),
  
  /** 删除模板 */
  delete: (id) => request.delete(`/alarm/template/${id}`)
}

// ========== 活动告警 API ==========

export const activeAlarmApi = {
  /** 获取所有活动告警 */
  list: () => request.get('/alarm/active'),
  
  /** 获取告警历史 */
  history: (params) => request.get('/alarm/history', { params }),
  
  /** 确认告警 */
  acknowledge: (id) => request.post(`/alarm/active/${id}/acknowledge`),
  
  /** 抑制告警 */
  suppress: (id, minutes) => request.post(`/alarm/active/${id}/suppress?minutes=${minutes}`)
}
