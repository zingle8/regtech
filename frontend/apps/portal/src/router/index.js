import { h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { Shell } from '@regtech/ui'
import { useUserStore, setUnauthorizedHandler } from '@regtech/shared'

import overview from '@regtech/module-overview'
import kb from '@regtech/module-kb'
import graph from '@regtech/module-graph'
import ai from '@regtech/module-ai'
import ontology from '@regtech/module-ontology'
import system from '@regtech/module-system'

const modules = [overview, kb, graph, ai, ontology, system]

/** 侧边栏分组：每个分组下的模块自带菜单项 */
export const menuRoutes = [
  { title: '监管概览', children: overview.menu },
  { title: '法规知识', children: [...kb.menu, ...graph.menu] },
  { title: '智能应用', children: [...ai.menu, ...ontology.menu] },
  { title: '平台管理', children: system.menu }
]

const Layout = {
  name: 'AppLayout',
  render: () => h(Shell, { menus: menuRoutes, brand: 'NCS RegTech' })
}

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/overview',
    children: modules.flatMap((m) => m.routes)
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  const redirect = current && current.path !== '/login' ? current.fullPath : ''
  router.replace(redirect ? { name: 'Login', query: { redirect } } : { name: 'Login' })
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (!to.meta.public) {
    if (!store.token) {
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
    if (store.expired) {
      store.forceLogout()
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router
