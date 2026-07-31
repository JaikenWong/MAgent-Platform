import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Typography, Button, Input, Spin, Empty, App,
} from 'antd'
import { CheckOutlined, EditOutlined, CloseOutlined } from '@ant-design/icons'
import { settingApi, type SystemSetting } from '@/api/settings'
import { tokens } from '@/theme/tokens'

const { Title, Text } = Typography

export default function Settings() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [editingKey, setEditingKey] = useState<string | null>(null)
  const [editValue, setEditValue] = useState<string>('')

  const { data, isLoading } = useQuery({
    queryKey: ['settings'],
    queryFn: () => settingApi.all(),
  })
  const settings = data ?? []

  const updateMut = useMutation({
    mutationFn: (s: SystemSetting) => settingApi.update(s),
    onSuccess: () => {
      message.success('已保存')
      setEditingKey(null)
      qc.invalidateQueries({ queryKey: ['settings'] })
    },
    onError: (e: Error) => message.error(e.message),
  })

  function startEdit(setting: SystemSetting) {
    setEditingKey(setting.key)
    setEditValue(typeof setting.value === 'object'
      ? JSON.stringify(setting.value)
      : String(setting.value ?? ''))
  }

  function cancelEdit() {
    setEditingKey(null)
    setEditValue('')
  }

  function saveEdit(setting: SystemSetting) {
    let value: Record<string, unknown> = {}
    try {
      const parsed = JSON.parse(editValue)
      value = typeof parsed === 'object' && parsed !== null ? parsed : { _value: editValue }
    } catch {
      value = { _value: editValue }
    }
    updateMut.mutate({ key: setting.key, value, id: setting.id })
  }

  function displayValue(value: unknown): string {
    if (value === null || value === undefined) return ''
    if (typeof value === 'object') return JSON.stringify(value)
    return String(value)
  }

  return (
    <div>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
        marginBottom: 24,
      }}>
        <Title level={2} style={{
          fontFamily: tokens.font.display, letterSpacing: '-0.02em', margin: 0,
        }}>
          系统设置
        </Title>
        <Text type="secondary" style={{ fontFamily: tokens.font.mono, fontSize: 12 }}>
          // LLM / Dify 默认 / 飞书默认 / 通知
        </Text>
      </div>

      {isLoading
        ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
        : settings.length === 0
          ? <Empty description="暂无系统设置" />
          : (
            <div style={{
              border: `1px solid ${tokens.color.border}`,
              borderRadius: tokens.radius.md,
              overflow: 'hidden',
            }}>
              {settings.map((setting, i) => (
                <div key={setting.key} style={{
                  padding: '12px 20px',
                  borderBottom: i < settings.length - 1 ? `1px solid ${tokens.color.border}` : 'none',
                  display: 'flex', alignItems: 'center', gap: 16,
                }}>
                  <Text style={{
                    fontFamily: tokens.font.mono, fontSize: 12,
                    minWidth: 200, color: tokens.color.ink, fontWeight: 500,
                  }}>
                    {setting.key}
                  </Text>

                  <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8 }}>
                    {editingKey === setting.key ? (
                      <>
                        <Input
                          value={editValue}
                          onChange={e => setEditValue(e.target.value)}
                          style={{
                            fontFamily: tokens.font.mono, fontSize: 12,
                            flex: 1,
                          }}
                          autoFocus
                        />
                        <Button
                          size="small"
                          type="primary"
                          icon={<CheckOutlined />}
                          loading={updateMut.isPending}
                          onClick={() => saveEdit(setting)}
                          style={{ background: tokens.color.anchor, borderColor: tokens.color.anchor }}
                        />
                        <Button size="small" icon={<CloseOutlined />} onClick={cancelEdit} />
                      </>
                    ) : (
                      <>
                        <Text style={{
                          fontFamily: tokens.font.mono, fontSize: 12,
                          color: tokens.color.muted, flex: 1,
                          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                        }}>
                          {displayValue(setting.value)}
                        </Text>
                        <Button
                          size="small"
                          type="text"
                          icon={<EditOutlined />}
                          onClick={() => startEdit(setting)}
                        />
                      </>
                    )}
                  </div>

                  {setting.description && (
                    <Text type="secondary" style={{ fontSize: 11, minWidth: 120, textAlign: 'right' }}>
                      {setting.description}
                    </Text>
                  )}
                </div>
              ))}
            </div>
          )
      }
    </div>
  )
}
