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
        <div class="recommend-header">
          <div class="header-left">
            <template v-if="isAdmin">
              <el-button-group>
                <el-button :type="toolViewMode === 'all' ? 'primary' : 'default'" @click="toolViewMode = 'all'">全部工具</el-button>
                <el-button :type="toolViewMode === 'mine' ? 'primary' : 'default'" @click="toolViewMode = 'mine'">我的发布</el-button>
                <el-button :type="toolViewMode === 'drafts' ? 'primary' : 'default'" @click="toolViewMode = 'drafts'">我的草稿</el-button>
              </el-button-group>
            </template>
          </div>
          <el-button v-if="isAdmin" type="primary" @click="$router.push('/tool/new')">
            <el-icon><Plus /></el-icon>
            发布工具
          </el-button>
        </div>

        <div v-if="toolsLoading" class="loading-wrapper">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <div v-else-if="tools.length === 0" class="empty-state">
          <el-empty :description="isAdmin ? '暂无工具，点击上方发布' : '暂无小工具'">
            <el-button v-if="isAdmin" type="primary" @click="$router.push('/tool/new')">发布第一个工具</el-button>
          </el-empty>
        </div>

        <div v-else class="recommend-grid">
          <div
            v-for="item in tools"
            :key="item.id"
            class="recommend-card"
            :class="{ dragging: draggingId === item.id, 'drag-over': dragOverId === item.id && draggingId !== item.id }"
            :draggable="isAdmin && toolViewMode === 'all'"
            @dragstart="handleDragStart($event, item.id)"
            @dragover="handleDragOver($event, item.id)"
            @dragleave="handleDragLeave"
            @drop="handleDrop($event, item.id)"
            @dragend="handleDragEnd"
            @click="handleCardClick(item)"
          >
            <div v-if="isAdmin && toolViewMode !== 'all'" class="card-badges">
              <div class="card-delete" @click.stop="handleDeleteTool(item.id)">
                <el-icon><Delete /></el-icon>
              </div>
              <div v-if="toolViewMode === 'drafts'" class="draft-badge">草稿</div>
            </div>
            <div v-if="item.coverUrl" class="card-cover">
              <img :src="item.coverUrl" alt="封面" />
            </div>
            <div class="card-body">
              <h3 class="card-title">
                {{ item.title }}
                <el-icon v-if="isRedirectCard(item)" class="external-link-icon"><Link /></el-icon>
              </h3>
              <p class="card-summary">{{ item.summary || '暂无简介' }}</p>
              <div class="card-footer">
                <span class="card-author">{{ item.authorName }}</span>
                <span class="card-time">{{ formatTime(item.updatedAt) }}</span>
              </div>
              <div v-if="toolViewMode !== 'drafts'" class="card-stats">
                <span><el-icon><View /></el-icon> {{ item.views }}</span>
                <span v-if="!isRedirectCard(item)"><el-icon><Star /></el-icon> {{ item.favorites }}</span>
              </div>
            </div>
          </div>
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
            <div class="header-left">
              <el-button-group>
                <el-button :type="viewMode === 'all' ? 'primary' : 'default'" @click="viewMode = 'all'">全部发布</el-button>
                <el-button :type="viewMode === 'mine' ? 'primary' : 'default'" @click="viewMode = 'mine'">我的发布</el-button>
                <el-button :type="viewMode === 'drafts' ? 'primary' : 'default'" @click="viewMode = 'drafts'">我的草稿</el-button>
              </el-button-group>
            </div>
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
              @click="viewMode === 'drafts' ? $router.push(`/recommend/${item.id}/edit`) : $router.push(viewMode === 'mine' ? `/recommend/${item.id}?mine=1` : `/recommend/${item.id}`)"
            >
              <div v-if="viewMode !== 'all'" class="card-badges">
                <div class="card-delete" @click.stop="handleDelete(item.id)">
                  <el-icon><Delete /></el-icon>
                </div>
                <div v-if="viewMode === 'drafts'" class="draft-badge">草稿</div>
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
                <div v-if="viewMode !== 'drafts'" class="card-stats">
                  <span><el-icon><View /></el-icon> {{ item.views }}</span>
                  <span><el-icon><Star /></el-icon> {{ item.favorites }}</span>
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
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Loading, Lock, View, Star, Delete, Link } from '@element-plus/icons-vue'
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
  favorites: number
  status: number
  createdAt: string
  updatedAt: string
}

const store = useUserStore()
const route = useRoute()
const router = useRouter()
const isLoggedIn = computed(() => !!store.token)
const isAdmin = computed(() => store.userInfo?.username === 'admin' || store.userInfo?.role === 'admin')
const activeTab = ref(route.query.from === 'nav' ? 'tools' : (sessionStorage.getItem('toolsActiveTab') || 'tools'))

// 清除 query 参数，保持 URL 干净
if (route.query.from === 'nav') {
  router.replace({ path: '/tools' })
}

// ========== 小工具 ==========
const toolsLoading = ref(false)
type ToolViewMode = 'all' | 'mine' | 'drafts'
const toolViewMode = ref<ToolViewMode>('all')
const tools = ref<Recommendation[]>([])
const draggingId = ref<number | null>(null)
const dragOverId = ref<number | null>(null)

const toolUrlMap: Record<ToolViewMode, string> = {
  all: '/tools',
  mine: '/tools/mine',
  drafts: '/tools/drafts',
}

