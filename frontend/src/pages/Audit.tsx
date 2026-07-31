import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Typography, Table, Tag, Input, Select, Spin, Empty,
} from 'antd'
import { auditApi, type AuditLog } from '@/api/audit'
import { tokens } from '@/theme/tokens'

const { Title, Text } = Typography

const ACTION_OPTIONS = [
  { label: '全部操作', value: '' },
  { label: 'create', value: 'create' },
  { label: 'update', value: 'update' },
  { label: 'delete', value: 'delete' },
  { label: 'read', value: 'read' },
  { label: 'approve', value: 'approve' },
  { label: 'reject', value: 'reject' },
  { label: 'execute', value: 'execute' },
  { label: 'cancel', value: 'cancel' },
  { label: 'login', value: 'login' },
  { label: 'logout', value: 'logout' },
]

const ACTION_COLORS: Record<string, string> = {
  create: tokens.color.ok,
  update: tokens.color.anchorSoft,
  delete: tokens.color.critical,
  read: tokens.color.muted,
  approve: tokens.color.ok,
  reject: tokens.color.critical,
  execute: tokens.color.anchor,
  cancel: tokens.color.warn,
  login: tokens.color.ok,
  logout: tokens.color.muted,
}

export default function Audit() {
  const [actorFilter, setActorFilter] = useState('')
  const [actionFilter, setActionFilter] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['audit', actorFilter, actionFilter],
    queryFn: () => auditApi.page(1, 100, actorFilter || undefined, actionFilter || undefined),
  })
  const logs = data?.items ?? []

  const columns = [
    {
      title: 'Actor ID',
      dataIndex: 'actorId',
      key: 'actorId',
      width: 160,
      render: (id: string) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
          {id.length > 14 ? id.slice(0, 14) + '...' : id}
        </Text>
      ),
    },
    {
      title: 'Action',
      dataIndex: 'action',
      key: 'action',
      width: 110,
      render: (action: string) => {
        const color = ACTION_COLORS[action] ?? tokens.color.muted
        return (
          <Tag bordered={false} color={color}
            style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
            {action}
          </Tag>
        )
      },
    },
    {
      title: 'Entity',
      key: 'entity',
      width: 200,
      render: (_: unknown, record: AuditLog) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
          <Text style={{ color: tokens.color.muted }}>{record.entityType}</Text>
          {' / '}
          <Text style={{ color: tokens.color.ink }}>
            {record.entityId.length > 14 ? record.entityId.slice(0, 14) + '...' : record.entityId}
          </Text>
        </Text>
      ),
    },
    {
      title: 'Details',
      dataIndex: 'details',
      key: 'details',
      render: (details: string) => (
        <Text style={{
          fontFamily: tokens.font.mono, fontSize: 10, color: tokens.color.muted,
          maxWidth: 300, display: 'inline-block',
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>
          {details || '—'}
        </Text>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v: string) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 11, color: tokens.color.muted }}>
          {v ? new Date(v).toLocaleString() : ''}
        </Text>
      ),
    },
  ]

  return (
    <div>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
        marginBottom: 24,
      }}>
        <Title level={2} style={{
          fontFamily: tokens.font.display, letterSpacing: '-0.02em', margin: 0,
        }}>
          审计日志
        </Title>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // 终端样式 monospace 日志流
        </Text>
      </div>

      <div style={{ marginBottom: 16, display: 'flex', gap: 12 }}>
        <Input
          placeholder="按 Actor ID 筛选"
          value={actorFilter}
          onChange={e => setActorFilter(e.target.value)}
          style={{ width: 220, fontFamily: tokens.font.mono, fontSize: 12 }}
          allowClear
        />
        <Select
          value={actionFilter}
          onChange={setActionFilter}
          options={ACTION_OPTIONS}
          style={{ width: 160 }}
          placeholder="按操作筛选"
        />
      </div>

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : logs.length === 0
          ? <Empty description="暂无审计日志" />
          : <Table
            dataSource={logs}
            columns={columns}
            rowKey="id"
            pagination={false}
            size="small"
            style={{
              border: `1px solid ${tokens.color.border}`,
              borderRadius: tokens.radius.md,
              overflow: 'hidden',
              background: tokens.color.surface,
            }}
          />
      }
    </div>
  )
}
