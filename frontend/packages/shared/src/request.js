import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from './store/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 60000
})

let unauthorizedHandler = null

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn
}

function handleUnauthorized(message) {
  const store = useUserStore()
  if (!store.token) return
  store.forceLogout()
  ElMessage.error(message || '登录已失效，请重新登录')
  if (unauthorizedHandler) unauthorizedHandler()
}

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return body.data
      }
      if (body.code === 401 || body.code === 403) {
        handleUnauthorized(body.message)
        return Promise.reject(new Error(body.message || '登录已失效'))
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      handleUnauthorized('登录已失效，请重新登录')
    } else {
      ElMessage.error(
        (error.response && error.response.data && error.response.data.message) ||
          error.message ||
          '网络异常'
      )
    }
    return Promise.reject(error)
  }
)

export default request
