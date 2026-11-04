import request from '@regtech/shared/request'

const BASE = '/regtech/graph'

export function fetchGraph(params) {
  return request.get(`${BASE}/graph`, { params })
}

export function fetchNode(id) {
  return request.get(`${BASE}/nodes/${id}`)
}

export function fetchNodeList(params) {
  return request.get(`${BASE}/nodes`, { params })
}

export function fetchStats() {
  return request.get(`${BASE}/stats`)
}

export function buildGraph(limit = 200) {
  return request.post(`${BASE}/build`, null, { params: { limit } })
}

export function createNode(data) {
  return request.post(`${BASE}/nodes`, data)
}

export function deleteNode(id) {
  return request.delete(`${BASE}/nodes/${id}`)
}
