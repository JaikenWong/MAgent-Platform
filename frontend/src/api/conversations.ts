import api from './client'

export interface Conversation {
  id?: string
  source: string
  externalChatId?: string
  externalUserId?: string
  a2aContextId?: string
  status: string
  closedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface Message {
  id?: string
  conversationId: string
  role: string
  agentId?: string
  parts: string
  createdAt: string
}

export interface ConversationDetail extends Conversation {
  messages?: Message[]
}

export const conversationApi = {
  page: (p = 1, s = 20) =>
    api.get<unknown, { items: Conversation[]; total: number }>('/conversations', { params: { page: p, size: s } }),
  get: (id: string) => api.get<unknown, ConversationDetail>(`/conversations/${id}`),
  messages: (id: string) => api.get<unknown, Message[]>(`/conversations/${id}/messages`),
}
