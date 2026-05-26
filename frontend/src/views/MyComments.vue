<template>
  <div class="my-comments-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="comments-container">
      <div class="comments-header">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>我的评论</h2>
      </div>

      <div v-if="loading" class="loading-wrapper">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="comments.length === 0" class="empty-state">
        <el-empty description="暂无评论">
          <el-button type="primary" @click="$router.push('/tools')">去发现内容</el-button>
        </el-empty>
      </div>

      <div v-else class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-card">
          <div class="comment-content">{{ comment.content }}</div>
          <div class="comment-meta">
            <span class="comment-article" @click.stop="$router.push(`/recommend/${comment.recommendId}`)">
              <el-icon><Document /></el-icon>
              {{ comment.recommendTitle || '已删除的文章' }}
            </span>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
          </div>
          <div class="comment-footer">
            <span class="comment-likes">
              <el-icon><Star /></el-icon>
              {{ comment.likes }}
            </span>
            <el-button size="small" type="danger" text @click.stop="handleDelete(comment.id)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, Star, Delete, Document } from '@element-plus/icons-vue'
import StatusBar from '@/components/StatusBar.vue'
import request from '@/utils/request'

interface MyComment {
  id: number
  content: string
  likes: number
  createdAt: string
  recommendId: number
  recommendTitle: string
}

const loading = ref(false)
const comments = ref<MyComment[]>([])

const fetchComments = async () => {
  loading.value = true
  try {
    const res = await request.get('/comments/mine') as any
    comments.value = res.data || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取评论列表失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/comments/${id}`)
    ElMessage.success('评论已删除')
    comments.value = comments.value.filter(item => item.id !== id)
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day} ${hours}:${minutes}`
}

onMounted(() => {
  fetchComments()
})
</script>

<style scoped lang="scss">
.my-comments-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.comments-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.comments-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;

  h2 {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin: 0;
  }
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: #909399;
}

.empty-state {
  padding: 40px 0;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-card {
  background: white;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.comment-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  margin-bottom: 10px;
  word-break: break-word;
}

.comment-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-article {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover {
    text-decoration: underline;
  }
}

.comment-time {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.comment-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.comment-likes {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
}

@media screen and (max-width: 768px) {
  .comments-container {
    padding: 12px;
  }

  .comment-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
