/**
 * 监控模板相关API
 * @typedef {import('./types').components['schemas']['MonitorTemplateRequest']} MonitorTemplateRequest
 */

import request from './request'
import { pick } from './utils'

const TEMPLATE_FIELDS = ['id', 'name', 'category', 'collectType', 'collectScript', 'defaultThreshold', 'defaultCron', 'description', 'alarmTemplateId', 'isSystem', 'isActive']

export const monitorTemplateApi = {
  list: () => request.get('/monitor-template'),
  getById: (id) => request.get(`/monitor-template/${id}`),
  getByCategory: (category) => request.get(`/monitor-template/category/${category}`),
  getSystemTemplates: () => request.get('/monitor-template/system'),
  
  /** @param {MonitorTemplateRequest} data */
  create: (data) => request.post('/monitor-template', pick(data, TEMPLATE_FIELDS)),
  
  /** @param {MonitorTemplateRequest} data */
  update: (data) => request.put('/monitor-template', pick(data, TEMPLATE_FIELDS)),
  
  delete: (id) => request.delete(`/monitor-template/${id}`)
}
