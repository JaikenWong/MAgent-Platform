import api from './client'

export interface Approval {
  id?: string; taskId: string; policyId: string; requestedBy?: string
  skillName: string; payload?: string; status: string
  decisionBy?: string; decisionAt?: string; decisionChannel?: string; comment?: string; createdAt?: string
}

export const approvalApi = {
  page: (p = 1, s = 20, status?: string) =>
    api.get<unknown, { items: Approval[]; total: number }>('/approvals', { params: { page: p, size: s, status } }),
  get: (id: string) => api.get<unknown, Approval>(`/approvals/${id}`),
  decide: (id: string, decision: string, comment?: string) =>
    api.post<unknown, Approval>(`/approvals/${id}/decide`, { decision, comment, actor: 'admin' }),
  pendingCount: () => api.get<unknown, number>('/approvals/pending/count'),
}
