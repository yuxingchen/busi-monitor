/**
 * 工作流相关API
 * @typedef {import('./types').components['schemas']['WorkflowRequest']} WorkflowRequest
 */

import request from './request'
import { pick } from './utils'

const WORKFLOW_FIELDS = ['id', 'name', 'description', 'datasourceId', 'cronExpression', 'timeoutSeconds', 'indexFields', 'isActive', 'steps']

export const workflowApi = {
  list: () => request.get('/workflows'),
  getById: (id) => request.get(`/workflows/${id}`),
  
  /** @param {WorkflowRequest} data */
  create: (data) => request.post('/workflows', pick(data, WORKFLOW_FIELDS)),
  
  /** @param {WorkflowRequest} data */
  update: (id, data) => request.put(`/workflows/${id}`, pick(data, WORKFLOW_FIELDS)),
  
  delete: (id) => request.delete(`/workflows/${id}`),
  
  execute: (id) => request.post(`/workflows/${id}/execute`),
  
  toggleActive: (id) => request.patch(`/workflows/${id}/active`),
  
  getResult: (id) => request.get(`/workflows/${id}/result`),
  
  getExecutions: (id) => request.get(`/workflows/${id}/executions`),
  
  testStep: (data) => request.post('/workflows/test-step', data)
}
