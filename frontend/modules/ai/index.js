import IndexView from './src/views/Index.vue'

export const menu = [{ path: '/ai', title: 'AI 问答', icon: 'ChatDotRound' }]

export const routes = [
  {
    path: 'ai',
    name: 'AiChat',
    component: IndexView,
    meta: { title: 'AI 问答', subtitle: 'OpenAI 兼容协议 · 本地 Ollama 模型 · 会话留存' }
  }
]

export default { name: 'ai', menu, routes }
