<template>
  <div class="electricity-card">
    <div class="card-title-row">
      <span class="card-title">我的电费</span>
      <el-icon v-if="store.userInfo?.roomId" class="refresh-btn" :class="{ spinning: refreshing }" @click="handleRefresh"><Refresh /></el-icon>
    </div>

    <div v-if="!loading && !summary.roomName" class="no-bind">
      <p class="no-bind-text">绑定宿舍查看电费</p>
      <el-button type="primary" size="small" @click="$router.push('/profile')">去绑定</el-button>
    </div>

    <div v-else-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else class="card-content">
      <div class="room-name">{{ summary.roomName }}</div>

      <div class="stat-row">
        <div class="stat-item">
          <span class="stat-label">当前余额</span>
          <span class="stat-value">{{ summary.balance != null ? summary.balance + ' 度' : '--' }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">昨日耗电</span>
          <span class="stat-value consumption">{{ summary.consumption != null ? summary.consumption + ' 度' : '--' }}</span>
        </div>
      </div>

      <div class="more-link" @click="$router.push('/electricity')">
        查看更多
        <el-icon><ArrowRight /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { Loading, ArrowRight, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const store = useUserStore()
const loading = ref(false)
const refreshing = ref(false)
const summary = ref<any>({})

const fetchSummary = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/electricity/realtime')
    if (res.code === 200 && res.data) {
      summary.value = res.data
    }
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

const handleRefresh = async () => {
  if (refreshing.value) return
  refreshing.value = true
  try {
    const res: any = await request.get('/electricity/realtime')
    if (res.code === 200 && res.data) {
      summary.value = res.data
    }
  } catch {
    // ignore
  } finally {
    refreshing.value = false
  }
}

onMounted(fetchSummary)
</script>

<style scoped lang="scss">
.electricity-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.refresh-btn {
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  transition: color 0.2s, transform 0.3s;

  &:hover {
    color: #409eff;
  }

  &.spinning {
    animation: spin 0.8s linear infinite;
    color: #409eff;
    pointer-events: none;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.no-bind {
  text-align: center;
  padding: 12px 0;
}

.no-bind-text {
  color: #909399;
  font-size: 14px;
  margin-bottom: 12px;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  color: #909399;
}

.card-content {
  .room-name {
    font-size: 15px;
    font-weight: 500;
    color: #409eff;
    margin-bottom: 16px;
  }
}

.stat-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;

  &.consumption {
    color: #e6a23c;
  }
}

.more-link {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.8;
  }
}
</style>
