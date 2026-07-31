import api from './client'

export interface DashboardStats {
  agentCount: number; taskCountToday: number; pendingApprovals: number
  feishuMessageCount: number; taskDistribution: Record<string, number>
  recentConversations: Array<{ id: string; source: string; status: string; createdAt: string }>
  [key: string]: unknown
}

export const dashboardApi = {
  stats: () => api.get<unknown, DashboardStats>('/dashboard/stats'),
}
