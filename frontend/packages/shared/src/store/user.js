import { defineStore } from 'pinia'

const TOKEN_KEY = 'rt_token'
const USER_KEY = 'rt_user'

let persist = 'local'

const storage = () => (persist === 'local' ? localStorage : sessionStorage)

function readPersisted(key) {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || ''
}

export function parseJwtPayload(token) {
  if (!token) return null
  const part = String(token).split('.')[1]
  if (!part) return null
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    return JSON.parse(decodeURIComponent(escape(atob(padded))))
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: readPersisted(TOKEN_KEY),
    userInfo: JSON.parse(readPersisted(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => (state.userInfo && state.userInfo.nickname) || '未登录',
    role: (state) => (state.userInfo && state.userInfo.role) || '',
    expired: (state) => {
      const payload = parseJwtPayload(state.token)
      if (!payload || !payload.exp) return false
      return payload.exp * 1000 <= Date.now()
    }
  },
  actions: {
    setSession(token, userInfo, remember = true) {
      persist = remember ? 'local' : 'session'
      this.token = token
      this.userInfo = userInfo
      const target = storage()
      const other = persist === 'local' ? sessionStorage : localStorage
      ;[TOKEN_KEY, USER_KEY].forEach((k) => other.removeItem(k))
      target.setItem(TOKEN_KEY, token)
      target.setItem(USER_KEY, JSON.stringify(userInfo))
    },
    clearSession() {
      this.token = ''
      this.userInfo = null
      ;[localStorage, sessionStorage].forEach((s) => {
        s.removeItem(TOKEN_KEY)
        s.removeItem(USER_KEY)
      })
    },
    forceLogout() {
      this.clearSession()
    }
  }
})