const fetchTools = async () => {
  toolsLoading.value = true
  try {
    const url = toolUrlMap[toolViewMode.value]
    const res = await request.get(url) as any
    tools.value = res.data || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取工具列表失败')
  } finally {
    toolsLoading.value = false
  }
}

const handleDeleteTool = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除这个工具吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/tools/${id}`)
    ElMessage.success('删除成功')
    tools.value = tools.value.filter(item => item.id !== id)
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

const handleDragStart = (e: DragEvent, id: number) => {
  draggingId.value = id
  e.dataTransfer!.effectAllowed = 'move'
}

const handleDragOver = (e: DragEvent, id: number) => {
  e.preventDefault()
  e.dataTransfer!.dropEffect = 'move'
  dragOverId.value = id
}

const handleDragLeave = () => {
  dragOverId.value = null
}

const handleDrop = async (e: DragEvent, targetId: number) => {
  e.preventDefault()
  dragOverId.value = null
  const sourceId = draggingId.value
  if (sourceId === null || sourceId === targetId) return

  const sourceIdx = tools.value.findIndex(t => t.id === sourceId)
  const targetIdx = tools.value.findIndex(t => t.id === targetId)
  if (sourceIdx === -1 || targetIdx === -1) return

  const item = tools.value.splice(sourceIdx, 1)[0]
  tools.value.splice(targetIdx, 0, item)
  draggingId.value = null

  try {
    await request.put('/tools/reorder', tools.value.map(t => t.id))
  } catch {
    ElMessage.error('排序保存失败')
  }
}

const handleDragEnd = () => {
  draggingId.value = null
  dragOverId.value = null
}

const isRedirectCard = (item: Recommendation) => item.status === 2

const handleCardClick = (item: Recommendation) => {
  if (toolViewMode.value === 'drafts') {
    router.push(`/tool/${item.id}/edit`)
  } else if (isRedirectCard(item) && toolViewMode.value !== 'mine') {
    request.post(`/recommendations/${item.id}/view`)
    window.open(item.contentUrl, '_blank')
  } else if (isRedirectCard(item) && toolViewMode.value === 'mine') {
    router.push(`/tool/${item.id}/edit`)
  } else {
    router.push(toolViewMode.value === 'mine' ? `/recommend/${item.id}?mine=1` : `/recommend/${item.id}`)
  }
}

// ========== 用户推荐 ==========
const loading = ref(false)
type ViewMode = 'all' | 'mine' | 'drafts'
const viewMode = ref<ViewMode>('all')
const recommendations = ref<Recommendation[]>([])

const urlMap: Record<ViewMode, string> = {
  all: '/recommendations',
  mine: '/recommendations/mine',
  drafts: '/recommendations/drafts',
}

const fetchRecommendations = async () => {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const res = await request.get(urlMap[viewMode.value]) as any
    recommendations.value = res.data || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取推荐列表失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除这条推荐吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/recommendations/${id}`)
    ElMessage.success('删除成功')
    recommendations.value = recommendations.value.filter(item => item.id !== id)
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
  return `${d.getFullYear()}-${month}-${day}`
}

watch(activeTab, (tab) => {
  sessionStorage.setItem('toolsActiveTab', tab)
  if (tab === 'tools') {
    fetchTools()
  } else if (tab === 'recommend' && isLoggedIn.value) {
    fetchRecommendations()
  }
})

watch(toolViewMode, () => {
  fetchTools()
})

watch(viewMode, () => {
  if (isLoggedIn.value) {
    fetchRecommendations()
  }
})

onMounted(() => {
  if (activeTab.value === 'tools') {
    fetchTools()
  } else if (activeTab.value === 'recommend' && isLoggedIn.value) {
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
  display: flex;
  align-items: flex-start;
  padding-top: 20px;
}

.tab-bar {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
  width: 120px;
  margin-left: 20px;
  position: sticky;
  top: 80px;
}

.tab-item {
  text-align: left;
  padding: 14px 16px;
  font-size: 15px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;

  &:hover {
    color: #409eff;
    background: #f5f7fa;
  }

  &.active {
    color: #409eff;
    font-weight: 600;
    border-left-color: #409eff;
    background: #ecf5ff;
  }
}

.tab-content {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  min-height: 400px;
  flex: 1;
  min-width: 0;
  max-width: 780px;
  margin: 0 auto;
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
  justify-content: space-between;
  align-items: center;
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
  align-items: start;
}

.recommend-card {
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

  &.dragging {
    opacity: 0.4;
    transform: scale(0.98);
  }

  &.drag-over {
    border-color: #409eff;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.3);
  }
}

.card-badges {
  position: absolute;
  top: 8px;
  left: 0;
  right: 0;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  padding: 0 8px;
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

.draft-badge {
  background: #e6a23c;
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.card-delete {
  width: 28px;
  height: 28px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: white;
  font-size: 14px;
  transition: background 0.2s;

  &:hover {
    background: #f56c6c;
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

  .external-link-icon {
    font-size: 13px;
    color: #409eff;
    margin-left: 4px;
    vertical-align: middle;
  }
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
    flex-direction: column;
  }

  .tab-bar {
    flex-direction: row;
    width: 100%;
  }

  .tab-item {
    flex: 1;
    text-align: center;
    padding: 14px 0;
    border-left: none;
    border-bottom: 2px solid transparent;

    &.active {
      border-left-color: transparent;
      border-bottom-color: #409eff;
      background: white;
    }
  }

  .tab-content {
    border-radius: 12px;
  }

  .recommend-grid {
    grid-template-columns: 1fr;
  }

  .card-cover {
    height: 120px;
  }
}
</style>
