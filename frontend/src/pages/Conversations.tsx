import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Typography, Table, Tag, Drawer, Timeline, Spin, Empty,
} from 'antd'
import dayjs from 'dayjs'
import { conversationApi, type Message } from '@/api/conversations'
import { tokens } from '@/theme/tokens'

const { Text } = Typography

const SOURCE_LABELS: Record<string, string> = { feishu: '飞书', web: '前端', api: 'API' }
const STATUS_COLOR: Record<string, string> = {
  active: tokens.color.ok,
  completed: tokens.color.anchor,
  closed: tokens.color.muted,
}

export default function Conversations() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [msgLoading, setMsgLoading] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => conversationApi.page(1, 50),
  })
  const conversations = data?.items ?? []

  const loadMessages = async (id: string) => {
    setSelectedId(id)
    setMsgLoading(true)
    try {
      const msgs = await conversationApi.messages(id)
      setMessages(msgs)
    } catch (_e) {
      setMessages([])
    } finally {
      setMsgLoading(false)
    }
  }

  const columns = [
    {
      title: 'ID', dataIndex: 'id', key: 'id',
      render: (v: string) => <Text copyable style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>{v?.slice(0, 8)}...</Text>,
    },
    {
      title: '来源', dataIndex: 'source', key: 'source',
      render: (v: string) => <Tag>{SOURCE_LABELS[v] ?? v}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: '时间', dataIndex: 'createdAt', key: 'createdAt',
      render: (v: string) => v ? dayjs(v).format('MM-DD HH:mm') : '-',
    },
  ]

  const parseParts = (parts: string): string => {
    try {
      const arr = JSON.parse(parts)
      return arr.map((p: { type: string; text?: string }) => p.text ?? '').join(' ').slice(0, 200)
    } catch {
      return parts?.slice(0, 200) ?? ''
    }
  }

  return (
    <div>
      <h2 style={{ fontFamily: tokens.font.display, marginBottom: 24 }}>对话记录</h2>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={conversations}
        loading={isLoading}
        pagination={false}
        onRow={(r) => ({
          onClick: () => loadMessages(r.id!),
          style: { cursor: 'pointer' },
        })}
        locale={{ emptyText: '暂无对话记录' }}
      />

      <Drawer
        title="消息流"
        open={selectedId !== null}
        onClose={() => { setSelectedId(null); setMessages([]) }}
        width={560}
      >
        {msgLoading ? <Spin /> : messages.length === 0 ? <Empty description="暂无消息" /> : (
          <Timeline
            items={messages.map((m, i) => ({
              key: i,
              color: m.role === 'user' ? 'blue' : m.role === 'orchestrator' ? tokens.color.anchor : 'green',
              children: (
                <div>
                  <Tag style={{ marginBottom: 4 }}>{m.role}{m.agentId ? ` (${m.agentId.slice(0, 6)})` : ''}</Tag>
                  <div style={{ fontFamily: tokens.font.mono, fontSize: 13, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {parseParts(m.parts)}
                  </div>
                  <div style={{ fontSize: 11, color: tokens.color.muted, marginTop: 4 }}>
                    {dayjs(m.createdAt).format('HH:mm:ss')}
                  </div>
                </div>
              ),
            }))}
          />
        )}
      </Drawer>
    </div>
  )
}
