/**
 * 监控任务相关API（SQL任务）
 * @typedef {import('./types').components['schemas']['MonitorTaskRequest']} MonitorTaskRequest
 */

import request from './request'
import { pick } from './utils'

const TASK_FIELDS = ['id', 'name', 'datasourceId', 'sqlQuery', 'cronExpression', 'thresholdRule', 'alarmChannels', 'alarmTemplateId', 'isActive']

export const monitorTaskApi = {
  list: () => request.get('/task'),
  getById: (id) => request.get(`/task/${id}`),
  
  /** @param {MonitorTaskRequest} data */
  create: (data) => request.post('/task', pick(data, TASK_FIELDS)),
  
  /** @param {MonitorTaskRequest} data */
  update: (data) => request.put('/task', pick(data, TASK_FIELDS)),
  
  delete: (id) => request.delete(`/task/${id}`),
  
  execute: (id) => request.post(`/task/${id}/execute`),
  
  testSql: (datasourceId, sql) => request.post('/task/test-sql', { datasourceId, sql })
}
