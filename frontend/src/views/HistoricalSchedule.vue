<template>
  <div class="historical-schedule-page">
    <status-bar />
    <div class="status-bar-placeholder"></div>

    <div class="historical-container">
      <div class="schedule-section">
        <div class="schedule-header">
          <h3 class="schedule-title">往期课表</h3>
          <div class="header-actions">
            <el-select
              v-model="selectedSemester"
              placeholder="选择学期"
              size="small"
              @change="handleSemesterChange"
            >
              <el-option
                v-for="semester in semesterList"
                :key="semester.semesterId"
                :label="formatSemester(semester.nameZh)"
                :value="semester.nameZh"
              >
                <div class="semester-option">
                  <span>{{ formatSemester(semester.nameZh) }}</span>
                  <el-icon v-if="semester.imported" class="imported-icon">
                    <Check />
                  </el-icon>
                </div>
              </el-option>
            </el-select>
          </div>
        </div>

        <div v-if="loading" class="loading-wrapper">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <ScheduleGrid
          v-else-if="hasData"
          :courses="scheduleData.courses"
          :semester="scheduleData.schedule?.semester"
          :start-date="scheduleData.schedule?.startDate"
        />

        <div v-else class="empty-schedule">
          <el-empty v-if="semesterList.length === 0" description="请先登录教务系统">
            <el-button type="primary" @click="goToLogin">登录教务系统</el-button>
          </el-empty>
          <el-empty v-else description="请选择学期查看课表" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { Check, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import StatusBar from '@/components/StatusBar.vue'
import ScheduleGrid from '@/components/ScheduleGrid.vue'
import { useScheduleStore } from '@/stores/schedule'

const scheduleStore = useScheduleStore()
const { semesterList, scheduleData, loading } = storeToRefs(scheduleStore)
const { fetchSemesterList, fetchHistoricalSchedule } = scheduleStore

const selectedSemester = ref<string>('')
const hasData = computed(() => scheduleData.value.courses.length > 0)

const goToLogin = () => {
  window.open('/schedule-import', '_blank')
}

// 格式化学期名称：2025-2026-1 -> 2025-1
const formatSemester = (nameZh: string): string => {
  if (!nameZh) return ''
  const parts = nameZh.split('-')
  if (parts.length >= 3) {
    return `${parts[0]}-${parts[2]}`
  }
  return nameZh
}

const handleSemesterChange = async (semester: string) => {
  const selected = semesterList.value.find(s => s.nameZh === semester)
  if (!selected) return

  try {
    await fetchHistoricalSchedule(selected.nameZh, selected.semesterId)
  } catch (error: any) {
    if (error.message?.includes('未登录')) {
      ElMessage.warning('教务系统未登录，请先打开课表导入页面登录')
    } else {
      ElMessage.error(error.message || '获取课表失败')
    }
  }
}

onMounted(async () => {
  try {
    await fetchSemesterList()
  } catch (error: any) {
    console.error('获取学期列表失败:', error)
    if (error.message?.includes('未登录')) {
      ElMessage.warning('请先登录教务系统')
    }
  }
})
</script>

<style scoped lang="scss">
.historical-schedule-page {
  min-height: 100vh;
  background: var(--page-bg);
  display: flex;
  flex-direction: column;
}

.status-bar-placeholder {
  height: 60px;
}

.historical-container {
  width: 100%;
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  flex: 1;
  box-sizing: border-box;
}

.schedule-section {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--card-shadow);
}

.schedule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.schedule-title {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--text-secondary);
}

.empty-schedule {
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.semester-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.imported-icon {
  color: #67C23A;
}

@media screen and (max-width: 768px) {
  .historical-container {
    padding: 12px;
  }

  .schedule-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
  }

  .el-select {
    width: 100%;
  }
}
</style>
