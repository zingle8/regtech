import request from '@regtech/shared/request'

const BASE = '/regtech/kb'

export function fetchDatasets(keyword) {
  return request.get(`${BASE}/datasets`, { params: { keyword } })
}

export function createDataset(data) {
  return request.post(`${BASE}/datasets`, data)
}

export function fetchDocuments(params) {
  return request.get(`${BASE}/documents`, { params })
}

export function fetchDocument(id) {
  return request.get(`${BASE}/documents/${id}`)
}

export function createDocument(data) {
  return request.post(`${BASE}/documents`, data)
}

export function deleteDocument(id) {
  return request.delete(`${BASE}/documents/${id}`)
}

export function searchKnowledge(data) {
  return request.post(`${BASE}/search`, data)
}

export function fetchArticles(params) {
  return request.get(`${BASE}/articles`, { params })
}

export function fetchArticle(id) {
  return request.get(`${BASE}/articles/${id}`)
}

export function createArticle(data) {
  return request.post(`${BASE}/articles`, data)
}

export function updateArticle(id, data) {
  return request.put(`${BASE}/articles/${id}`, data)
}

export function deleteArticle(id) {
  return request.delete(`${BASE}/articles/${id}`)
}

export function searchArticles(data) {
  return request.post(`${BASE}/articles/search`, data)
}

export function askWithKnowledge(data) {
  return request.post(`${BASE}/rag/ask`, data)
}

/** 单对条文逐行精读对比（会写入对比历史） */
export function compareDiff(data) {
  return request.post(`${BASE}/diff/compare`, data)
}

/** 已有条文的法域清单（含条文数） */
export function fetchDiffJurisdictions() {
  return request.get(`${BASE}/diff/jurisdictions`)
}

/** 多地域 / 多场景交叉对比 */
export function compareMatrix(data) {
  return request.post(`${BASE}/diff/matrix`, data, { timeout: 180000 })
}

/** 对比历史（逐条精读留痕） */
export function fetchDiffRecords(params) {
  return request.get(`${BASE}/diff/records`, { params })
}

export function fetchDiffRecord(id) {
  return request.get(`${BASE}/diff/records/${id}`)
}

export function deleteDiffRecord(id) {
  return request.delete(`${BASE}/diff/records/${id}`)
}

/** 为历史 / 初始化数据补齐向量（切换向量模型后也需要执行） */
export function reindexKnowledge(limit = 2000) {
  return request.post(`${BASE}/reindex`, null, { params: { limit }, timeout: 300000 })
}
