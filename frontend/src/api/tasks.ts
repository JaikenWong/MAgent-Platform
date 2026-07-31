import api from './client'

export interface TaskItem { id: string; contextId?: string; assignedAgentId?: string
  status: string; errorDetail?: string; createdAt?: string; updatedAt?: string; completedAt?: string }

export const taskApi = {
  page: (p = 1, s = 20, status?: string) =>
    api.get<unknown, { items: TaskItem[]; total: number }>('/tasks', { params: { page: p, size: s, status } }),
  get: (id: string) => api.get<unknown, TaskItem>(`/tasks/${id}`),
  cancel: (id: string) => api.post<unknown, TaskItem>(`/tasks/${id}/cancel`),
}
