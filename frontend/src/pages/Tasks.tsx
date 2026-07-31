import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Table, Tag, Select, Button, Modal, App, Space, Spin, Empty,
} from 'antd'
import { taskApi, type TaskItem } from '@/api/tasks'
import { tokens } from '@/theme/tokens'

const { Title, Text } = Typography

const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'pending', value: 'pending' },
  { label: 'working', value: 'working' },
  { label: 'input_required', value: 'input_required' },
  { label: 'completed', value: 'completed' },
  { label: 'failed', value: 'failed' },
]

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  completed: { color: tokens.color.ok, label: 'completed' },
  working: { color: tokens.color.anchor, label: 'working' },
  failed: { color: tokens.color.critical, label: 'failed' },
  input_required: { color: tokens.color.warn, label: 'input_required' },
  pending: { color: tokens.color.muted, label: 'pending' },
}

export default function Tasks() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [statusFilter, setStatusFilter] = useState('')
  const [detailOpen, setDetailOpen] = useState(false)
  const [selectedTask, setSelectedTask] = useState<TaskItem | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['tasks', statusFilter],
    queryFn: () => taskApi.page(1, 100, statusFilter || undefined),
  })
  const tasks = data?.items ?? []

  function viewDetail(task: TaskItem) {
    setSelectedTask(task)
    setDetailOpen(true)
  }

  function confirmCancel(task: TaskItem) {
    modal.confirm({
      title: `取消任务`,
      content: `确定要取消任务 ${task.id.slice(0, 8)}... 吗？`,
      okType: 'danger',
      okText: '取消任务',
      cancelText: '返回',
      onOk: async () => {
        try {
          await taskApi.cancel(task.id)
          message.success('任务已取消')
          qc.invalidateQueries({ queryKey: ['tasks'] })
        } catch (e) {
          message.error((e as Error).message)
        }
      },
    })
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      render: (id: string) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
          {id.length > 12 ? id.slice(0, 12) + '...' : id}
        </Text>
      ),
    },
    {
      title: 'Agent',
      dataIndex: 'assignedAgentId',
      key: 'assignedAgentId',
      render: (id: string) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
          {id ? (id.length > 12 ? id.slice(0, 12) + '...' : id) : '—'}
        </Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: string) => {
        const s = STATUS_TAG[status] ?? STATUS_TAG.pending
        return (
          <Tag bordered={false} color={s?.color}
            style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
            {s?.label ?? status}
          </Tag>
        )
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 11, color: tokens.color.muted }}>
          {v ? new Date(v).toLocaleString() : ''}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_: unknown, record: TaskItem) => (
        <Space size="small">
          <Button size="small" type="link" onClick={() => viewDetail(record)}>
            详情
          </Button>
          {record.status !== 'completed' && record.status !== 'failed' && (
            <Button size="small" type="link" danger onClick={() => confirmCancel(record)}>
              取消
            </Button>
          )}
        </Space>
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
          任务监控
        </Title>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // A2A Task 列表 + artifacts / 状态转换日志
        </Text>
      </div>

      <div style={{ marginBottom: 16 }}>
        <Select
          value={statusFilter}
          onChange={setStatusFilter}
          options={STATUS_OPTIONS}
          style={{ width: 180 }}
          placeholder="筛选状态"
        />
      </div>

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : tasks.length === 0
          ? <Empty description="暂无任务数据" />
          : <Table
            dataSource={tasks}
            columns={columns}
            rowKey="id"
            pagination={false}
            style={{
              border: `1px solid ${tokens.color.border}`,
              borderRadius: tokens.radius.md,
              overflow: 'hidden',
            }}
          />
      }

      <Modal
        title="任务详情"
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={520}
      >
        {selectedTask && (
          <div style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">ID: </Text>
              <Text>{selectedTask.id}</Text>
            </div>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">状态: </Text>
              <Tag bordered={false}
                color={STATUS_TAG[selectedTask.status]?.color}>
                {selectedTask.status}
              </Tag>
            </div>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">Agent: </Text>
              <Text>{selectedTask.assignedAgentId || '—'}</Text>
            </div>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">Context: </Text>
              <Text>{selectedTask.contextId || '—'}</Text>
            </div>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">创建时间: </Text>
              <Text>{selectedTask.createdAt ? new Date(selectedTask.createdAt).toLocaleString() : ''}</Text>
            </div>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">更新时间: </Text>
              <Text>{selectedTask.updatedAt ? new Date(selectedTask.updatedAt).toLocaleString() : ''}</Text>
            </div>
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">完成时间: </Text>
              <Text>{selectedTask.completedAt ? new Date(selectedTask.completedAt).toLocaleString() : '—'}</Text>
            </div>
            {selectedTask.errorDetail && (
              <div style={{ marginBottom: 8 }}>
                <Text type="secondary">错误信息: </Text>
                <Text type="danger">{selectedTask.errorDetail}</Text>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
