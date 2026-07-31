import axios, { AxiosError } from 'axios'
import { message } from 'antd'
import { useAuthStore } from '@/stores/auth'

const BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const api = axios.create({ baseURL: BASE, timeout: 30000 })

api.interceptors.request.use((c) => {
  const t = useAuthStore.getState().token
  if (t) c.headers.Authorization = `Bearer ${t}`
  return c
})

api.interceptors.response.use(
  (r) => {
    const body = r.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      message.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || 'biz error'))
    }
    return body
  },
  (e: AxiosError) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const data = e.response?.data as any
    if (e.response?.status === 401) {
      useAuthStore.getState().logout()
      if (location.pathname !== '/login') location.href = '/login'
    }
    const msg = data?.msg || e.message || '网络错误'
    message.error(msg)
    return Promise.reject(e)
  },
)

export default api