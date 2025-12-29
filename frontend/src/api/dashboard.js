/**
 * 仪表盘相关API
 * @typedef {import('./types').components['schemas']['DashboardRequest']} DashboardRequest
 * @typedef {import('./types').components['schemas']['DashboardWidgetRequest']} DashboardWidgetRequest
 */

import request from './request'
import { pick } from './utils'

const DASHBOARD_FIELDS = ['id', 'name', 'description', 'gridCols', 'gridRows', 'cellHeight', 'isDefault', 'isActive']
const WIDGET_FIELDS = ['id', 'dashboardId', 'widgetType', 'taskId', 'workflowId', 'chartConfig', 'gridX', 'gridY', 'gridW', 'gridH', 'zIndex']

export const dashboardApi = {
  list: () => request.get('/dashboard'),
  getById: (id) => request.get(`/dashboard/${id}`),
  getDefault: () => request.get('/dashboard/default'),
  
  /** @param {DashboardRequest} data */
  create: (data) => request.post('/dashboard', pick(data, DASHBOARD_FIELDS)),
  
  /** @param {DashboardRequest} data */
  update: (data) => request.put('/dashboard', pick(data, DASHBOARD_FIELDS)),
  
  delete: (id) => request.delete(`/dashboard/${id}`),
  
  getWidgets: (dashboardId) => request.get(`/dashboard/${dashboardId}/widgets`),
  
  /** @param {DashboardWidgetRequest} data */
  addWidget: (dashboardId, data) => request.post(`/dashboard/${dashboardId}/widget`, pick(data, WIDGET_FIELDS)),
  
  /** @param {DashboardWidgetRequest} data */
  updateWidget: (data) => request.put('/dashboard/widget', pick(data, WIDGET_FIELDS)),
  
  deleteWidget: (widgetId) => request.delete(`/dashboard/widget/${widgetId}`),
  
  /** 批量保存布局 */
  saveLayout: (dashboardId, widgets) => request.post(`/dashboard/${dashboardId}/layout`, widgets)
}
