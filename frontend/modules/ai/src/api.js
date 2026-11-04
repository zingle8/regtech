import request from '@regtech/shared/request'
import { useUserStore } from '@regtech/shared'

const BASE = '/regtech/ai'

/** 本地模型列表（OpenAI 格式） */
export function fetchModels() {
  return request.get(`${BASE}/models`)
}

export function fetchConversations() {
  return request.get(`${BASE}/conversations`)
}

export function fetchMessages(id) {
  return request.get(`${BASE}/conversations/${id}/messages`)
}

export function createConversation(data = {}) {
  return request.post(`${BASE}/conversations`, data)
}

export function renameConversation(id, title) {
  return request.put(`${BASE}/conversations/${id}`, { title })
}

export function deleteConversation(id) {
  return request.delete(`${BASE}/conversations/${id}`)
}

export function fetchAiStats() {
  return request.get(`${BASE}/stats`)
}

/** 平台知识库数据集（用于知识库增强问答） */
export function fetchDatasets() {
  return request.get('/regtech/kb/datasets')
}

/** 知识库语义检索：{ query, datasetId, topK } -> { mode, total, records } */
export function searchKnowledge(payload) {
  return request.post('/regtech/kb/search', payload)
}

/**
 * 流式对话：后端以 SSE 返回，前端用 fetch 读取（EventSource 不支持自定义 Header）。
 *
 * @param {object} payload  { messages, model, conversationId, scene, systemPrompt }
 * @param {object} handlers { onMeta, onDelta, onDone, onError }
 */
export async function streamChat(payload, handlers = {}, signal) {
  const store = useUserStore()
  const response = await fetch(`/api${BASE}/v1/chat/completions/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(store.token ? { Authorization: `Bearer ${store.token}` } : {})
    },
    body: JSON.stringify({ ...payload, stream: true }),
    signal
  })

  if (!response.ok) {
    throw new Error(`模型服务异常（HTTP ${response.status}）`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''

    for (const frame of frames) {
      const line = frame.split('\n').find((item) => item.startsWith('data:'))
      if (!line) continue
      const data = line.slice(5).trim()
      if (!data) continue
      if (data === '[DONE]') {
        handlers.onDone && handlers.onDone()
        return
      }
      try {
        const chunk = JSON.parse(data)
        if (chunk.type === 'meta') {
          handlers.onMeta && handlers.onMeta(chunk)
        } else if (chunk.type === 'error') {
          handlers.onError && handlers.onError(chunk.message || '模型调用失败')
          return
        } else if (chunk.choices && chunk.choices[0] && chunk.choices[0].delta) {
          handlers.onDelta && handlers.onDelta(chunk.choices[0].delta.content || '')
        }
      } catch (e) {
        /* 忽略无法解析的心跳帧 */
      }
    }
  }
  handlers.onDone && handlers.onDone()
}
