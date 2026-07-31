import api from './client'

export interface AuditLog {
  id?: string; actorId: string; action: string; entityType: string
  entityId: string; details?: string; createdAt?: string
}

export const auditApi = {
  page: (p = 1, s = 20, actor?: string, action?: string) =>
    api.get<unknown, { items: AuditLog[]; total: number }>('/audit', { params: { page: p, size: s, actorId: actor, action } }),
}
