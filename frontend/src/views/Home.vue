<template>
  <div class="home-page">
    <status-bar />
    <div class="status-bar-placeholder"></div>

    <div class="home-container">
      <div class="schedule-section">
        <div class="schedule-header">
          <h3 class="schedule-title">我的课表</h3>
          <div class="header-actions">
            <el-button v-if="scheduleStore.hasSchedule" size="small" @click="handleRefresh" :loading="refreshing">
              <el-icon><Refresh /></el-icon>
              刷新课表
            </el-button>
            <el-button type="primary" size="small" @click="goToImport">
              <el-icon><Plus /></el-icon>
              打开课程表
            </el-button>
          </div>
        </div>

        <div v-if="scheduleStore.loading" class="loading-wrapper">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <ScheduleGrid
          v-else-if="scheduleStore.hasSchedule"
          :courses="scheduleStore.scheduleData.courses"
          :semester="scheduleStore.scheduleData.schedule?.semester"
          :current-week="scheduleStore.scheduleData.schedule?.currentWeek"
          :start-date="scheduleStore.scheduleData.schedule?.startDate"
        />

        <div v-else class="empty-schedule">
          <el-empty description="暂无课表数据">
            <el-button type="primary" @click="goToImport">导入课表</el-button>
          </el-empty>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, Loading, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import StatusBar from '@/components/StatusBar.vue'
import ScheduleGrid from '@/components/ScheduleGrid.vue'
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const refreshing = ref(false)

onMounted(() => {
  scheduleStore.fetchSchedule()
})

const goToImport = () => window.open('/schedule-import', '_blank')

const handleRefresh = async () => {
  refreshing.value = true
  try {
    const msg = await scheduleStore.refreshSchedule()
    ElMessage.success(msg)
  } catch (err: any) {
    if (err.message?.includes('未登录')) {
      ElMessage.warning('教务系统未登录，请先打开课表导入页面登录')
    } else {
      ElMessage.error(err.message || '刷新失败')
    }
  } finally {
    refreshing.value = false
  }
}
</script>

<style scoped lang="scss">
.home-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.status-bar-placeholder {
  height: 60px;
}

.home-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.schedule-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.schedule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.schedule-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #909399;
}

.empty-schedule {
  padding: 20px 0;
}
</style>
