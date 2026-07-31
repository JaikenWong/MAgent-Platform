import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Button, Empty, Spin, Tooltip, Tag, Form, Input,
  Drawer, Modal, App,
} from 'antd'
import {
  PlusOutlined, ExperimentOutlined, CreditCardOutlined, RobotOutlined,
  DeleteOutlined, EditOutlined, LinkOutlined,
} from '@ant-design/icons'
import { agentApi, type Agent, type TestResult, type AgentCard } from '@/api/agents'
import { tokens } from '@/theme/tokens'

const { Title, Text, Paragraph } = Typography

const STATUS_COLOR: Record<string, string> = {
  active: tokens.color.ok,
  inactive: tokens.color.muted,
  error: tokens.color.critical,
}

export default function Agents() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Agent | null>(null)
  const [cardAgent, setCardAgent] = useState<{ agent: Agent; card: AgentCard } | null>(null)
  const [form] = Form.useForm()

  const { data, isLoading } = useQuery({
    queryKey: ['agents'],
    queryFn: () => agentApi.page(1, 100),
  })
  const agents = data?.items ?? []

  const testMut = useMutation({
    mutationFn: (id: string) => agentApi.test(id),
    onSuccess: (r: TestResult) => {
      const msg = r.ok
        ? `连通 ✓ ${(r.answer || '').slice(0, 80) || '无 answer but 流程已通'}`
        : `探测失败: ${r.error} (code ${r.code})`
      message[r.ok ? 'success' : 'error'](msg)
    },
    onError: (e: Error) => message.error(e.message),
  })

  const cardMut = useMutation({
    mutationFn: (id: string) => agentApi.card(id),
    onSuccess: (card: AgentCard) => {
      const a = agents.find(x => x.id === cardMut.variables)
      if (a) setCardAgent({ agent: a, card })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const saveMut = useMutation({
    mutationFn: (b: Agent) => b.id
      ? agentApi.update(b.id, b)
      : agentApi.create(b),
    onSuccess: () => {
      message.success('已保存')
      setFormOpen(false)
      form.resetFields()
      qc.invalidateQueries({ queryKey: ['agents'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  const delMut = useMutation({
    mutationFn: (id: string) => agentApi.remove(id),
    onSuccess: () => { message.success('已删除'); qc.invalidateQueries({ queryKey: ['agents'] }) },
  })

  function openNew() {
    setEditing({ name: '', status: 'active', difyAppId: '', difyApiKey: '', description: '' })
    form.resetFields()
    setFormOpen(true)
  }
  function openEdit(a: Agent) {
    setEditing({ ...a, difyApiKey: '' }) // 编辑时不回填密钥
    form.setFieldsValue({ ...a, difyApiKey: '' })
    setFormOpen(true)
  }
  function save(values: Agent) {
    const payload: Agent = {
      ...editing,
      ...values,
      id: editing?.id,
    } as Agent
    saveMut.mutate(payload)
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 24 }}>
        <Title level={2} style={{
          fontFamily: tokens.font.display, letterSpacing: '-0.02em', margin: 0,
        }}>Agent 管理</Title>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // Hallmark macrostructure: 网格画廊 (Vercel projects 风), 不是表格.
        </Text>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openNew}>新建 Agent</Button>
      </div>

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : agents.length === 0
          ? <Empty description="还没有 Agent — 新建一个 Dify Agent 包装成 A2A Server" />
          : <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: 16,
            }}>
              {agents.map(a => <AgentCardTile
                key={a.id} agent={a}
                onTest={() => testMut.mutate(a.id!)}
                onCard={() => cardMut.mutate(a.id!)}
                onEdit={() => openEdit(a)}
                onDelete={() => delMut.mutate(a.id!)}
                testLoading={testMut.isPending && testMut.variables === a.id}
                cardLoading={cardMut.isPending && cardMut.variables === a.id}
              />)}
            </div>
      }

      {/* 表单 */}
      <Drawer
        title={editing?.id ? '编辑 Agent' : '新建 Agent'}
        open={formOpen}
        onClose={() => setFormOpen(false)}
        width={520}
        destroyOnClose
        extra={<Button type="primary" loading={saveMut.isPending} onClick={() => form.submit()}>保存</Button>}
      >
        <Form form={form} layout="vertical" onFinish={save} requiredMark={false}>
          <Form.Item label="Agent 名称" name="name" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如 研究员 / 撰稿人" />
          </Form.Item>
          <Form.Item label="描述" name="description"><Input.TextArea rows={2} placeholder="这个 Agent 擅长做什么" /></Form.Item>
          <Form.Item label="Dify App 类型" name="difyAppId"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              前缀 chat- = Chatflow/Agent (走 /chat-messages); 其他 = Workflow (走 /workflows/run).
              填 Dify 应用 ID, 形如 `chat-abc123` 或 `workflow-xyz`.
            </Text>}>
            <Input placeholder="chat-xxxx | workflow-xxxx" />
          </Form.Item>
          <Form.Item label="Dify API Key" name="difyApiKey"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              数据库 AES 加密储存; 编辑时留空则保留原 key.
            </Text>}>
            <Input.Password placeholder="app-xxxxxx" />
          </Form.Item>
          <Form.Item label="Dify Base URL" name="difyBaseUrl"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>留空用默认 ({import.meta.env.VITE_DIFY_BASE_URL || 'magent.dify.base-url'})</Text>}>
            <Input placeholder="https://dify.local/v1" />
          </Form.Item>
          <Form.Item label="状态" name="status" initialValue="active">
            <Input placeholder="active | inactive | error" />
          </Form.Item>
          <Form.Item label="Skills JSON" name="skills"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              A2A AgentCard.skills; 空则用默认 skill. 示例: [{`[{"id":"research","name":"竞品分析","tags":["research"]}]`}]
            </Text>}>
            <Input.TextArea rows={3} placeholder='[{"id":"default","name":"general","tags":["general"]}]' />
          </Form.Item>
          <Form.Item label="需审批 Skill" name="approvalSkills"
            extra={<Text type="secondary" style={{ fontSize: 12 }}>
              触发此 skill 时 Task 入 input_required, 等管理员批准. 示例: [{`["send_email","publish_doc"]`}]
            </Text>}>
            <Input.TextArea rows={2} placeholder='["send_email"]' />
          </Form.Item>
        </Form>
      </Drawer>

      {/* Agent Card 预览 */}
      <Modal
        open={!!cardAgent}
        onCancel={() => setCardAgent(null)}
        footer={null}
        width={640}
        title={<span style={{ fontFamily: tokens.font.display }}>
          <CreditCardOutlined /> Agent Card 预览
        </span>}
      >
        {cardAgent && <CardPreview agent={cardAgent.agent} card={cardAgent.card} />}
      </Modal>
    </div>
  )
}

