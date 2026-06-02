import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/request'

export interface ThemeConfig {
  theme: { preset: 'dark' | 'light' | 'auto'; color: string }
  logo: { favicon: string }
  statusbar: { color: string; opacity: number }
  interface: {
    rounded: 'small' | 'medium' | 'large' | 'none'
    bg_image: string; bg_image_opacity: number
    content_opacity: number
    card_opacity: number
    shadow_color: string; shadow_opacity: number
    container_width: string
  }
}

const defaultConfig: ThemeConfig = {
  theme: { preset: 'light', color: '#409eff' },
  logo: { favicon: '' },
  statusbar: { color: '#b7a091', opacity: 100 },
  interface: {
    rounded: 'small', bg_image: '', bg_image_opacity: 100,
    content_opacity: 100, card_opacity: 100, shadow_color: '#000000', shadow_opacity: 0,
    container_width: '100%'
  }
}

const STORAGE_KEY = 'themeConfig'

// 根据主色生成浅色/更浅色变体
function generateAccentVariants(hex: string) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return {
    light: `rgba(${r}, ${g}, ${b}, 0.1)`,
    lighter: `rgba(${r}, ${g}, ${b}, 0.05)`,
    bg: `rgba(${r}, ${g}, ${b}, 0.1)`
  }
}

// 圆角值映射
const roundedMap: Record<string, string> = {
  none: '0',
  small: '4px',
  medium: '8px',
  large: '16px'
}

// 将 hex 转为 RGB 数组
function hexToRgb(hex: string): [number, number, number] {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return [r, g, b]
}

// 生成 Element Plus 主色变体（深色/浅色阶）
function generateElColorVariants(hex: string) {
  const [r, g, b] = hexToRgb(hex)
  // 生成 9 个 light shade（混合白色）
  const light: Record<string, string> = {}
  for (let i = 1; i <= 9; i++) {
    const ratio = i * 0.1
    const lr = Math.round(r + (255 - r) * ratio)
    const lg = Math.round(g + (255 - g) * ratio)
    const lb = Math.round(b + (255 - b) * ratio)
    light[i] = `#${lr.toString(16).padStart(2, '0')}${lg.toString(16).padStart(2, '0')}${lb.toString(16).padStart(2, '0')}`
  }
  // 深色 shade（混合黑色）
  const dark: Record<string, string> = {}
  for (let i = 1; i <= 9; i++) {
    const ratio = 1 - i * 0.1
    const dr = Math.round(r * ratio)
    const dg = Math.round(g * ratio)
    const db = Math.round(b * ratio)
    dark[i] = `#${dr.toString(16).padStart(2, '0')}${dg.toString(16).padStart(2, '0')}${db.toString(16).padStart(2, '0')}`
  }
  return { light, dark }
}

