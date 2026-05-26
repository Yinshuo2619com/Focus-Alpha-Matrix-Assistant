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
        <!-- 左侧：消息通知 -->
        <div class="column left-column">
          <div class="column-header">
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
            <el-empty description="暂无消息" :image-size="80" />
          </div>

          <div v-else class="item-list">
            <div
              v-for="(item, index) in notifications"
              :key="index"
              class="notification-item"
              :class="{ unread: !item.isRead }"
              @click="handleNotifClick(item)"
            >
              <div class="item-avatar">
                <img :src="item.actors?.[0]?.avatar || '/default-avatar.png'" alt="头像" />
                <span v-if="(item.actors?.length ?? 0) > 1" class="avatar-badge">+{{ item.actors!.length - 1 }}</span>
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
                  <template v-if="item.preview">
                    <span class="preview-inline">「{{ item.preview }}」</span>
                  </template>
                </div>
                <div class="item-time">{{ formatRelativeTime(item.latestTime) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：我的评论 -->
        <div class="column right-column">
          <div class="column-header">
            <h2>我的评论</h2>
          </div>

          <div v-if="comments.length === 0" class="column-empty">
            <el-empty description="暂无评论" :image-size="80" />
          </div>

          <div v-else class="item-list">
            <div
              v-for="comment in comments"
              :key="comment.id"
              class="comment-item"
              @click="handleCommentClick(comment)"
            >
              <div class="comment-content">{{ comment.content }}</div>
              <div class="comment-meta">
                <span class="comment-article">
                  <el-icon><Document /></el-icon>
                  {{ comment.recommendTitle || '已删除的文章' }}
                </span>
                <span class="comment-time">{{ formatRelativeTime(comment.createdAt) }}</span>
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
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Document, Delete } from '@element-plus/icons-vue'
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
const notifications = ref<NotificationItem[]>([])
const comments = ref<MyComment[]>([])

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
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.notifications-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.column {
  flex: 1;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
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
    color: #303133;
  }
}

.column-empty {
  padding: 40px 0;
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
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #fafafa;
  }

  &.unread {
    background: #f0f7ff;

    &:hover {
      background: #e8f1fc;
    }
  }
}

.item-avatar {
  position: relative;
  flex-shrink: 0;

  img {
    width: 38px;
    height: 38px;
    border-radius: 50%;
    object-fit: cover;
  }
}

.avatar-badge {
  position: absolute;
  bottom: -2px;
  right: -4px;
  background: #909399;
  color: white;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 8px;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-text {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
}

.actor-name {
  font-weight: 600;
  color: #409eff;
}

.action-text {
  color: #606266;
}

.preview-inline {
  color: #909399;
  font-size: 12px;
  margin-left: 2px;
}

.item-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.comment-item {
  position: relative;
  padding: 14px 20px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #fafafa;
  }
}

.comment-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
  word-break: break-word;
  margin-bottom: 8px;
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
  color: #409eff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.comment-time {
  font-size: 12px;
  color: #c0c4cc;
  flex-shrink: 0;
}

.delete-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  opacity: 0;
  transition: opacity 0.2s;
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
  color: #909399;
  width: 100%;
}

@media screen and (max-width: 768px) {
  .notifications-container {
    flex-direction: column;
    padding: 12px;
  }

  .notification-item,
  .comment-item {
    padding: 12px 16px;
  }

  .delete-btn {
    opacity: 1;
  }
}
</style>
