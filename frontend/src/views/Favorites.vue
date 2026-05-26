<template>
  <div class="favorites-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="favorites-container">
      <div class="favorites-header">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>我的收藏</h2>
      </div>

      <div v-if="loading" class="loading-wrapper">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="favorites.length === 0" class="empty-state">
        <el-empty description="暂无收藏内容">
          <el-button type="primary" @click="$router.push('/tools')">去发现内容</el-button>
        </el-empty>
      </div>

      <div v-else class="favorites-grid">
        <div
          v-for="item in favorites"
          :key="item.id"
          class="favorite-card"
          @click="$router.push(`/recommend/${item.id}`)"
        >
          <div class="card-remove" @click.stop="handleRemoveFavorite(item.id)">
            <el-icon><StarFilled /></el-icon>
          </div>
          <div v-if="item.coverUrl" class="card-cover">
            <img :src="item.coverUrl" alt="封面" />
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ item.title }}</h3>
            <p class="card-summary">{{ item.summary || '暂无简介' }}</p>
            <div class="card-footer">
              <span class="card-author">{{ item.authorName }}</span>
              <span class="card-time">{{ formatTime(item.updatedAt) }}</span>
            </div>
            <div class="card-stats">
              <span><el-icon><View /></el-icon> {{ item.views }}</span>
              <span><el-icon><Star /></el-icon> {{ item.likes }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, View, Star, StarFilled } from '@element-plus/icons-vue'
import StatusBar from '@/components/StatusBar.vue'
import request from '@/utils/request'

interface FavoriteItem {
  id: number
  title: string
  summary: string
  coverUrl: string
  views: number
  likes: number
  authorName: string
  updatedAt: string
}

const loading = ref(false)
const favorites = ref<FavoriteItem[]>([])

const fetchFavorites = async () => {
  loading.value = true
  try {
    const res = await request.get('/recommendations/favorites') as any
    favorites.value = res.data || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

const handleRemoveFavorite = async (recommendId: number) => {
  try {
    await ElMessageBox.confirm('确定取消收藏吗？', '确认', {
      confirmButtonText: '取消收藏',
      cancelButtonText: '保留',
      type: 'warning',
    })
    await request.delete(`/recommendations/${recommendId}/favorite`)
    ElMessage.success('已取消收藏')
    favorites.value = favorites.value.filter(item => item.id !== recommendId)
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '操作失败')
    }
  }
}

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

onMounted(() => {
  fetchFavorites()
})
</script>

<style scoped lang="scss">
.favorites-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.favorites-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.favorites-header {
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

.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  align-items: start;
}

.favorite-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }
}

.card-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  width: 28px;
  height: 28px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #e6a23c;
  font-size: 14px;
  transition: all 0.2s;

  &:hover {
    background: rgba(0, 0, 0, 0.7);
    color: #f56c6c;
  }
}

.card-cover {
  height: 140px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.card-body {
  padding: 14px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-summary {
  font-size: 13px;
  color: #909399;
  margin: 0 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.card-stats {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #909399;

  span {
    display: flex;
    align-items: center;
    gap: 3px;
  }
}

@media screen and (max-width: 768px) {
  .favorites-container {
    padding: 12px;
  }

  .favorites-grid {
    grid-template-columns: 1fr;
  }

  .card-cover {
    height: 120px;
  }
}
</style>
