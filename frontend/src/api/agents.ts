import api from './client'

export interface PageResult<T> { items: T[]; total: number; page: number; size: number }

export interface Agent {
  id?: string
  name: string
  description?: string
  difyBaseUrl?: string
  difyAppId?: string
  difyApiKey?: string
  skills?: string
  capabilities?: string
  approvalSkills?: string
  status?: string
  lastHealthAt?: string
}

export const agentApi = {
  page: (p = 1, s = 20) =>
    api.get<unknown, PageResult<Agent>>('/agents', { params: { page: p, size: s } }),
  all: () => api.get<unknown, Agent[]>('/agents/all'),
  get: (id: string) => api.get<unknown, Agent>(`/agents/${id}`),
  create: (b: Agent) => api.post<unknown, Agent>('/agents', b),
  update: (id: string, b: Agent) => api.put<unknown, Agent>(`/agents/${id}`, b),
  remove: (id: string) => api.delete<unknown, void>(`/agents/${id}`),
  test: (id: string) => api.post<unknown, TestResult>(`/agents/${id}/test`),
  card: (id: string) => api.get<unknown, AgentCard>(`/agents/${id}/card`),
}

export interface TestResult {
  ok: boolean
  answer?: string
  outputs?: Record<string, unknown>
  error?: string
  code?: number
  workflow_run_id?: string
  conversation_id?: string
}

export interface AgentCard {
  name: string
  description: string
  version: string
  protocolVersion: string
  url: string
  capabilities: { streaming?: boolean; pushNotifications?: boolean; stateTransitionHistory?: boolean }
  skills: Array<{
    id: string
    name: string
    description: string
    tags: string[]
    inputModes: string[]
    outputModes: string[]
  }>
}