/**
 * 用户管理相关API
 * @typedef {import('./types').components['schemas']['UserRequest']} UserRequest
 */

import request from './request'
import { pick } from './utils'

const USER_FIELDS = ['username', 'password', 'role']

export const userApi = {
  list: (params) => request.get('/user/list', { params }),
  getById: (id) => request.get(`/user/${id}`),
  
  /** @param {UserRequest} data */
  create: (data) => request.post('/user', pick(data, USER_FIELDS)),
  
  /** @param {UserRequest} data */
  update: (id, data) => request.put(`/user/${id}`, pick(data, USER_FIELDS)),
  
  delete: (id) => request.delete(`/user/${id}`),
  
  toggleStatus: (id) => request.post(`/user/${id}/toggle-status`),
  
  resetPassword: (id, password) => request.post(`/user/${id}/reset-password`, { password })
}
