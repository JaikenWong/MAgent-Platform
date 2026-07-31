import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Button, Empty, Spin, Tabs, Tag, Modal, Input, App, Space,
} from 'antd'
import {
  CheckOutlined, CloseOutlined,
} from '@ant-design/icons'
import { approvalApi, type Approval } from '@/api/approvals'
import { tokens } from '@/theme/tokens'

const { Title, Text, Paragraph } = Typography

const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  pending: { color: tokens.color.warn, label: 'Pending' },
  approved: { color: tokens.color.ok, label: 'Approved' },
  rejected: { color: tokens.color.critical, label: 'Rejected' },
}

const TAB_ITEMS = [
  { key: '', label: 'All' },
  { key: 'pending', label: 'Pending' },
  { key: 'approved', label: 'Approved' },
  { key: 'rejected', label: 'Rejected' },
]

function formatTime(ts?: string) {
  if (!ts) return '—'
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ts
  }
}

function truncate(s: string, max = 120) {
  if (!s) return '—'
  return s.length > max ? s.slice(0, max) + '…' : s
}

export default function Approvals() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [statusFilter, setStatusFilter] = useState('')
  const [decideModal, setDecideModal] = useState<{ approval: Approval; decision: 'approved' | 'rejected' } | null>(null)
  const [comment, setComment] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['approvals', statusFilter],
    queryFn: () => approvalApi.page(1, 200, statusFilter || undefined),
  })
  const items = data?.items ?? []

  const decideMut = useMutation({
    mutationFn: ({ id, decision, comment }: { id: string; decision: string; comment?: string }) =>
      approvalApi.decide(id, decision, comment),
    onSuccess: () => {
      message.success('操作成功')
      setDecideModal(null)
      setComment('')
      qc.invalidateQueries({ queryKey: ['approvals'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  function handleDecide() {
    if (!decideModal) return
    decideMut.mutate({
      id: decideModal.approval.id!,
      decision: decideModal.decision,
      comment: comment || undefined,
    })
  }

  function openDecide(approval: Approval, decision: 'approved' | 'rejected') {
    setDecideModal({ approval, decision })
    setComment('')
  }

  const pendingCount = items.filter(i => i.status === 'pending').length

  return (
    <div>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
        marginBottom: 24,
      }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
          <Title level={2} style={{
            fontFamily: tokens.font.display, letterSpacing: '-0.02em', margin: 0,
          }}>
            审批队列
          </Title>
          {pendingCount > 0 && (
            <Tag bordered={false} color="warning" style={{
              fontFamily: tokens.font.mono, fontSize: 12, borderRadius: 99,
            }}>
              {pendingCount} pending
            </Tag>
          )}
        </div>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // Linear inbox 风格 · 快速审批
        </Text>
      </div>

      <Tabs
        activeKey={statusFilter}
        onChange={setStatusFilter}
        items={TAB_ITEMS.map(tab => ({
          key: tab.key,
          label: tab.label,
        }))}
        style={{ marginBottom: 16 }}
      />

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : items.length === 0
          ? <Empty description="没有审批单" />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {items.map(item => (
                <ApprovalCard
                  key={item.id}
                  approval={item}
                  onApprove={() => openDecide(item, 'approved')}
                  onReject={() => openDecide(item, 'rejected')}
                  decideLoading={decideMut.isPending}
                />
              ))}
            </div>
      }

      {/* 审批确认 Modal */}
      <Modal
        title={
          <span style={{ fontFamily: tokens.font.display }}>
            {decideModal?.decision === 'approved'
              ? <><CheckOutlined style={{ color: tokens.color.ok, marginRight: 8 }} /> 批准</>
              : <><CloseOutlined style={{ color: tokens.color.critical, marginRight: 8 }} /> 拒绝</>
            }
          </span>
        }
        open={!!decideModal}
        onOk={handleDecide}
        onCancel={() => setDecideModal(null)}
        confirmLoading={decideMut.isPending}
        okText={decideModal?.decision === 'approved' ? '批准' : '拒绝'}
        okButtonProps={{
          danger: decideModal?.decision === 'rejected',
          style: decideModal?.decision === 'approved'
            ? { background: tokens.color.ok, borderColor: tokens.color.ok }
            : undefined,
        }}
        width={480}
        destroyOnClose
      >
        {decideModal && (
          <div>
            <div style={{
              background: tokens.color.surfaceAlt, padding: 12, borderRadius: tokens.radius.md,
              marginBottom: 16, border: `1px solid ${tokens.color.border}`,
            }}>
              <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
                TASK {decideModal.approval.taskId?.slice(0, 12)}…
              </Text>
              <br />
              <Text strong style={{ fontSize: 15, fontFamily: tokens.font.sans }}>
                {decideModal.approval.skillName}
              </Text>
            </div>
            <Input.TextArea
              rows={3}
              value={comment}
              onChange={e => setComment(e.target.value)}
              placeholder={`可选：${decideModal.decision === 'approved' ? '批准' : '拒绝'}原因...`}
            />
          </div>
        )}
      </Modal>
    </div>
  )
}

function ApprovalCard(p: {
  approval: Approval
  onApprove: () => void
  onReject: () => void
  decideLoading: boolean
}) {
  const { approval: a } = p
  const isPending = a.status === 'pending'
  const cfg = STATUS_CONFIG[a.status ?? 'pending']

  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 16,
      border: `1px solid ${tokens.color.border}`,
      borderRadius: tokens.radius.md,
      padding: 16,
      background: isPending ? tokens.color.surface : tokens.color.surfaceAlt,
      opacity: isPending ? 1 : 0.75,
      transition: 'background .15s',
    }}>
      {/* 左边状态标识 */}
      <div style={{
        width: 10, height: 10, borderRadius: '50%',
        background: cfg.color,
        marginTop: 6, flexShrink: 0,
      }} />

      {/* 中间内容 */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <Text strong style={{ fontSize: 15, fontFamily: tokens.font.sans }}>
            {a.skillName || '(unnamed)'}
          </Text>
          <Tag bordered={false}
            color={isPending ? 'warning' : a.status === 'approved' ? 'success' : 'error'}
            style={{ fontFamily: tokens.font.mono, fontSize: 11, borderRadius: 99 }}>
            {cfg.label}
          </Tag>
        </div>
        <div style={{ display: 'flex', gap: 16, marginBottom: 6 }}>
          <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
            {a.taskId?.slice(0, 12)}…
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {formatTime(a.createdAt)}
          </Text>
        </div>
        {a.payload && (
          <Paragraph type="secondary" style={{
            fontSize: 12, marginBottom: 0,
            fontFamily: tokens.font.mono,
            lineClamp: 2, display: '-webkit-box',
            WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
            wordBreak: 'break-all',
          }}>
            {truncate(a.payload, 160)}
          </Paragraph>
        )}
        {a.comment && (
          <Paragraph type="secondary" style={{
            fontSize: 12, marginTop: 6, marginBottom: 0,
            fontStyle: 'italic', paddingLeft: 12,
            borderLeft: `2px solid ${tokens.color.border}`,
          }}>
            {a.comment}
          </Paragraph>
        )}
      </div>

      {/* 右边操作 */}
      {isPending && (
        <Space style={{ flexShrink: 0, marginTop: 2 }}>
          <Button
            size="small"
            type="dashed"
            icon={<CloseOutlined />}
            danger
            onClick={p.onReject}
            loading={p.decideLoading}
          >
            拒绝
          </Button>
          <Button
            size="small"
            icon={<CheckOutlined />}
            onClick={p.onApprove}
            loading={p.decideLoading}
            style={{ background: tokens.color.ok, borderColor: tokens.color.ok, color: '#fff' }}
          >
            批准
          </Button>
        </Space>
      )}
    </div>
  )
}
