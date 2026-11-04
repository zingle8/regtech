import IndexView from './src/views/Index.vue'

export const menu = [{ path: '/overview', title: '数据总览', icon: 'DataBoard' }]

export const routes = [
  {
    path: 'overview',
    name: 'Overview',
    component: IndexView,
    meta: { title: '数据总览', subtitle: '法规知识资产、图谱规模与问答活跃度' }
  }
]

export default { name: 'overview', menu, routes }
