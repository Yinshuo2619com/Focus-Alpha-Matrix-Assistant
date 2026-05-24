<template>
  <div class="tools-page">
    <StatusBar />
    <div class="status-bar-placeholder"></div>

    <div class="tools-container">
      <div class="tab-bar">
        <div
          class="tab-item"
          :class="{ active: activeTab === 'tools' }"
          @click="activeTab = 'tools'"
        >
          小工具
        </div>
        <div
          class="tab-item"
          :class="{ active: activeTab === 'recommend' }"
          @click="activeTab = 'recommend'"
        >
          用户推荐
        </div>
      </div>

      <!-- 小工具模块 -->
      <div v-if="activeTab === 'tools'" class="tab-content">
        <div class="placeholder-box">
          <el-icon class="placeholder-icon"><Tools /></el-icon>
          <p>小工具开发中，敬请期待...</p>
        </div>
      </div>

      <!-- 用户推荐模块 -->
      <div v-if="activeTab === 'recommend'" class="tab-content">
        <template v-if="!isLoggedIn">
          <div class="login-hint">
            <el-icon><Lock /></el-icon>
            <span>请先登录后查看用户推荐</span>
            <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
          </div>
        </template>
        <template v-else>
          <div class="recommend-header">
            <el-button type="primary" @click="$router.push('/recommend/new')">
              <el-icon><Plus /></el-icon>
              发布推荐
            </el-button>
          </div>

          <div v-if="loading" class="loading-wrapper">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div v-else-if="recommendations.length === 0" class="empty-state">
            <el-empty description="暂无推荐内容">
              <el-button type="primary" @click="$router.push('/recommend/new')">发布第一条推荐</el-button>
            </el-empty>
          </div>

          <div v-else class="recommend-grid">
            <div
              v-for="item in recommendations"
              :key="item.id"
              class="recommend-card"
              @click="$router.push(`/recommend/${item.id}`)"
            >
              <div class="card-cover">
                <img v-if="item.coverUrl" :src="item.coverUrl" alt="封面" />
                <div v-else class="cover-placeholder">
                  <el-icon><Document /></el-icon>
                </div>
              </div>
              <div class="card-body">
                <h3 class="card-title">{{ item.title }}</h3>
                <p class="card-summary">{{ item.summary }}</p>
                <div class="card-footer">
                  <span class="card-author">{{ item.authorName }}</span>
                  <span class="card-time">{{ formatTime(item.createdAt) }}</span>
                </div>
                <div class="card-stats">
                  <span><el-icon><View /></el-icon> {{ item.views }}</span>
                  <span><el-icon><Star /></el-icon> {{ item.likes }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { Plus, Loading, Lock, Document, View, Star, Tools } from '@element-plus/icons-vue'
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

const store = useUserStore()
const isLoggedIn = computed(() => !!store.token)
const activeTab = ref('tools')
const loading = ref(false)
const recommendations = ref<Recommendation[]>([])

const fetchRecommendations = async () => {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const res = await request.get('/recommendations') as any
    recommendations.value = res.data || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取推荐列表失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

watch(activeTab, (tab) => {
  if (tab === 'recommend' && isLoggedIn.value) {
    fetchRecommendations()
  }
})

onMounted(() => {
  if (activeTab.value === 'recommend' && isLoggedIn.value) {
    fetchRecommendations()
  }
})
</script>

<style scoped lang="scss">
.tools-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.tools-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.tab-bar {
  display: flex;
  gap: 0;
  background: white;
  border-radius: 12px 12px 0 0;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 14px 0;
  font-size: 15px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 2px solid transparent;

  &:hover {
    color: #409eff;
    background: #f5f7fa;
  }

  &.active {
    color: #409eff;
    font-weight: 600;
    border-bottom-color: #409eff;
    background: white;
  }
}

.tab-content {
  background: white;
  border-radius: 0 0 12px 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  min-height: 400px;
}

.placeholder-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #909399;

  .placeholder-icon {
    font-size: 48px;
    margin-bottom: 16px;
  }

  p {
    font-size: 15px;
  }
}

.login-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 0;
  color: #909399;
  font-size: 15px;

  .el-icon {
    font-size: 36px;
  }
}

.recommend-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
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
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.recommend-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
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

  .cover-placeholder {
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    display: flex;
    align-items: center;
    justify-content: center;

    .el-icon {
      font-size: 40px;
      color: rgba(255, 255, 255, 0.6);
    }
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
  .tools-container {
    padding: 12px;
  }

  .recommend-grid {
    grid-template-columns: 1fr;
  }

  .card-cover {
    height: 120px;
  }
}
</style>
