import { useQueryClient } from '@tanstack/react-query'
import { App, message, Modal } from 'antd'
import { agentApi, type Agent } from '@/api/agents'

export function useAgentDelete() {
  const qc = useQueryClient()
  const { modal } = App.useApp()
  return (agent: Agent) =>
    new Promise<void>((resolve, reject) => {
      modal.confirm({
        title: `删除 Agent "${agent.name}"`,
        content: '此操作逻辑删除, 不会物理清除。确认?',
        okType: 'danger',
        okText: '删除',
        cancelText: '取消',
        onOk: async () => {
          try {
            await agentApi.remove(agent.id!)
            message.success('已删除')
            qc.invalidateQueries({ queryKey: ['agents'] })
            resolve()
          } catch (e) {
            Modal.destroyAll()
            reject(e)
          }
        },
        onCancel: () => reject(new Error('cancel')),
      })
    })
}