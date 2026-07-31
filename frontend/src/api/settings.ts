import api from './client'

export interface SystemSetting { id?: string; key: string; value: unknown; description?: string }

export const settingApi = {
  all: () => api.get<unknown, SystemSetting[]>('/settings'),
  update: (b: SystemSetting) => api.put<unknown, SystemSetting>('/settings', b),
}
