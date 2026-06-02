<template>
  <div class="share-page">
    <div class="share-header">
      <h2 class="share-title">课表分享</h2>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="error" class="error-wrapper">
      <el-empty :description="error">
        <el-button type="primary" @click="router.push('/home')">返回首页</el-button>
      </el-empty>
    </div>

    <template v-else-if="scheduleData">
      <div class="schedule-meta">
        <div class="meta-item">
          <span class="meta-label">学期</span>
          <span class="meta-value">{{ scheduleData.schedule?.semester }}</span>
        </div>
        <div class="meta-item" v-if="scheduleData.schedule?.startDate">
          <span class="meta-label">开学日期</span>
          <span class="meta-value">{{ scheduleData.schedule.startDate }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">课程数</span>
          <span class="meta-value">{{ scheduleData.courses.length }} 门</span>
        </div>
      </div>

      <div class="schedule-section">
        <ScheduleGrid
          :courses="scheduleData.courses"
          :semester="scheduleData.schedule?.semester"
          :start-date="scheduleData.schedule?.startDate"
        />
      </div>

      <div class="action-bar">
        <el-button
          type="success"
          size="large"
          :loading="importing"
          @click="handleImport"
        >
          导入到我的课表
        </el-button>
        <el-button
          v-if="scheduleData.schedule?.startDate"
          type="primary"
          size="large"
          @click="handleExportIcs"
        >
          <el-icon><Download /></el-icon>
          导出 .ics 日历文件
        </el-button>
        <el-button size="large" @click="router.push('/home')">
          返回首页
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ScheduleGrid from '@/components/ScheduleGrid.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useUserStore } from '@/stores/user'
import { generateICS, downloadICS } from '@/utils/ics'
import type { ScheduleData } from '@/types/schedule'

const route = useRoute()
const router = useRouter()
const scheduleStore = useScheduleStore()
const userStore = useUserStore()

const loading = ref(true)
const importing = ref(false)
const error = ref('')
const scheduleData = ref<ScheduleData | null>(null)

onMounted(async () => {
  const token = route.params.token as string
  if (!token) {
    error.value = '无效的分享链接'
    loading.value = false
    return
  }

  try {
    const data = await scheduleStore.fetchSharedSchedule(token)
    scheduleData.value = data
  } catch (err: any) {
    error.value = err.message || '课表数据加载失败'
  } finally {
    loading.value = false
  }
})

const handleImport = async () => {
  const shareToken = route.params.token as string
  if (!userStore.token) {
    // 未登录：保存 token 到 sessionStorage，跳转登录
    sessionStorage.setItem('pendingImportToken', shareToken)
    router.push({ path: '/login', query: { redirect: `/schedule/share/${shareToken}` } })
    return
  }

  importing.value = true
  try {
    const msg = await scheduleStore.importSharedSchedule(shareToken)
    ElMessage.success(msg || '导入成功')
    router.push('/home')
  } catch (err: any) {
    ElMessage.error(err.message || '导入失败')
  } finally {
    importing.value = false
  }
}

const handleExportIcs = () => {
  if (!scheduleData.value?.schedule?.startDate) {
    ElMessage.warning('缺少开学日期，无法导出')
    return
  }
  const { schedule, courses } = scheduleData.value
  const icsContent = generateICS(courses, schedule.startDate!, schedule.semester)
  const filename = `${schedule.semester}课表.ics`
  downloadICS(filename, icsContent)
  ElMessage.success('导出成功，请用日历 App 打开')
}
</script>

<style scoped lang="scss">
.share-page {
  min-height: 100vh;
  background: var(--page-bg);
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.share-header {
  text-align: center;
  margin-bottom: 16px;
}

.share-title {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.schedule-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--card-bg);
  border-radius: 12px;
  box-shadow: var(--card-shadow);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.meta-value {
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 500;
}

.schedule-section {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--card-shadow);
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--text-secondary);
}

.error-wrapper {
  padding: 40px 0;
}

.action-bar {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  padding-bottom: 40px;
}
</style>
