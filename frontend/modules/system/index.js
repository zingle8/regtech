import UsersView from './src/views/Users.vue'

export const menu = [{ path: '/system/users', title: '用户管理', icon: 'User' }]

export const routes = [
  {
    path: 'system/users',
    name: 'Users',
    component: UsersView,
    meta: { title: '用户管理', subtitle: '平台账号、角色与状态维护' }
  }
]

export default { name: 'system', menu, routes }
