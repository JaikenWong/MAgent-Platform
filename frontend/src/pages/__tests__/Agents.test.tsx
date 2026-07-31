import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider } from 'antd'
import Agents from '@/pages/Agents'

vi.mock('@/api/agents', () => ({
  agentApi: {
    page: vi.fn().mockResolvedValue({ items: [], total: 0 }),
    all: vi.fn().mockResolvedValue([]),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    test: vi.fn(),
    card: vi.fn(),
  },
}))

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={qc}>
      <ConfigProvider>
        <AntdApp>{ui}</AntdApp>
      </ConfigProvider>
    </QueryClientProvider>
  )
}

describe('Agents page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the page title', async () => {
    wrap(<Agents />)
    expect(screen.getByText('Agent 管理')).toBeInTheDocument()
  })

  it('renders the create button', async () => {
    wrap(<Agents />)
    expect(screen.getByText('新建 Agent')).toBeInTheDocument()
  })

  it('renders without crashing when no agents loaded', async () => {
    wrap(<Agents />)
    // Just verify the page container is present after render
    await waitFor(() => {
      expect(screen.getByText('Agent 管理')).toBeInTheDocument()
    })
  })
})
