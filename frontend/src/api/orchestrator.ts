import api from './client'

export interface ChatResult {
  conversationId: string
  reply: string
  mode: string
  stages: Array<{ agentId: string; agentName: string; inputFrom: string; description: string }>
  reasoning: string
}

export const orchestratorApi = {
  chat: (message: string, conversationId?: string) =>
    api.post<unknown, ChatResult>('/orchestrator/chat', { message, conversationId }),
}
