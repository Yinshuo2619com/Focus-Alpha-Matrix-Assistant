<template>
  <div class="import-page">
    <div class="import-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h3>导入课表</h3>
      <span class="status-hint" v-if="iframeLoaded">已加载</span>
      <span class="status-hint loading" v-else>加载中...</span>
    </div>

    <div class="semester-input">
      <span>当前学期：</span>
      <el-input v-model="semester" placeholder="如 2025-2026-2" size="small" style="width: 200px" />
      <span style="margin-left: 16px">开学日期：</span>
      <el-date-picker v-model="startDate" type="date" placeholder="选择开学日期" size="small" value-format="YYYY-MM-DD" style="width: 160px" />
    </div>

    <div class="edu-frame-wrapper">
      <EduSystemFrame ref="eduFrameRef" school-id="default" @loaded="iframeLoaded = true" @error="handleFrameError" />
    </div>

    <!-- Fixed bottom action bar -->
    <div class="action-bar">
      <el-button
        type="primary"
        @click="handleExtract"
        :loading="extracting"
        :disabled="!iframeLoaded"
        size="large"
      >
        <el-icon><Download /></el-icon>
        提取当前页面课表
      </el-button>
    </div>

    <el-dialog v-model="showPreview" title="课表预览" width="80%">
      <p>识别到 <strong>{{ extractedCourses.length }}</strong> 门课程</p>
      <el-table :data="extractedCourses" max-height="400" stripe size="small">
        <el-table-column prop="courseName" label="课程名称" />
        <el-table-column prop="teacher" label="教师" />
        <el-table-column prop="location" label="地点" />
        <el-table-column label="星期" :formatter="(row: any) => weekdays[row.dayOfWeek - 1]" />
        <el-table-column label="节次" :formatter="(row: any) => `${row.startSection}-${row.endSection}`" />
        <el-table-column prop="weeks" label="周次" />
      </el-table>
      <template #footer>
        <el-button @click="showPreview = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">确认保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import EduSystemFrame from '@/components/EduSystemFrame.vue'
import { useScheduleStore } from '@/stores/schedule'
import type { CourseEntry } from '@/types/schedule'
const router = useRouter()
const scheduleStore = useScheduleStore()

const semester = ref('2025-2026-2')
const startDate = ref('')
const iframeLoaded = ref(false)
const extracting = ref(false)
const saving = ref(false)
const showPreview = ref(false)
const extractedCourses = ref<CourseEntry[]>([])

const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const goBack = () => router.push('/home')

const handleFrameError = (msg: string) => {
  ElMessage.error(msg)
}

// Listen for messages from iframe
const handleMessage = (event: MessageEvent) => {
  if (event.data && event.data.type === 'schedule-data') {
    const courses = event.data.courses
    if (courses && courses.length > 0) {
      extractedCourses.value = courses
      showPreview.value = true
      ElMessage.success(`识别到 ${courses.length} 门课程`)
    } else {
      ElMessage.warning('未识别到课程数据，请确认已打开课表页面')
    }
    extracting.value = false
  }
}

onMounted(() => {
  window.addEventListener('message', handleMessage)
})

onUnmounted(() => {
  window.removeEventListener('message', handleMessage)
})

const handleExtract = async () => {
  if (!semester.value.trim()) {
    ElMessage.warning('请填写学期信息')
    return
  }
  extracting.value = true
  try {
    const iframe = document.querySelector('iframe') as HTMLIFrameElement
    if (!iframe) {
      ElMessage.error('找不到iframe元素')
      return
    }
    const doc = iframe.contentDocument
    if (!doc) {
      ElMessage.error('无法访问iframe内容，请确保已登录')
      return
    }

    const courses = await extractFromDOM(doc)
    if (courses.length > 0) {
      extractedCourses.value = courses
      showPreview.value = true
      ElMessage.success(`识别到 ${courses.length} 门课程`)
    } else {
      ElMessage.warning('未识别到课程数据，请确认已打开课表页面')
    }

    // 自动提取起始日期
    const startDateEl = doc.querySelector('#startDate')
    if (startDateEl?.textContent?.trim()) {
      startDate.value = startDateEl.textContent.trim()
    }
  } catch (err: any) {
    ElMessage.error(err.message || '提取失败')
  } finally {
    extracting.value = false
  }
}

