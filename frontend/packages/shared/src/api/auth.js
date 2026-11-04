import request from '../request'

/** 登录（jv-regtech-user） */
export function login(data) {
  return request.post('/regtech/user/auth/login', data)
}

/** 退出登录 */
export function logout() {
  return request.post('/regtech/user/auth/logout')
}

/** 当前登录用户 */
export function fetchMe() {
  return request.get('/regtech/user/auth/me')
}
