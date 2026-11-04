export function formatNumber(value) {
  if (value === null || value === undefined || value === '') return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  if (num >= 100000000) return (num / 100000000).toFixed(2) + ' 亿'
  if (num >= 10000) return (num / 10000).toFixed(1) + ' 万'
  return num.toLocaleString('zh-CN')
}

export function formatPercent(value, digits = 1) {
  if (value === null || value === undefined) return '-'
  return (Number(value) * 100).toFixed(digits) + '%'
}

export function formatDate(value, withTime = false) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return withTime ? text.slice(0, 19) : text.slice(0, 10)
}

export function truncate(text, length = 80) {
  if (!text) return ''
  return text.length > length ? text.slice(0, length) + '…' : text
}

export function statusType(status) {
  if (['健康', '在线', '现行有效', 'UP'].includes(status)) return 'success'
  if (['亚健康', '已修订'].includes(status)) return 'warning'
  if (['异常', '已下线', '已废止', 'DOWN'].includes(status)) return 'danger'
  return 'info'
}

export function jurisdictionLabel(code) {
  return { CN: '中国大陆', HK: '中国香港', TW: '中国台湾', SG: '新加坡', US: '美国' }[code] || code || '-'
}
