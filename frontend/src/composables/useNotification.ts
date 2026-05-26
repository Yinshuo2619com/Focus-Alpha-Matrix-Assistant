import { ref } from 'vue'
import { useUserStore } from '@/stores/user'

const unreadCount = ref(0)
let ws: WebSocket | null = null
let reconnectTimer: number | null = null
let reconnectDelay = 1000

export function useNotification() {
  const store = useUserStore()

  const connect = () => {
    if (ws && ws.readyState === WebSocket.OPEN) return
    if (!store.token) return

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = location.host
    ws = new WebSocket(`${protocol}//${host}/ws/notifications?token=${store.token}`)

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'unread_count') {
          unreadCount.value = data.count
        }
      } catch (e) {
        console.error('WebSocket message parse error:', e)
      }
    }

    ws.onopen = () => {
      reconnectDelay = 1000
    }

    ws.onclose = () => {
      if (store.token) {
        reconnectTimer = window.setTimeout(() => {
          reconnectDelay = Math.min(reconnectDelay * 2, 30000)
          connect()
        }, reconnectDelay)
      }
    }

    ws.onerror = () => {
      ws?.close()
    }
  }

  const disconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      ws.close()
      ws = null
    }
    unreadCount.value = 0
  }

  const fetchUnreadCount = async () => {
    if (!store.token) return
    try {
      const { default: request } = await import('@/utils/request')
      const res = await request.get('/notifications/unread-count') as any
      unreadCount.value = res.data ?? 0
    } catch (e) {
      console.error('Failed to fetch unread count:', e)
    }
  }

  return {
    unreadCount,
    connect,
    disconnect,
    fetchUnreadCount
  }
}
