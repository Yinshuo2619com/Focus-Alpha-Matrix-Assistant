<template>
  <div class="detail-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="detail-container">
      <div class="detail-header">
        <el-button @click="$router.push('/tools')">
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </el-button>
      </div>

      <div v-if="loading" class="loading-wrapper">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <template v-else-if="recommendation">
        <div class="detail-card">
          <div v-if="recommendation.coverUrl" class="detail-cover">
            <img :src="recommendation.coverUrl" alt="封面" />
          </div>

          <div class="detail-body">
            <h1 class="detail-title">{{ recommendation.title }}</h1>
            <p v-if="recommendation.summary" class="detail-summary">{{ recommendation.summary }}</p>

            <div class="detail-meta">
              <span class="meta-author">
                <el-icon><User /></el-icon>
                {{ recommendation.authorName }}
              </span>
              <span class="meta-time">
                <el-icon><Clock /></el-icon>
                {{ formatTime(recommendation.createdAt) }}
              </span>
              <span class="meta-views">
                <el-icon><View /></el-icon>
                {{ recommendation.views }}
              </span>
              <span class="meta-likes" @click="handleLike">
                <el-icon :class="{ liked: hasLiked }"><Star /></el-icon>
                {{ recommendation.likes }}
              </span>
            </div>

            <div class="detail-actions" v-if="isAuthor">
              <el-button size="small" @click="$router.push(`/recommend/${recommendation.id}/edit`)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="handleDelete">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>

            <el-divider />

            <div class="md-content" v-html="renderedContent"></div>
          </div>
        </div>
      </template>

      <div v-else class="empty-state">
        <el-empty description="推荐内容不存在或已被删除" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, User, Clock, View, Star, Edit, Delete } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import StatusBar from '@/components/StatusBar.vue'
import request from '@/utils/request'

interface Recommendation {
  id: number
  userId: number
  authorName: string
  title: string
  summary: string
  coverUrl: string
  contentUrl: string
  views: number
  likes: number
  createdAt: string
  updatedAt: string
}

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const loading = ref(true)
const recommendation = ref<Recommendation | null>(null)
const mdContent = ref('')
const hasLiked = ref(false)

const md = new MarkdownIt()
const renderedContent = computed(() => md.render(mdContent.value || ''))

const isAuthor = computed(() => {
  return recommendation.value && store.userInfo?.id === recommendation.value.userId
})

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await request.get(`/recommendations/${route.params.id}`) as any
    recommendation.value = res.data

    // Fetch MD content from COS
    if (res.data.contentUrl) {
      const contentRes = await fetch(res.data.contentUrl)
      mdContent.value = await contentRes.text()
    }
  } catch (err: any) {
    ElMessage.error(err.message || '获取推荐详情失败')
  } finally {
    loading.value = false
  }
}

const handleLike = async () => {
  if (hasLiked.value) return
  try {
    await request.post(`/recommendations/${route.params.id}/like`)
    if (recommendation.value) {
      recommendation.value.likes++
    }
    hasLiked.value = true
    ElMessage.success('点赞成功')
  } catch (err: any) {
    ElMessage.error(err.message || '点赞失败')
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定删除这条推荐吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/recommendations/${route.params.id}`)
    ElMessage.success('删除成功')
    router.push('/tools')
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped lang="scss">
.detail-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.detail-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.detail-header {
  margin-bottom: 20px;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 80px 0;
  color: #909399;
}

.empty-state {
  padding: 60px 0;
}

.detail-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.detail-cover {
  width: 100%;
  max-height: 300px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.detail-body {
  padding: 24px;
}

.detail-title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px;
}

.detail-summary {
  font-size: 15px;
  color: #909399;
  margin: 0 0 16px;
}

.detail-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #909399;
  flex-wrap: wrap;

  span {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .meta-likes {
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: #e6a23c;
    }

    .liked {
      color: #e6a23c;
    }
  }
}

.detail-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.md-content {
  font-size: 15px;
  line-height: 1.8;
  color: #303133;

  :deep(h1), :deep(h2), :deep(h3) {
    margin: 20px 0 10px;
    font-weight: 600;
  }

  :deep(h1) { font-size: 24px; }
  :deep(h2) { font-size: 20px; }
  :deep(h3) { font-size: 17px; }

  :deep(p) {
    margin: 10px 0;
  }

  :deep(code) {
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'Courier New', Consolas, monospace;
  }

  :deep(pre) {
    background: #f5f7fa;
    padding: 14px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 12px 0;

    code {
      background: none;
      padding: 0;
    }
  }

  :deep(blockquote) {
    border-left: 4px solid #409eff;
    padding-left: 14px;
    margin: 10px 0;
    color: #606266;
  }

  :deep(ul), :deep(ol) {
    padding-left: 24px;
    margin: 10px 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 8px 0;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 10px 0;

    th, td {
      border: 1px solid #ebeef5;
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: #f5f7fa;
      font-weight: 600;
    }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid #ebeef5;
    margin: 16px 0;
  }
}

@media screen and (max-width: 768px) {
  .detail-container {
    padding: 12px;
  }

  .detail-body {
    padding: 16px;
  }

  .detail-title {
    font-size: 20px;
  }

  .detail-meta {
    gap: 12px;
  }
}
</style>
