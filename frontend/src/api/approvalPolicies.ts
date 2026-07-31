import api from './client'

export interface ApprovalPolicy {
  id?: string; name: string; description?: string
  strategy: string; quorum?: number; requiredRole?: string
  timeoutSeconds?: number; timeoutAction?: string
  escalationChannel?: string; appliesTo?: string; enabled?: boolean
}

export const policyApi = {
  page: (p = 1, s = 20) => api.get<unknown, { items: ApprovalPolicy[]; total: number }>('/approval-policies', { params: { page: p, size: s } }),
  all: () => api.get<unknown, ApprovalPolicy[]>('/approval-policies/all'),
  get: (id: string) => api.get<unknown, ApprovalPolicy>(`/approval-policies/${id}`),
  create: (b: ApprovalPolicy) => api.post<unknown, ApprovalPolicy>('/approval-policies', b),
  update: (id: string, b: ApprovalPolicy) => api.put<unknown, ApprovalPolicy>(`/approval-policies/${id}`, b),
  remove: (id: string) => api.delete<unknown, void>(`/approval-policies/${id}`),
}
