import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Button, Table, Tag, Form, Input, Select,
  InputNumber, Drawer, Switch, Space, App, Tooltip,
} from 'antd'
import {
  PlusOutlined, DeleteOutlined, EditOutlined,
} from '@ant-design/icons'
import { ruleApi, type OrchestrationRule } from '@/api/rules'
import { agentApi } from '@/api/agents'
import { tokens } from '@/theme/tokens'

const { Text } = Typography

const TRIGGER_TYPES = [
  { label: '关键词', value: 'keyword' },
  { label: '正则', value: 'regex' },
  { label: '意图', value: 'intent' },
  { label: '手动', value: 'manual' },
  { label: '全部', value: 'all' },
]

const EXECUTION_MODES = [
  { label: '顺序执行', value: 'sequential' },
  { label: '并行执行', value: 'parallel' },
  { label: '条件执行', value: 'conditional' },
  { label: '路由器', value: 'router' },
]

export default function Rules() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<OrchestrationRule | null>(null)
  const [form] = Form.useForm()

  const { data, isLoading } = useQuery({
    queryKey: ['rules'],
    queryFn: () => ruleApi.page(1, 100),
  })
  const rules = data?.items ?? []

  const { data: agents } = useQuery({
    queryKey: ['agents'],
    queryFn: () => agentApi.page(1, 100),
  })
  const agentList = agents?.items ?? []

  const saveMut = useMutation({
    mutationFn: (b: OrchestrationRule) => b.id
      ? ruleApi.update(b.id, b)
      : ruleApi.create(b),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rules'] })
      message.success('保存成功')
      setFormOpen(false)
      setEditing(null)
    },
    onError: (e: Error) => message.error(e.message),
  })

  const delMut = useMutation({
    mutationFn: (id: string) => ruleApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rules'] })
      message.success('删除成功')
    },
    onError: (e: Error) => message.error(e.message),
  })

  const openNew = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ priority: 0, enabled: true, executionMode: 'sequential' })
    setFormOpen(true)
  }

  const openEdit = (r: OrchestrationRule) => {
    setEditing(r)
    try {
      form.setFieldsValue({
        ...r,
        agentChain: r.agentChain ? JSON.stringify(JSON.parse(r.agentChain)) : undefined,
      })
    } catch {
      form.setFieldsValue({ ...r })
    }
    setFormOpen(true)
  }

  const handleSave = () => {
    form.validateFields().then(vals => {
      const body: OrchestrationRule = { ...vals }
      if (typeof body.agentChain === 'string') {
        try { body.agentChain = JSON.stringify(JSON.parse(body.agentChain)) } catch {}
      } else if (body.agentChain) {
        body.agentChain = JSON.stringify(body.agentChain)
      }
      saveMut.mutate(body)
    })
  }

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name', render: (v: string) => <Text strong>{v}</Text> },
    { title: '触发', dataIndex: 'triggerType', key: 'triggerType',
      render: (v: string) => <Tag>{v}</Tag> },
    { title: '模式', dataIndex: 'executionMode', key: 'executionMode',
      render: (v: string) => <Tag color="purple">{v}</Tag> },
    {
      title: '状态', dataIndex: 'enabled', key: 'enabled',
      render: (v: boolean) => <Tag color={v ? tokens.color.ok : tokens.color.muted}>{v ? '启用' : '禁用'}</Tag>,
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority' },
    {
      title: '操作', key: 'actions',
      render: (_: unknown, r: OrchestrationRule) => (
        <Space>
          <Tooltip title="编辑"><Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} /></Tooltip>
          <Tooltip title="删除"><Button size="small" danger icon={<DeleteOutlined />} onClick={() => delMut.mutate(r.id!)} /></Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
        <h2 style={{ fontFamily: tokens.font.display }}>编排规则</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={openNew}
          style={{ background: tokens.color.anchor }}>
          新建规则
        </Button>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={rules}
        loading={isLoading}
        pagination={false}
        locale={{ emptyText: '暂无编排规则' }}
      />

      <Drawer
        title={editing ? '编辑规则' : '新建规则'}
        open={formOpen}
        onClose={() => { setFormOpen(false); setEditing(null) }}
        width={560}
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={() => { setFormOpen(false); setEditing(null) }}>取消</Button>
            <Button type="primary" loading={saveMut.isPending} onClick={handleSave}
              style={{ background: tokens.color.anchor }}>保存</Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="规则名称" />
          </Form.Item>

          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="规则描述" />
          </Form.Item>

          <Form.Item name="triggerType" label="触发类型" rules={[{ required: true }]}>
            <Select options={TRIGGER_TYPES} />
          </Form.Item>

          <Form.Item name="triggerConfig" label="触发配置 (JSON)"
            extra='{"keywords": ["分析","报告"]} 或 {"regex": ".*分析.*"}'>
            <Input.TextArea rows={3} placeholder='{"keywords": ["关键词1","关键词2"]}' />
          </Form.Item>

          <Form.Item name="executionMode" label="执行模式" rules={[{ required: true }]}>
            <Select options={EXECUTION_MODES} />
          </Form.Item>

          <Form.Item name="agentChain" label="Agent 链 (JSON 数组)"
            extra='[{"agentId":"uuid","role":"研究员","inputFrom":"user"}]'>
            <Input.TextArea rows={4} placeholder='[{"agentId":"...","role":"role","inputFrom":"user"}]' />
          </Form.Item>

          <Form.Item name="fallbackAgentId" label="兜底 Agent">
            <Select
              allowClear
              placeholder="选择兜底 Agent"
              options={agentList.map(a => ({ label: a.name, value: a.id }))}
            />
          </Form.Item>

          <Form.Item name="priority" label="优先级">
            <InputNumber min={0} max={1000} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