async function extractFromDOM(doc: Document): Promise<CourseEntry[]> {
  const courses: CourseEntry[] = []
  const columns = doc.querySelectorAll('div.columns.weekday')
  for (let idx = 0; idx < columns.length; idx++) {
    const col = columns[idx]
    const dayOfWeek = idx + 1
    const cards = col.querySelectorAll('div.card-view')
    for (let ci = 0; ci < cards.length; ci++) {
      const card = cards[ci]
      // 跳过免听课程
      if (card.textContent?.includes('免听')) continue
      const info = card.querySelector('p.card-content-info')
      if (!info) continue

      const entry: any = { dayOfWeek, weeks: '' }

      // 按 <br> 分割获取各行，清理 &nbsp;
      const lines = info.innerHTML.split(/<br\s*\/?>/i).map(l => {
        const tmp = document.createElement('div')
        tmp.innerHTML = l
        return (tmp.textContent || '').replace(/&nbsp;/g, ' ').trim()
      }).filter(l => l.length > 0)
      if (lines.length === 0) continue

      // 第一行：课程名称
      entry.courseName = lines[0]

      // 解析后续行：每行独立提取所有字段（不互斥）
      for (let i = 1; i < lines.length; i++) {
        const line = lines[i]
        if (line.includes('上课组') || line.includes('人数')) continue

        // 周次：(1~17周) 或 (1-17周)
        const weekMatch = line.match(/[（(]([^）)]*周)[）)]/)
        if (weekMatch) entry.weeks = weekMatch[1].replace('周', '')

        // 节次：1 (3,4)
        const dsMatch = line.match(/(\d+)\s*\((\d+(?:\s*,\s*\d+)*)\)/)
        if (dsMatch) {
          const parts = dsMatch[2].split(',').map(s => parseInt(s.trim()))
          entry.startSection = parts[0]
          entry.endSection = parts[parts.length - 1]
        }

        // 地点：教室编码如 L314、A507
        const locMatch = line.match(/([A-Z]-?\d+)/)
        if (locMatch && !entry.location && !locMatch[1].includes('.')) {
          entry.location = locMatch[1]
        }
      }

      // 教师：触发 mouseover 让 popover 出现，然后从 icon-schedule-teacher 获取
      card.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
      await new Promise(resolve => setTimeout(resolve, 100))

      const popoverId = card.getAttribute('aria-describedby')
      if (popoverId) {
        const popover = doc.getElementById(popoverId)
        if (popover) {
          const teacherIcon = popover.querySelector('.icon-schedule-teacher')
          if (teacherIcon?.parentElement) {
            const teacherText = teacherIcon.parentElement.textContent?.trim()
            if (teacherText) entry.teacher = teacherText
          }
        }
      }

      card.dispatchEvent(new MouseEvent('mouseout', { bubbles: true }))

      if (entry.courseName && entry.startSection && entry.weeks) courses.push(entry)
    }
  }
  return courses
}

const handleSave = async () => {
  saving.value = true
  try {
    await scheduleStore.saveSchedule(semester.value, extractedCourses.value, startDate.value || undefined)
    ElMessage.success('课表保存成功')
    router.push('/home')
  } catch (err: any) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.import-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.import-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: var(--card-bg);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 100;
}

.import-header h3 {
  margin: 0;
  flex: 1;
  font-size: 16px;
}

.status-hint {
  font-size: 12px;
  color: #67c23a;
}

.status-hint.loading {
  color: var(--text-secondary);
}

.semester-input {
  position: fixed;
  top: 60px;
  left: 0;
  right: 0;
  height: 50px;
  background: var(--card-bg);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 8px;
  border-bottom: 1px solid #ebeef5;
  z-index: 99;
}

.edu-frame-wrapper {
  margin-top: 110px;
  padding: 16px;
  padding-bottom: 80px;
}

.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: var(--card-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.08);
  z-index: 100;
}
</style>
