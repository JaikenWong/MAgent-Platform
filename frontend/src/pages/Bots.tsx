import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Button, Empty, Spin, Table, Tag, Form, Input, Select, Switch,
  Drawer, App, Tooltip, Space,
} from 'antd'
import {
  PlusOutlined, DeleteOutlined, EditOutlined, CopyOutlined,
} from '@ant-design/icons'
import { botApi, type FeishuBot } from '@/api/bots'
import { agentApi } from '@/api/agents'
import { tokens } from '@/theme/tokens'

const { Title, Text } = Typography

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  active: { color: tokens.color.ok, label: 'active' },
  inactive: { color: tokens.color.muted, label: 'inactive' },
}

export default function Bots() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<FeishuBot | null>(null)
  const [form] = Form.useForm()

  const { data, isLoading } = useQuery({
    queryKey: ['bots'],
    queryFn: () => botApi.page(1, 100),
  })
  const bots = data?.items ?? []

  const { data: agents } = useQuery({
    queryKey: ['agents-all'],
    queryFn: () => agentApi.all(),
  })

  const saveMut = useMutation({
    mutationFn: (b: FeishuBot) => b.id
      ? botApi.update(b.id, b)
      : botApi.create(b),
    onSuccess: () => {
      message.success('已保存')
      setFormOpen(false)
      form.resetFields()
      qc.invalidateQueries({ queryKey: ['bots'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const delMut = useMutation({
    mutationFn: (id: string) => botApi.remove(id),
    onSuccess: () => { message.success('已删除'); qc.invalidateQueries({ queryKey: ['bots'] }) },
    onError: (e: Error) => message.error(e.message),
  })

  function openNew() {
    setEditing({ name: '', status: 'active' })
    form.resetFields()
    setFormOpen(true)
  }

  function openEdit(bot: FeishuBot) {
    setEditing({ ...bot, appSecret: '', verificationToken: '', encryptKey: '' })
    form.setFieldsValue({ ...bot, appSecret: '', verificationToken: '', encryptKey: '' })
    setFormOpen(true)
  }

  function save(values: FeishuBot) {
    const payload: FeishuBot = { ...editing, ...values, id: editing?.id }
    saveMut.mutate(payload)
  }

  function confirmDelete(bot: FeishuBot) {
    modal.confirm({
      title: `删除机器人 "${bot.name}"`,
      content: '确定删除此飞书机器人？此操作不可撤销。',
      okType: 'danger',
      okText: '删除',
      cancelText: '取消',
      onOk: () => delMut.mutate(bot.id!),
    })
  }

  function copyWebhookUrl(botId: string) {
    const url = `${window.location.origin}/webhook/feishu/${botId}`
    navigator.clipboard.writeText(url).then(
      () => message.success('Webhook URL 已复制到剪贴板'),
      () => message.error('复制失败'),
    )
  }

  function getBoundAgentName(agentId?: string) {
    if (!agentId || !agents) return '—'
    const agent = agents.find(a => a.id === agentId)
    return agent?.name ?? agentId
  }

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
      title: 'App ID',
      dataIndex: 'appId',
      key: 'appId',
      render: (appId: string) => (
        <Text style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          {appId || '—'}
        </Text>
      ),
    },
    {
      title: '关联 Agent',
      dataIndex: 'boundAgentId',
      key: 'boundAgentId',
      render: (id: string) => {
        const name = getBoundAgentName(id)
        return <Text type={id ? undefined : 'secondary'}>{name}</Text>
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const s = STATUS_TAG[status ?? 'inactive']
        return (
          <Tag bordered={false} color={s?.color}
            style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>
            {s?.label ?? status}
          </Tag>
        )
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_: unknown, record: FeishuBot) => (
        <Space size="small">
          <Tooltip title="复制 Webhook URL">
            <Button size="small" type="text" icon={<CopyOutlined />}
              onClick={() => copyWebhookUrl(record.id!)} />
          </Tooltip>
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
          飞书机器人
        </Title>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // 飞书 webhook 通道管理 · 列表 + 配置表单
        </Text>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openNew}
          style={{ background: tokens.color.anchor, borderColor: tokens.color.anchor }}>
          新建机器人
        </Button>
      </div>

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : bots.length === 0
          ? <Empty description="还没有飞书机器人 — 点击右上角新建" />
          : <Table
            dataSource={bots}
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

      {/* 表单 */}
      <Drawer
        title={editing?.id ? '编辑机器人' : '新建机器人'}
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
            <Input placeholder="如 内部通知机器人" />
          </Form.Item>
          <Form.Item label="App ID" name="appId" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="cli_xxxxxx" />
          </Form.Item>
          <Form.Item label="App Secret" name="appSecret"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              编辑时留空则保留原密钥
            </Text>}>
            <Input.Password placeholder="从飞书应用获取" />
          </Form.Item>
          <Form.Item label="Verification Token" name="verificationToken"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              编辑时留空则保留原值
            </Text>}>
            <Input placeholder="飞书事件订阅 Token" />
          </Form.Item>
          <Form.Item label="Encrypt Key" name="encryptKey"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              编辑时留空则保留原值
            </Text>}>
            <Input placeholder="飞书消息加密 Key" />
          </Form.Item>
          <Form.Item label="关联 Agent" name="boundAgentId">
            <Select
              allowClear
              placeholder="选择要绑定的 Agent"
              options={(agents ?? []).map(a => ({
                label: a.name,
                value: a.id,
              }))}
            />
          </Form.Item>
          <Form.Item label="状态" name="status" valuePropName="checked"
            initialValue="active"
            getValueFromEvent={(checked: boolean) => checked ? 'active' : 'inactive'}
            getValueProps={(value: string) => ({ checked: value === 'active' })}>
            <Switch checkedChildren="active" unCheckedChildren="inactive" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
