import IndexView from './src/views/Index.vue'
import ArticlesView from './src/views/Articles.vue'
import DiffView from './src/views/Diff.vue'

export const menu = [
  { path: '/kb', title: '法律知识库', icon: 'Collection' },
  { path: '/articles', title: '条文检索', icon: 'Search' },
  { path: '/diff', title: '条文对比', icon: 'DataAnalysis' }
]

export const routes = [
  {
    path: 'kb',
    name: 'KnowledgeBase',
    component: IndexView,
    meta: { title: '法律知识库', subtitle: 'pgvector 语义检索 · 文档入库 · RAG 溯源问答' }
  },
  {
    path: 'articles',
    name: 'Articles',
    component: ArticlesView,
    meta: { title: '条文检索', subtitle: '法规条款混合检索（向量 + 关键词）' }
  },
  {
    path: 'diff',
    name: 'ArticleDiff',
    component: DiffView,
    meta: { title: '条文对比', subtitle: '多地域 / 多场景交叉对比 · 跨地域相似度 · 知识库出处溯源' }
  }
]

export default { name: 'kb', menu, routes }
