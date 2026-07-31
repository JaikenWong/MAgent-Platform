import { useEffect, useState, useRef } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Layout, Menu, Avatar, Typography, Badge } from 'antd'
import {
  DashboardOutlined, RobotOutlined, MessageOutlined,
  ApartmentOutlined, SafetyCertificateOutlined,
  MessageOutlined as ChatOutlined, ThunderboltOutlined, SettingOutlined, FileTextOutlined,
  BellOutlined, LogoutOutlined,
} from '@ant-design/icons'
import { Client } from '@stomp/stompjs'
import { useAuthStore } from '@/stores/auth'
import { approvalApi } from '@/api/approvals'
import { tokens } from '@/theme/tokens'

const { Sider, Header, Content } = Layout
const { Text } = Typography

const NAV = [
  { key: '/', label: '面板', icon: <DashboardOutlined /> },
  { key: '/agents', label: 'Agent', icon: <RobotOutlined /> },
  { key: '/bots', label: '飞书机器人', icon: <MessageOutlined /> },
  { key: '/rules', label: '编排规则', icon: <ApartmentOutlined /> },
  { key: '/approvals', label: '审批队列', icon: <BellOutlined /> },
  { key: '/approval-policies', label: '审批策略', icon: <SafetyCertificateOutlined /> },
  { key: '/conversations', label: '对话记录', icon: <ChatOutlined /> },
  { key: '/tasks', label: '任务监控', icon: <ThunderboltOutlined /> },
  { key: '/settings', label: '系统设置', icon: <SettingOutlined /> },
  { key: '/audit', label: '审计日志', icon: <FileTextOutlined /> },
]

export default function AdminLayout() {
  const loc = useLocation()
  const selected = NAV.find(n => loc.pathname.startsWith(n.key) && n.key !== '/')?.key
    || (loc.pathname === '/' ? '/' : '')
  const [collapsed, setCollapsed] = useState(false)
  const [pendingCount, setPendingCount] = useState(0)
  const { username, logout } = useAuthStore()
  const stompRef = useRef<Client | null>(null)

  // Initial fetch + WebSocket subscription for real-time updates
  useEffect(() => {
    // Initial count
    approvalApi.pendingCount().then(c => setPendingCount(c)).catch(() => {})

    // WebSocket: subscribe to /topic/approvals
    const wsUrl = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/approvals', (msg) => {
          try {
            const data = JSON.parse(msg.body)
            if (data.pendingCount !== undefined) {
              setPendingCount(data.pendingCount)
            }
          } catch {
            // refresh count on any message
            approvalApi.pendingCount().then(c => setPendingCount(c)).catch(() => {})
          }
        })
      },
    })
    stompRef.current = client
    client.activate()

    return () => {
      client.deactivate()
    }
  }, [])

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible collapsed={collapsed} onCollapse={setCollapsed}
        width={224} theme="dark"
        style={{ background: tokens.color.ink }}
      >
        <div style={{ height: 64, display: 'grid', placeItems: 'center' }}>
          <Typography style={{
            color: tokens.color.surface,
            fontFamily: tokens.font.display,
            fontSize: 18, fontWeight: 700,
            letterSpacing: '-0.01em',
          }}>
            {collapsed ? 'M' : 'MAgent'}
          </Typography>
        </div>
        <Menu
          theme="dark" mode="inline"
          selectedKeys={[selected || '/']}
          items={NAV.map(n => ({
            key: n.key,
            label: <Link to={n.key}>{n.label}</Link>,
            icon: n.key === '/approvals' && pendingCount > 0
              ? <Badge count={pendingCount} size="small" offset={[6, -2]}>{n.icon}</Badge>
              : n.icon,
          }))}
        />
      </Sider>
      <Layout>
        <Header style={{
          background: tokens.color.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'flex-end',
          borderBottom: `1px solid ${tokens.color.border}`,
          paddingInline: 24, gap: 16,
        }}>
          {pendingCount > 0 && (
            <Link to="/approvals">
              <Badge count={pendingCount} offset={[-2, 4]}>
                <BellOutlined style={{ fontSize: 18, color: tokens.color.anchor, cursor: 'pointer' }} />
              </Badge>
            </Link>
          )}
          <Avatar size="small" style={{ background: tokens.color.anchor }}>
            {(username || 'U').slice(0, 1).toUpperCase()}
          </Avatar>
          <Text type="secondary">{username}</Text>
          <LogoutOutlined
            onClick={() => { logout(); location.href = '/login' }}
            style={{ color: tokens.color.muted, cursor: 'pointer' }}
          />
        </Header>
        <Content style={{ padding: 24, background: tokens.color.surface }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}