import api from './client'

export interface OrchestrationRule {
  id?: string
  name: string
  description?: string
  triggerType: string
  triggerConfig?: string
  executionMode: string
  agentChain?: string
  fallbackAgentId?: string
  priority?: number
  enabled?: boolean
}

export const ruleApi = {
  page: (p = 1, s = 20) =>
    api.get<unknown, { items: OrchestrationRule[]; total: number }>('/rules', { params: { page: p, size: s } }),
  all: () => api.get<unknown, OrchestrationRule[]>('/rules/all'),
  get: (id: string) => api.get<unknown, OrchestrationRule>(`/rules/${id}`),
  create: (b: OrchestrationRule) => api.post<unknown, OrchestrationRule>('/rules', b),
  update: (id: string, b: OrchestrationRule) => api.put<unknown, OrchestrationRule>(`/rules/${id}`, b),
  remove: (id: string) => api.delete<unknown, void>(`/rules/${id}`),
}
