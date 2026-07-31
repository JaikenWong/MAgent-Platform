import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Button, Empty, Spin, Table, Tag, Form, Input, Select, Switch,
  InputNumber, Drawer, App, Tooltip, Space,
} from 'antd'
import {
  PlusOutlined, DeleteOutlined, EditOutlined,
} from '@ant-design/icons'
import { policyApi, type ApprovalPolicy } from '@/api/approvalPolicies'
import { tokens } from '@/theme/tokens'

const { Title, Text } = Typography
const { TextArea } = Input

const STRATEGY_OPTIONS = [
  { label: 'auto', value: 'auto' },
  { label: 'notify', value: 'notify' },
  { label: 'require_one', value: 'require_one' },
  { label: 'require_quorum', value: 'require_quorum' },
  { label: 'require_role', value: 'require_role' },
]

const TIMEOUT_ACTION_OPTIONS = [
  { label: 'auto_reject', value: 'auto_reject' },
  { label: 'escalate', value: 'escalate' },
]

export default function ApprovalPolicies() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ApprovalPolicy | null>(null)
  const [form] = Form.useForm()

  const { data, isLoading } = useQuery({
    queryKey: ['approval-policies'],
    queryFn: () => policyApi.all(),
  })
  const policies = data ?? []

  const saveMut = useMutation({
    mutationFn: (b: ApprovalPolicy) => b.id
      ? policyApi.update(b.id, b)
      : policyApi.create(b),
    onSuccess: () => {
      message.success('已保存')
      setFormOpen(false)
      form.resetFields()
      qc.invalidateQueries({ queryKey: ['approval-policies'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const delMut = useMutation({
    mutationFn: (id: string) => policyApi.remove(id),
    onSuccess: () => { message.success('已删除'); qc.invalidateQueries({ queryKey: ['approval-policies'] }) },
    onError: (e: Error) => message.error(e.message),
  })

  function openNew() {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ timeoutSeconds: 1800, enabled: true })
    setFormOpen(true)
  }

  function openEdit(policy: ApprovalPolicy) {
    setEditing(policy)
    form.setFieldsValue(policy)
    setFormOpen(true)
  }

  function save(values: ApprovalPolicy) {
    const payload: ApprovalPolicy = { ...editing, ...values, id: editing?.id }
    saveMut.mutate(payload)
  }

  function confirmDelete(policy: ApprovalPolicy) {
    modal.confirm({
      title: `删除策略 "${policy.name}"`,
      content: '确定删除此审批策略？此操作不可撤销。',
      okType: 'danger',
      okText: '删除',
      cancelText: '取消',
      onOk: () => delMut.mutate(policy.id!),
    })
  }

  const strategy = Form.useWatch('strategy', form)

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Text strong style={{ fontFamily: tokens.font.sans }}>{name}</Text>
      ),
    },
    {
      title: '策略',
      dataIndex: 'strategy',
      key: 'strategy',
      width: 140,
      render: (strategy: string) => (
        <Tag bordered={false} color={tokens.color.anchorSoft}
          style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
          {strategy}
        </Tag>
      ),
    },
    {
      title: '超时(s)',
      dataIndex: 'timeoutSeconds',
      key: 'timeoutSeconds',
      width: 90,
      render: (v: number) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>{v ?? 1800}</Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (enabled: boolean) => (
        <Tag bordered={false}
          color={enabled ? tokens.color.ok : tokens.color.muted}
          style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
          {enabled ? 'on' : 'off'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_: unknown, record: ApprovalPolicy) => (
        <Space size="small">
          <Tooltip title="编辑">
            <Button size="small" type="text" icon={<EditOutlined />}
              onClick={() => openEdit(record)} />
          </Tooltip>
          <Tooltip title="删除">
            <Button size="small" type="text" danger icon={<DeleteOutlined />}
              onClick={() => confirmDelete(record)} />
          </Tooltip>
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
          审批策略
        </Title>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // auto / notify / require_one / require_quorum / require_role
        </Text>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openNew}
          style={{ background: tokens.color.anchor, borderColor: tokens.color.anchor }}>
          新建策略
        </Button>
      </div>

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : policies.length === 0
          ? <Empty description="还没有审批策略 — 点击右上角新建" />
          : <Table
            dataSource={policies}
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

      <Drawer
        title={editing?.id ? '编辑策略' : '新建策略'}
        open={formOpen}
        onClose={() => setFormOpen(false)}
        width={520}
        destroyOnClose
        extra={
          <Button type="primary" loading={saveMut.isPending} onClick={() => form.submit()}
            style={{ background: tokens.color.anchor, borderColor: tokens.color.anchor }}>
            保存
          </Button>
        }
      >
        <Form form={form} layout="vertical" onFinish={save} requiredMark={false}>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如 高危操作审批" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <TextArea rows={2} placeholder="可选描述" />
          </Form.Item>
          <Form.Item label="策略" name="strategy" rules={[{ required: true, message: '必填' }]}>
            <Select options={STRATEGY_OPTIONS} placeholder="选择策略类型" />
          </Form.Item>
          {strategy === 'require_quorum' && (
            <Form.Item label="Quorum" name="quorum" rules={[{ required: true, message: '必填' }]}>
              <InputNumber min={1} placeholder="最少审批人数" style={{ width: '100%' }} />
            </Form.Item>
          )}
          {strategy === 'require_role' && (
            <Form.Item label="Required Role" name="requiredRole" rules={[{ required: true, message: '必填' }]}>
              <Input placeholder="如 admin / manager" />
            </Form.Item>
          )}
          <Form.Item label="超时秒数" name="timeoutSeconds" initialValue={1800}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="超时动作" name="timeoutAction">
            <Select options={TIMEOUT_ACTION_OPTIONS} placeholder="超时后动作" allowClear />
          </Form.Item>
          <Form.Item label="适用范围" name="appliesTo"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>JSON 格式</Text>}>
            <TextArea rows={3} placeholder='如 {"types":["tool_call","message"]}' />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
