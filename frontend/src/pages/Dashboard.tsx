import { useQuery } from '@tanstack/react-query'
import { Typography, Spin, Alert, Tag } from 'antd'
import { dashboardApi } from '@/api/dashboard'
import { tokens } from '@/theme/tokens'

const { Title, Text } = Typography

const BAR_COLORS = [
  tokens.color.anchor,
  tokens.color.anchorSoft,
  tokens.color.ok,
  tokens.color.warn,
  tokens.color.critical,
]

export default function Dashboard() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats'],
    queryFn: dashboardApi.stats,
  })

  const stats = [
    { key: 'agentCount', label: '已注册 Agent', value: data?.agentCount ?? 0 },
    { key: 'taskCountToday', label: '今日任务', value: data?.taskCountToday ?? 0 },
    { key: 'pendingApprovals', label: '待审批', value: data?.pendingApprovals ?? 0 },
    { key: 'feishuMessageCount', label: '飞书消息', value: data?.feishuMessageCount ?? 0 },
  ]

  const distributionEntries = data?.taskDistribution
    ? Object.entries(data.taskDistribution)
    : []

  const conversations = data?.recentConversations ?? []

  return (
    <div>
      <Title level={2} style={{
        fontFamily: tokens.font.display, letterSpacing: '-0.02em',
        marginBottom: 32,
      }}>面板</Title>

      {error && <Alert type="error" message="加载失败, 如未登录请重新登录" style={{ marginBottom: 24 }} />}

      {isLoading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} style={{ padding: 24, textAlign: 'center' }}>
              <Spin />
            </div>
          ))}
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
          {stats.map(s => (
            <div key={s.key} style={{
              borderLeft: `3px solid ${tokens.color.anchor}`,
              padding: '4px 0 4px 16px',
            }}>
              <div style={{
                fontFamily: tokens.font.display, fontSize: 48, fontWeight: 700,
                lineHeight: 1, color: tokens.color.ink,
                letterSpacing: '-0.04em',
              }}>
                {s.value}
              </div>
              <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
                {s.label}
              </Text>
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        {/* Task Distribution */}
        <div style={{
          padding: 24,
          background: tokens.color.surface,
          border: `1px solid ${tokens.color.border}`,
          borderRadius: tokens.radius.lg,
        }}>
          <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
            TASK DISTRIBUTION
          </Text>
          {isLoading ? (
            <div style={{ padding: '24px 0', textAlign: 'center' }}><Spin /></div>
          ) : distributionEntries.length === 0 ? (
            <div style={{ padding: '24px 0', textAlign: 'center' }}>
              <Text type="secondary">暂无数据</Text>
            </div>
          ) : (
            <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {distributionEntries.map(([status, count], i) => {
                const maxCount = Math.max(...distributionEntries.map(([, c]) => c), 1)
                const pct = Math.round((count / maxCount) * 100)
                return (
                  <div key={status}>
                    <div style={{
                      display: 'flex', justifyContent: 'space-between',
                      fontFamily: tokens.font.mono, fontSize: 11,
                      marginBottom: 4,
                    }}>
                      <Text style={{ color: tokens.color.ink }}>{status}</Text>
                      <Text style={{ color: tokens.color.muted }}>{count}</Text>
                    </div>
                    <div style={{
                      height: 8, borderRadius: 4,
                      background: tokens.color.border,
                      overflow: 'hidden',
                    }}>
                      <div style={{
                        height: '100%', width: `${pct}%`, borderRadius: 4,
                        background: BAR_COLORS[i % BAR_COLORS.length],
                        transition: 'width 0.4s ease',
                      }} />
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Recent Conversations */}
        <div style={{
          padding: 24,
          background: tokens.color.surface,
          border: `1px solid ${tokens.color.border}`,
          borderRadius: tokens.radius.lg,
        }}>
          <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
            RECENT CONVERSATIONS
          </Text>
          {isLoading ? (
            <div style={{ padding: '24px 0', textAlign: 'center' }}><Spin /></div>
          ) : conversations.length === 0 ? (
            <div style={{ padding: '24px 0', textAlign: 'center' }}>
              <Text type="secondary">暂无对话</Text>
            </div>
          ) : (
            <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {conversations.map(conv => (
                <div key={conv.id} style={{
                  padding: '8px 12px',
                  border: `1px solid ${tokens.color.border}`,
                  borderRadius: tokens.radius.sm,
                }}>
                  <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  }}>
                    <Text style={{
                      fontFamily: tokens.font.mono, fontSize: 11, color: tokens.color.muted,
                    }}>
                      {conv.id.length > 12 ? conv.id.slice(0, 12) + '...' : conv.id}
                    </Text>
                    <Tag bordered={false}
                      color={conv.status === 'completed' ? tokens.color.ok
                        : conv.status === 'working' ? tokens.color.anchor
                        : tokens.color.warn}
                      style={{ fontFamily: tokens.font.mono, fontSize: 10 }}>
                      {conv.status}
                    </Tag>
                  </div>
                  <div style={{
                    display: 'flex', justifyContent: 'space-between', marginTop: 4,
                  }}>
                    <Text style={{ fontSize: 12, color: tokens.color.ink }}>{conv.source}</Text>
                    <Text style={{
                      fontFamily: tokens.font.mono, fontSize: 11, color: tokens.color.muted,
                    }}>
                      {conv.createdAt ? new Date(conv.createdAt).toLocaleString() : ''}
                    </Text>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
