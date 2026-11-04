import request from '@regtech/shared/request'

const BASE = '/regtech/ontology'

export function fetchClassTree(keyword) {
  return request.get(`${BASE}/classes`, { params: { keyword } })
}

export function createClass(data) {
  return request.post(`${BASE}/classes`, data)
}

export function deleteClass(id) {
  return request.delete(`${BASE}/classes/${id}`)
}

export function fetchProperties(classId) {
  return request.get(`${BASE}/properties`, { params: { classId } })
}

export function createProperty(data) {
  return request.post(`${BASE}/properties`, data)
}

export function deleteProperty(id) {
  return request.delete(`${BASE}/properties/${id}`)
}

export function fetchIndividuals(params) {
  return request.get(`${BASE}/individuals`, { params })
}

export function createIndividual(data) {
  return request.post(`${BASE}/individuals`, data)
}

export function deleteIndividual(id) {
  return request.delete(`${BASE}/individuals/${id}`)
}

export function askOntology(data) {
  return request.post(`${BASE}/rag/ask`, data)
}
