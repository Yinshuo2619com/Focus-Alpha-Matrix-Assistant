<template>
  <div v-if="filteredExams.length > 0" class="exam-card">
    <div class="card-title-row">
      <span class="card-title">考试安排</span>
      <el-icon class="refresh-btn" :class="{ spinning: refreshing }" @click="handleRefresh">
        <Refresh />
      </el-icon>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else class="exam-list">
      <div v-for="(exam, index) in filteredExams" :key="index" class="exam-item">
        <div class="exam-course">{{ exam.courseName }}</div>
        <div class="exam-info">
          <span class="exam-time">{{ exam.dateTime }}</span>
          <span class="exam-location">{{ exam.room }} {{ exam.building }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Loading, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface Exam {
  courseName: string
  dateTime: string
  room: string
  building: string
  campus: string
}

const loading = ref(false)
const refreshing = ref(false)
const exams = ref<Exam[]>([])

/**
 * 解析日期字符串，支持 "2026-06-29" 和 "2026年6月29日" 格式
 */
const parseDate = (dateStr: string): Date | null => {
  if (!dateStr) return null

  // 尝试解析 "2026-06-29" 格式
  const standardMatch = dateStr.match(/(\d{4})-(\d{1,2})-(\d{1,2})/)
  if (standardMatch) {
    return new Date(`${standardMatch[1]}-${standardMatch[2].padStart(2, '0')}-${standardMatch[3].padStart(2, '0')}`)
  }

  // 尝试解析 "2026年6月29日" 格式
  const chineseMatch = dateStr.match(/(\d{4})年(\d{1,2})月(\d{1,2})日/)
  if (chineseMatch) {
    return new Date(`${chineseMatch[1]}-${chineseMatch[2].padStart(2, '0')}-${chineseMatch[3].padStart(2, '0')}`)
  }

  return null
}

/**
 * 获取考试列表中最后一天的日期
 */
const getLastExamDate = (examList: Exam[]): Date | null => {
  if (!examList.length) return null

  const dates = examList
    .map(e => parseDate(e.dateTime.split(' ')[0]))
    .filter((d): d is Date => d !== null && !isNaN(d.getTime()))

  if (!dates.length) return null
  return new Date(Math.max(...dates.map(d => d.getTime())))
}

/**
 * 过滤后的考试列表
 * 规则：最后考试日期+30天之前显示全部，超过则隐藏整个卡片
 */
const filteredExams = computed(() => {
  const now = new Date()

  // 获取最后考试日期
  const lastExamDate = getLastExamDate(exams.value)
  if (!lastExamDate) return []

  // 计算过期时间：最后考试日期 + 30天
  const expireDate = new Date(lastExamDate)
  expireDate.setDate(expireDate.getDate() + 30)

  // 超过过期时间，隐藏整个卡片
  if (now > expireDate) return []

  // 未过期，显示全部考试
  return exams.value
})

const fetchExams = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/exam/list')
    if (res.code === 200) {
      exams.value = res.data || []
    }
  } catch {
    // 静默处理错误
    exams.value = []
  } finally {
    loading.value = false
  }
}

const handleRefresh = async () => {
  if (refreshing.value) return
  refreshing.value = true
  try {
    const res: any = await request.get('/exam/list')
    if (res.code === 200) {
      exams.value = res.data || []
    }
  } catch {
    // ignore
  } finally {
    refreshing.value = false
  }
}

onMounted(fetchExams)
</script>

<style scoped lang="scss">
.exam-card {
  background: var(--card-bg);
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
  color: var(--text-primary);
}

.refresh-btn {
  font-size: 16px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color 0.2s, transform 0.3s;

  &:hover {
    color: var(--accent);
  }

  &.spinning {
    animation: spin 0.8s linear infinite;
    color: var(--accent);
    pointer-events: none;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--text-secondary);
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exam-item {
  padding: 10px 12px;
  background: var(--page-bg);
  border-radius: 8px;
  border-left: 3px solid var(--accent);
}

.exam-course {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.exam-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.exam-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.exam-location {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
