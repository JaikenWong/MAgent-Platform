import api from './client'

export interface FeishuBot {
  id?: string
  name: string
  appId?: string
  appSecret?: string
  verificationToken?: string
  encryptKey?: string
  webhookUrl?: string
  boundAgentId?: string
  status?: string
  longConnectionEnabled?: boolean
}

export const botApi = {
  page: (p = 1, s = 20) => api.get<unknown, { items: FeishuBot[]; total: number }>('/bots', { params: { page: p, size: s } }),
  all: () => api.get<unknown, FeishuBot[]>('/bots/all'),
  get: (id: string) => api.get<unknown, FeishuBot>(`/bots/${id}`),
  create: (b: FeishuBot) => api.post<unknown, FeishuBot>('/bots', b),
  update: (id: string, b: FeishuBot) => api.put<unknown, FeishuBot>(`/bots/${id}`, b),
  remove: (id: string) => api.delete<unknown, void>(`/bots/${id}`),
  enableLongConnection: (id: string) => api.post<unknown, void>(`/bots/${id}/long-connection/enable`),
  disableLongConnection: (id: string) => api.post<unknown, void>(`/bots/${id}/long-connection/disable`),
  test: (id: string) => api.post<unknown, { ok: boolean; message: string }>(`/bots/${id}/test`),
}
