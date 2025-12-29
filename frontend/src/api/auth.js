/**
 * 认证相关API
 */

import request from './request'

export const authApi = {
  login: (username, password) => request.post('/auth/login', { username, password }),
  
  getProfile: () => request.get('/auth/profile'),
  
  changePassword: (oldPassword, newPassword) => request.post('/auth/change-password', { oldPassword, newPassword })
}
