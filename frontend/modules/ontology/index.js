import IndexView from './src/views/Index.vue'

export const menu = [{ path: '/ontology', title: '本体论 RAG', icon: 'Connection' }]

export const routes = [
  {
    path: 'ontology',
    name: 'Ontology',
    component: IndexView,
    meta: { title: '本体论 RAG', subtitle: '本体建模 · 概念扩展检索 · 结构化溯源' }
  }
]

export default { name: 'ontology', menu, routes }
