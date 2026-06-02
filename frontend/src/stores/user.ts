import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/request'
import { useThemeStore } from './theme'

interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string | null
  role?: string
  email?: string
  phone?: string
  birthday?: string | null
  gender?: string
  createdAt?: string
  roomId?: number | null
  buiId?: number | null
  roomName?: string | null
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  
  // 从 localStorage 恢复用户信息
  const savedUserInfo = localStorage.getItem('userInfo')
  const userInfo = ref<UserInfo | null>(savedUserInfo ? JSON.parse(savedUserInfo) : null)

  // 登录方法
  const login = async (username: string, password: string) => {
    const res: any = await request.post('/auth/login', { username, password })
    if (res.code === 200 && res.data && res.data.token) {
      token.value = res.data.token
      localStorage.setItem('token', res.data.token)
      
      // 将登录返回的用户信息存储到 userInfo 中，并持久化到 localStorage
      userInfo.value = {
        id: res.data.userId,
        username: res.data.username,
        nickname: res.data.nickname,
        avatar: res.data.avatar || null,
        role: res.data.role || 'user',
        email: res.data.email || '',
        phone: res.data.phone || '',
        birthday: res.data.birthday || null,
        gender: res.data.gender || '',
        roomId: res.data.roomId ?? null,
        buiId: res.data.buiId ?? null,
        roomName: res.data.roomName ?? null
      }
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    }
    return res
  }

  // 退出登录 - 通知后端拉黑 token，再清理本地存储
  const logout = async () => {
    try {
      await request.post('/auth/logout')
    } catch (e) {
      // 即使后端请求失败，也要清理本地状态
    }
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    sessionStorage.clear()

    // 重置主题到默认状态
    const themeStore = useThemeStore()
    themeStore.resetTheme()
  }

  // 用户头像 getter - null 时自动使用默认头像
  const userAvatar = computed(() => {
    const avatar = userInfo.value?.avatar
    if (!avatar) return '/default-avatar.jpg'
    // 后端返回的相对路径直接使用
    return avatar.startsWith('http') ? avatar : avatar
  })

  // 判断是否是管理员
  const isAdmin = computed(() => userInfo.value?.role === 'admin' || userInfo.value?.username === 'admin')

  return {
    token,
    userInfo,
    login,
    logout,
    userAvatar,  // 导出 getter
    isAdmin  // 导出管理员判断
  }
})