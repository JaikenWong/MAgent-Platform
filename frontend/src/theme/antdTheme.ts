import type { ThemeConfig } from 'antd'
import { tokens } from './tokens'

/**
 * Hallmark 反 AI-slop 主题: 不用 AntD 默认 daybreak, 锚色 6F3FF5 紫调,
 * 字体 IBM Plex Sans / Sora / JetBrains Mono. 圆角 8 而非默认 6.
 */
export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: tokens.color.anchor,
    colorTextBase: tokens.color.ink,
    colorBgBase: tokens.color.surface,
    colorBorder: tokens.color.border,
    colorBorderSecondary: tokens.color.surfaceAlt,
    colorTextSecondary: tokens.color.muted,
    colorError: tokens.color.critical,
    colorSuccess: tokens.color.ok,
    colorWarning: tokens.color.warn,
    fontFamily: tokens.font.sans,
    fontFamilyCode: tokens.font.mono,
    borderRadius: tokens.radius.sm,
    borderRadiusLG: tokens.radius.md,
    wireframe: false,
    fontSize: 14,
  },
  components: {
    Layout: {
      headerBg: tokens.color.surface,
      headerHeight: 64,
      siderBg: tokens.color.ink,
      bodyBg: tokens.color.surface,
      triggerBg: tokens.color.anchor,
    },
    Menu: {
      darkItemBg: tokens.color.ink,
      darkSubMenuItemBg: tokens.color.ink,
      darkItemSelectedBg: tokens.color.anchor,
    },
    Card: {
      borderRadiusLG: tokens.radius.lg,
    },
    Button: {
      controlHeightLG: 44,
      fontWeight: 500,
    },
    Table: {
      headerBg: tokens.color.surfaceAlt,
      headerSplitColor: tokens.color.surfaceAlt,
    },
  },
}