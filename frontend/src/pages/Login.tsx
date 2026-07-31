import { useState } from 'react'
import { Card, Form, Input, Button, Typography, App } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { tokens } from '@/theme/tokens'

export default function Login() {
  const [loading, setLoading] = useState(false)
  const nav = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const login = useAuthStore(s => s.login)

  async function handle(values: { username: string; password: string }) {
    setLoading(true)
    try {
      const res = await authApi.login(values)
      login(res.token, res.adminId, res.username, res.role)
      qc.clear()
      message.success('登录成功')
      nav('/', { replace: true })
    } catch {
      // axios interceptor 已 toast
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'grid', gridTemplateColumns: '1.1fr 0.9fr',
      background: tokens.color.surfaceAlt,
    }}>
      <div style={{
        display: 'grid', placeItems: 'center', padding: 48,
        background: `linear-gradient(160deg, ${tokens.color.ink} 0%, #1B1B25 60%, ${tokens.color.anchor} 180%)`,
        color: tokens.color.surface,
      }}>
        <div style={{ maxWidth: 420 }}>
          <Typography style={{
            fontFamily: tokens.font.display, fontSize: 56, fontWeight: 800,
            lineHeight: 1.05, letterSpacing: '-0.03em',
          }}>
            多智能体<br />协同平台
          </Typography>
          <p style={{
            color: 'rgba(255,255,255,0.6)', marginTop: 24,
            fontFamily: tokens.font.sans, fontSize: 15, lineHeight: 1.7,
          }}>
            基于 Google A2A 协议, 让 Dify 上的 Agent 互相感知、协同处理用户问题,
            飞书为入口, 关键操作走管理员审批.
          </p>
          <div style={{
            marginTop: 48, display: 'flex', gap: 24,
            fontFamily: tokens.font.mono, fontSize: 11,
            color: 'rgba(255,255,255,0.4)',
          }}>
            <span>A2A · v1.0</span><span>·</span><span>Java 17</span><span>·</span><span>Phase 0</span>
          </div>
        </div>
      </div>
      <div style={{ display: 'grid', placeItems: 'center' }}>
        <Card
          style={{ width: 380, border: `1px solid ${tokens.color.border}` }}
          styles={{ body: { padding: 32 } }}
        >
          <Typography.Title level={3} style={{
            fontFamily: tokens.font.display,
            letterSpacing: '-0.02em', marginBottom: 28,
          }}>登录</Typography.Title>
          <Form layout="vertical" onFinish={handle} size="large" requiredMark={false}>
            <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading} style={{ marginTop: 8 }}>
              登录
            </Button>
          </Form>
          <p style={{
            marginTop: 24, fontFamily: tokens.font.mono, fontSize: 11,
            color: tokens.color.muted,
          }}>
            默认: admin / admin123
          </p>
        </Card>
      </div>
    </div>
  )
}