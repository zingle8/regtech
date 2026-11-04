import request from '@regtech/shared/request'

/** 总览指标（jv-regtech-overview 聚合各微服务） */
export function fetchSummary() {
  return request.get('/regtech/overview/summary')
}

/** 问答趋势 */
export function fetchTrend(days = 14) {
  return request.get('/regtech/overview/trend', { params: { days } })
}

/** 最近的条文变更动态（新增 / 更新 / 删除，jv-regtech-kb 提供） */
export function fetchArticleChanges(limit = 12) {
  return request.get('/regtech/kb/articles/changes', { params: { limit } })
}
