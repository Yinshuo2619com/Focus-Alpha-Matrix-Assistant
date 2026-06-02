<template>
  <div class="notifications-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="notifications-container">
      <div v-if="loading" class="loading-wrapper">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <template v-else>
        <!-- 移动端 Tab 栏 -->
        <div class="mobile-tabs">
          <div class="mobile-tab" :class="{ active: mobileTab === 'messages' }" @click="mobileTab = 'messages'">
            消息
            <span v-if="unreadCount > 0" class="tab-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </div>
          <div class="mobile-tab" :class="{ active: mobileTab === 'comments' }" @click="mobileTab = 'comments'">
            我的评论
          </div>
        </div>

        <div class="columns-wrapper">
          <!-- 左侧：消息通知 -->
          <div class="column left-column" :class="{ 'mobile-hide': mobileTab !== 'messages' }">
            <div v-if="notifications.length > 0" class="column-header">
              <h2>消息</h2>
              <el-button
                v-if="notifications.some((n: any) => !n.isRead)"
                type="primary"
                plain
                size="small"
                @click="handleMarkAllRead"
              >
                一键已读
              </el-button>
            </div>

            <div v-if="notifications.length === 0" class="column-empty">
              <el-icon class="empty-icon"><ChatDotRound /></el-icon>
              <p class="empty-text">暂无消息</p>
            </div>

            <div v-else class="item-list">
              <div
                v-for="(item, index) in notifications"
                :key="index"
                class="notification-item"
                :class="{ unread: !item.isRead }"
                @click="handleNotifClick(item)"
              >
                <div v-if="!item.isRead" class="unread-dot"></div>
                <div class="item-avatar">
                  <img :src="item.actors?.[0]?.avatar || '/default-avatar.png'" alt="头像" />
                  <div class="type-icon" :class="'type-' + item.type">
                    <el-icon v-if="item.type === 'COMMENT_REPLY'"><ChatDotRound /></el-icon>
                    <el-icon v-else-if="item.type === 'COMMENT_LIKE'"><Star /></el-icon>
                    <el-icon v-else-if="item.type === 'ARTICLE_FAVORITE'"><StarFilled /></el-icon>
                  </div>
                </div>
                <div class="item-content">
                  <div class="item-text">
                    <span class="actor-name">{{ item.actors?.[0]?.nickname }}</span>
                    <template v-if="item.actors?.length === 2">
                      、<span class="actor-name">{{ item.actors![1].nickname }}</span>
                    </template>
                    <template v-if="(item.actors?.length ?? 0) > 2">
                      等{{ item.actors!.length }}人
                    </template>
                    <span class="action-text">{{ getActionText(item.type) }}</span>
                  </div>
                  <div v-if="item.preview" class="item-preview">「{{ item.preview }}」</div>
                  <div class="item-time">{{ formatRelativeTime(item.latestTime) }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：我的评论 -->
          <div class="column right-column" :class="{ 'mobile-hide': mobileTab !== 'comments' }">
            <div v-if="comments.length > 0" class="column-header">
              <h2>我的评论</h2>
            </div>

            <div v-if="comments.length === 0" class="column-empty">
              <el-icon class="empty-icon"><Document /></el-icon>
              <p class="empty-text">暂无评论</p>
            </div>

            <div v-else class="item-list">
              <div
                v-for="comment in comments"
                :key="comment.id"
                class="comment-item"
                @click="handleCommentClick(comment)"
              >
                <div class="comment-main">
                  <div class="comment-content">{{ comment.content }}</div>
                  <div class="comment-meta">
                    <span class="comment-article">
                      <el-icon><Document /></el-icon>
                      {{ comment.recommendTitle || '已删除的文章' }}
                    </span>
                    <span class="comment-time">{{ formatRelativeTime(comment.createdAt) }}</span>
                  </div>
                </div>
                <el-button
                  class="delete-btn"
                  size="small"
                  type="danger"
                  text
                  @click.stop="handleDeleteComment(comment)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Document, Delete, ChatDotRound, Star, StarFilled } from '@element-plus/icons-vue'
import StatusBar from '@/components/StatusBar.vue'
import { useNotification } from '@/composables/useNotification'
import request from '@/utils/request'

interface Actor {
  id: number
  nickname: string
  avatar: string
}

interface NotificationItem {
  type: string
  targetId: number
  targetType: string
  isRead: boolean
  latestTime: string
  actors: Actor[]
  preview: string | null
  recommendId: number | null
}

interface MyComment {
  id: number
  content: string
  likes: number
  createdAt: string
  recommendId: number
  recommendTitle: string
}

const router = useRouter()
const { fetchUnreadCount } = useNotification()
const loading = ref(false)
const mobileTab = ref<'messages' | 'comments'>('messages')
const notifications = ref<NotificationItem[]>([])
const comments = ref<MyComment[]>([])
const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

const getActionText = (type: string) => {
  switch (type) {
    case 'COMMENT_REPLY': return '回复了你的评论'
    case 'COMMENT_LIKE': return '点赞了你的评论'
    case 'ARTICLE_FAVORITE': return '收藏了你的文章'
    default: return '进行了操作'
  }
}

const formatRelativeTime = (dateStr: string) => {
  if (!dateStr) return ''
  const now = new Date()
  const date = new Date(dateStr)
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 30) return `${days}天前`
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

const fetchAll = async () => {
  loading.value = true
  try {
    const [notifRes, commentRes] = await Promise.all([
      request.get('/notifications') as any,
      request.get('/comments/mine') as any
    ])
    notifications.value = notifRes.data || []
    comments.value = commentRes.data || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取消息失败')
  } finally {
    loading.value = false
  }
}

const handleMarkAllRead = async () => {
  try {
    await request.post('/notifications/read-all')
    notifications.value.forEach(item => { item.isRead = true })
    fetchUnreadCount()
    ElMessage.success('已全部标记为已读')
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

const handleNotifClick = async (item: NotificationItem) => {
  if (!item.isRead) {
    try {
      await request.post('/notifications/read', {
        type: item.type,
        targetId: item.targetId
      })
      item.isRead = true
      fetchUnreadCount()
    } catch {
      // ignore
    }
  }

  if (item.recommendId) {
    if (item.targetType === 'COMMENT') {
      router.push(`/recommend/${item.recommendId}?commentId=${item.targetId}`)
    } else {
      router.push(`/recommend/${item.recommendId}`)
    }
  }
}

const handleCommentClick = (comment: MyComment) => {
  if (comment.recommendId) {
    router.push(`/recommend/${comment.recommendId}?commentId=${comment.id}`)
  }
}

const handleDeleteComment = async (comment: MyComment) => {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/comments/${comment.id}`)
    ElMessage.success('评论已删除')
    comments.value = comments.value.filter(c => c.id !== comment.id)
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetchAll()
})
</script>

<style scoped lang="scss">
.notifications-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.status-bar-placeholder {
  height: 60px;
}

.notifications-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.mobile-tabs {
  display: none;
}

.columns-wrapper {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.column {
  flex: 1;
  background: var(--card-bg);
  border-radius: 12px;
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;

  h2 {
    margin: 0;
    font-size: 16px;
    color: var(--text-primary);
  }
}

.column-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;

  .empty-icon {
    font-size: 48px;
    color: #dcdfe6;
    margin-bottom: 12px;
  }

  .empty-text {
    font-size: 14px;
    color: var(--text-placeholder);
    margin: 0;
  }
}

.item-list {
  display: flex;
  flex-direction: column;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f0f2f5;
  position: relative;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #fafbfc;
  }

  &.unread {
    background: #f8fbff;

    &:hover {
      background: #f0f5ff;
    }
  }
}

.unread-dot {
  position: absolute;
  top: 20px;
  left: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}

.item-avatar {
  position: relative;
  flex-shrink: 0;
  margin-left: 6px;

  img {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    object-fit: cover;
  }
}

.type-icon {
  position: absolute;
  bottom: -3px;
  right: -3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: white;
  border: 2px solid #fff;

  &.type-COMMENT_REPLY {
    background: var(--accent);
  }

  &.type-COMMENT_LIKE {
    background: #f7ba2a;
  }

  &.type-ARTICLE_FAVORITE {
    background: #f56c6c;
  }
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-text {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
}

.actor-name {
  font-weight: 600;
  color: #1a1a1a;
}

.action-text {
  color: var(--text-regular);
}

.item-preview {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-time {
  font-size: 12px;
  color: var(--text-placeholder);
  margin-top: 6px;
}

.comment-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f0f2f5;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #fafbfc;
  }
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-content {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
  word-break: break-word;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.comment-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.comment-article {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--accent);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.comment-time {
  font-size: 12px;
  color: var(--text-placeholder);
  flex-shrink: 0;
}

.delete-btn {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
  margin-top: 2px;
}

.comment-item:hover .delete-btn {
  opacity: 1;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 100px 0;
  color: var(--text-secondary);
  width: 100%;
}

@media screen and (max-width: 768px) {
  .notifications-container {
    padding: 12px;
  }

  .mobile-tabs {
    display: flex;
    background: var(--card-bg);
    border-radius: 12px;
    overflow: hidden;
    box-shadow: var(--card-shadow);
    margin-bottom: 12px;
  }

  .mobile-tab {
    flex: 1;
    text-align: center;
    padding: 14px 0;
    font-size: 15px;
    color: var(--text-regular);
    cursor: pointer;
    position: relative;
    transition: all 0.2s;

    &.active {
      color: var(--accent);
      font-weight: 600;
      background: var(--accent-light);
    }
  }

  .tab-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    font-size: 11px;
    font-weight: 600;
    color: white;
    background: #f56c6c;
    border-radius: 10px;
    margin-left: 4px;
    vertical-align: middle;
  }

  .columns-wrapper {
    flex-direction: column;
  }

  .mobile-hide {
    display: none;
  }

  .column:has(.column-empty) {
    background: transparent;
    box-shadow: none;
  }

  .column-empty {
    padding: 80px 20px;

    .empty-icon {
      font-size: 56px;
      color: #e4e7ed;
    }
  }

  .notification-item {
    padding: 12px 16px;
  }

  .comment-item {
    padding: 12px 16px;
  }

  .delete-btn {
    opacity: 1;
  }

  .unread-dot {
    left: 4px;
  }

  .item-avatar {
    margin-left: 4px;
  }
}
</style>
