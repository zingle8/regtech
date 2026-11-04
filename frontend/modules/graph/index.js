import IndexView from './src/views/Index.vue'

export const menu = [{ path: '/graph', title: '法律知识图谱', icon: 'Share' }]

export const routes = [
  {
    path: 'graph',
    name: 'KnowledgeGraph',
    component: IndexView,
    meta: { title: '法律知识图谱', subtitle: '法规 / 条款 / 主题关联网络，支持一键从法条库构图' }
  }
]

export default { name: 'graph', menu, routes }