function AgentCardTile(p: {
  agent: Agent
  onTest: () => void; onCard: () => void; onEdit: () => void; onDelete: () => void
  testLoading: boolean; cardLoading: boolean
}) {
  const { agent: a } = p
  return (
    <div style={{
      border: `1px solid ${tokens.color.border}`,
      borderRadius: tokens.radius.lg,
      padding: 20, background: tokens.color.surface,
      display: 'flex', flexDirection: 'column', gap: 12,
      transition: 'transform .15s, border-color .15s',
    }}
    onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.borderColor = tokens.color.anchor }}
    onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.borderColor = tokens.color.border }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{
          width: 40, height: 40, borderRadius: 10,
          background: `${tokens.color.anchor}15`,
          display: 'grid', placeItems: 'center',
        }}>
          <RobotOutlined style={{ color: tokens.color.anchor, fontSize: 18 }} />
        </div>
        <div style={{ width: 8, height: 8, borderRadius: '50%',
          background: STATUS_COLOR[a.status ?? 'active'] }} />
      </div>
      <div>
        <Text strong style={{ fontSize: 16, fontFamily: tokens.font.sans }}>
          {a.name || '(unnamed)'}
        </Text>
        <Paragraph type="secondary" style={{
          fontSize: 13, marginTop: 4, marginBottom: 0,
          minHeight: 20, lineClamp: 2, display: '-webkit-box',
          WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
        }}>
          {a.description || '— 无描述'}
        </Paragraph>
      </div>
      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', minHeight: 22 }}>
        {a.difyAppId && <Tag bordered={false} color="geekblue">
          {a.difyAppId.startsWith('chat-') ? 'Chatflow' : 'Workflow'}
        </Tag>}
      </div>
      <div style={{ display: 'flex', gap: 8, marginTop: 4, borderTop: `1px solid ${tokens.color.border}`, paddingTop: 12 }}>
        <Tooltip title="测试连通 (调一次 Dify)">
          <Button size="small" icon={<ExperimentOutlined />} loading={p.testLoading} onClick={p.onTest}>测试</Button>
        </Tooltip>
        <Tooltip title="预览 A2A Agent Card">
          <Button size="small" icon={<CreditCardOutlined />} loading={p.cardLoading} onClick={p.onCard}>Card</Button>
        </Tooltip>
        <div style={{ flex: 1 }} />
        <Tooltip title="编辑"><Button size="small" type="text" icon={<EditOutlined />} onClick={p.onEdit} /></Tooltip>
        <Tooltip title="删除"><Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={p.onDelete} /></Tooltip>
      </div>
      <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 11, marginTop: 4 }}>
        A2A url: <Tag bordered={false} style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>/a2a/{a.id?.slice(0, 8)}…</Tag>
      </Text>
    </div>
  )
}

function CardPreview({ card }: { agent: Agent; card: AgentCard }) {
  return (
    <div>
      <div style={{
        background: tokens.color.surfaceAlt, padding: 16, borderRadius: tokens.radius.md,
        fontFamily: tokens.font.mono, fontSize: 12, marginBottom: 16,
        border: `1px solid ${tokens.color.border}`,
      }}>
        <div style={{ color: tokens.color.muted, marginBottom: 4 }}>
          <LinkOutlined /> endpoint
        </div>
        {card.url}
      </div>
      <Title level={5} style={{ fontFamily: tokens.font.display }}>{card.name}</Title>
      <Paragraph type="secondary">{card.description}</Paragraph>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <Tag bordered={false} color="purple">protocol {card.protocolVersion}</Tag>
        {card.capabilities.streaming && <Tag bordered={false}>streaming</Tag>}
        {card.capabilities.pushNotifications && <Tag bordered={false}>push</Tag>}
      </div>
      <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 11 }}>SKILLS ({card.skills.length})</Text>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
        {card.skills.map(s => (
          <div key={s.id} style={{
            border: `1px solid ${tokens.color.border}`, borderRadius: 8,
            padding: 12,
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <Text strong style={{ fontFamily: tokens.font.sans }}>{s.name}</Text>
              <Tag bordered={false} style={{ fontFamily: tokens.font.mono, fontSize: 10 }}>{s.id}</Tag>
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>{s.description}</Text>
            {s.tags?.length > 0 && <div style={{ marginTop: 6 }}>
              {s.tags.map((t: string) => <Tag key={t} bordered={false} color="default" style={{ fontSize: 11 }}>#{t}</Tag>)}
            </div>}
          </div>
        ))}
      </div>
    </div>
  )
}