import request from '@regtech/shared/request'

const BASE = '/regtech/user'

export function fetchUsers(params) {
  return request.get(`${BASE}/users`, { params })
}

export function createUser(data) {
  return request.post(`${BASE}/users`, data)
}

export function updateUser(id, data) {
  return request.put(`${BASE}/users/${id}`, data)
}

export function changeUserStatus(id, status) {
  return request.put(`${BASE}/users/${id}/status`, { status })
}

export function resetUserPassword(id, password) {
  return request.put(`${BASE}/users/${id}/password`, { password })
}

export function deleteUser(id) {
  return request.delete(`${BASE}/users/${id}`)
}