export const useThemeStore = defineStore('theme', () => {
  const config = ref<ThemeConfig>(structuredClone(defaultConfig))
  const darkMedia = window.matchMedia('(prefers-color-scheme: dark)')

  const resolvedTheme = computed<'light' | 'dark'>(() => {
    if (config.value.theme.preset === 'auto') {
      return darkMedia.matches ? 'dark' : 'light'
    }
    return config.value.theme.preset
  })

  function applyTheme() {
    const html = document.documentElement
    const theme = resolvedTheme.value
    const cfg = config.value

    // 设置 data-theme 和 Element Plus dark class
    html.setAttribute('data-theme', theme)
    if (theme === 'dark') {
      html.classList.add('dark')
    } else {
      html.classList.remove('dark')
    }

    // 应用自定义主题色
    const accent = cfg.theme.color
    html.style.setProperty('--accent', accent)
    const variants = generateAccentVariants(accent)
    html.style.setProperty('--accent-light', variants.light)
    html.style.setProperty('--accent-lighter', variants.lighter)

    // 覆盖 Element Plus 主色变量，使 el-button type="primary" 等跟随主题色
    const elVariants = generateElColorVariants(accent)
    html.style.setProperty('--el-color-primary', accent)
    for (let i = 1; i <= 9; i++) {
      html.style.setProperty(`--el-color-primary-light-${i}`, elVariants.light[i])
      html.style.setProperty(`--el-color-primary-dark-${i}`, elVariants.dark[i])
    }

    // 应用 favicon
    const favicon = cfg.logo.favicon
    if (favicon) {
      let link = document.querySelector('link[rel="icon"]') as HTMLLinkElement
      if (!link) {
        link = document.createElement('link')
        link.rel = 'icon'
        document.head.appendChild(link)
      }
      link.href = favicon
    }

    // 应用圆角
    html.style.setProperty('--border-radius', roundedMap[cfg.interface.rounded] || '4px')

    // 应用容器宽度
    html.style.setProperty('--container-width', cfg.interface.container_width || '100%')

    // 应用状态栏颜色
    const sbColor = cfg.statusbar.color
    const sbOpacity = cfg.statusbar.opacity / 100
    if (sbColor && sbColor.startsWith('#')) {
      const [r, g, b] = hexToRgb(sbColor)
      html.style.setProperty('--statusbar-bg', `rgba(${r}, ${g}, ${b}, ${sbOpacity})`)
    } else if (sbColor) {
      // rgba 格式或其他格式，直接设置（忽略自定义 opacity）
      html.style.setProperty('--statusbar-bg', sbColor)
    }

    // 背景图片 + 内容区透明度
    const bgImage = cfg.interface.bg_image
    const bgImgOpacity = cfg.interface.bg_image_opacity / 100
    const contentOpacity = cfg.interface.content_opacity / 100

    if (bgImage) {
      document.body.style.backgroundImage = `url(${bgImage})`
      document.body.style.backgroundSize = 'cover'
      document.body.style.backgroundPosition = 'center'
      document.body.style.backgroundAttachment = 'fixed'
      document.body.style.backgroundRepeat = 'no-repeat'
      // 遮罩：bg_image_opacity 控制图片清晰度，content_opacity 叠加内容区透明度
      const effectiveAlpha = (1 - bgImgOpacity) + bgImgOpacity * (1 - contentOpacity)
      const overlay = theme === 'dark'
        ? `rgba(13, 17, 23, ${effectiveAlpha})`
        : `rgba(245, 247, 250, ${effectiveAlpha})`
      html.style.setProperty('--page-bg', overlay)
    } else {
      document.body.style.backgroundImage = 'none'
      // 无背景图时，content_opacity 控制页面背景透明度（透出 body 底色）
      if (contentOpacity < 1) {
        const base = theme === 'dark' ? [13, 17, 23] : [245, 247, 250]
        html.style.setProperty('--page-bg', `rgba(${base[0]}, ${base[1]}, ${base[2]}, ${contentOpacity})`)
      } else {
        html.style.setProperty('--page-bg', theme === 'dark' ? '#0d1117' : '#f5f7fa')
      }
    }

    // 卡片背景透明度
    const cardOpacity = cfg.interface.card_opacity / 100
    const cardBase = theme === 'dark' ? [22, 27, 34] : [255, 255, 255]
    html.style.setProperty('--card-bg', `rgba(${cardBase[0]}, ${cardBase[1]}, ${cardBase[2]}, ${cardOpacity})`)

    // 阴影
    const shadowColor = cfg.interface.shadow_color || '#000000'
    const shadowAlpha = cfg.interface.shadow_opacity / 100
    if (shadowAlpha > 0) {
      const [sr, sg, sb] = hexToRgb(shadowColor)
      html.style.setProperty('--card-shadow', `0 2px 12px rgba(${sr}, ${sg}, ${sb}, ${shadowAlpha})`)
      html.style.setProperty('--card-shadow-hover', `0 4px 20px rgba(${sr}, ${sg}, ${sb}, ${shadowAlpha + 0.1})`)
    } else {
      const defaultAlpha = theme === 'dark' ? 0.3 : 0.06
      html.style.setProperty('--card-shadow', `0 2px 8px rgba(0, 0, 0, ${defaultAlpha})`)
      html.style.setProperty('--card-shadow-hover', `0 4px 16px rgba(0, 0, 0, ${defaultAlpha + 0.04})`)
    }
  }

  // 监听系统主题变化（auto 模式）
  function onSystemThemeChange() {
    if (config.value.theme.preset === 'auto') {
      applyTheme()
    }
  }

  async function initTheme() {
    // 1. 从 localStorage 读取缓存，立即应用
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        // 兼容旧配置：删除旧 sidebar/statusbar/home/login，使用新的默认值
        delete parsed.sidebar
        delete parsed.statusbar
        delete parsed.home
        delete parsed.login
        config.value = { ...structuredClone(defaultConfig), ...parsed }
      } catch {}
    }
    applyTheme()

    // 2. 监听系统主题变化
    darkMedia.addEventListener('change', onSystemThemeChange)

    // 3. 如果已登录，从后端获取最新配置
    const token = localStorage.getItem('token')
    if (token) {
      try {
        const res: any = await request.get('/theme')
        if (res.code === 200 && res.data) {
          const data = res.data
          // 兼容旧配置：如果有旧 sidebar 字段说明是旧格式，重置 statusbar
          if (data.sidebar) {
            delete data.statusbar
          }
          delete data.sidebar
          delete data.home
          delete data.login
          config.value = { ...structuredClone(defaultConfig), ...data }
          localStorage.setItem(STORAGE_KEY, JSON.stringify(config.value))
          applyTheme()
        }
      } catch {}
    }
  }

  async function saveTheme() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config.value))
    applyTheme()
    const token = localStorage.getItem('token')
    if (token) {
      try {
        await request.post('/theme', config.value)
      } catch {}
    }
  }

  function cyclePreset() {
    const order: Array<'auto' | 'light' | 'dark'> = ['auto', 'light', 'dark']
    const idx = order.indexOf(config.value.theme.preset)
    config.value.theme.preset = order[(idx + 1) % order.length]
    saveTheme()
  }

  function resetTheme() {
    config.value = structuredClone(defaultConfig)
    saveTheme()
  }

  function importTheme(json: string) {
    try {
      const parsed = JSON.parse(json)
      // 兼容旧配置
      delete parsed.sidebar
      delete parsed.home
      delete parsed.login
      config.value = { ...structuredClone(defaultConfig), ...parsed }
      saveTheme()
      return true
    } catch {
      return false
    }
  }

  function exportTheme(): string {
    return JSON.stringify(config.value, null, 2)
  }

  return {
    config,
    resolvedTheme,
    initTheme,
    saveTheme,
    cyclePreset,
    resetTheme,
    importTheme,
    exportTheme,
    applyTheme
  }
})
