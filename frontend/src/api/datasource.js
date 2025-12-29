/**
 * 数据源相关API
 * @typedef {import('./types').components['schemas']['DataSourceRequest']} DataSourceRequest
 */

import request from './request'
import { pick } from './utils'

const DATASOURCE_FIELDS = ['id', 'name', 'type', 'url', 'username', 'password', 'driverClassName', 'isActive']

export const datasourceApi = {
  list: () => request.get('/datasource'),
  getById: (id) => request.get(`/datasource/${id}`),
  
  /** @param {DataSourceRequest} data */
  create: (data) => request.post('/datasource', pick(data, DATASOURCE_FIELDS)),
  
  /** @param {DataSourceRequest} data */
  update: (data) => request.put('/datasource', pick(data, DATASOURCE_FIELDS)),
  
  delete: (id) => request.delete(`/datasource/${id}`),
  
  testConnection: (id) => request.post(`/datasource/${id}/test`)
}
