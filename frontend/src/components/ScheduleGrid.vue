<template>
  <div class="schedule-grid-wrapper">
    <div class="week-selector">
      <el-select v-model="currentWeek" size="small" placeholder="选择周次">
        <el-option v-for="w in availableWeeks" :key="w" :label="`第${w}周`" :value="w" />
      </el-select>
    </div>

    <div class="schedule-table-container">
      <table class="schedule-table">
        <thead>
          <tr>
            <th class="time-col">节次</th>
            <th v-for="day in weekdays" :key="day.value">{{ day.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="section in sections" :key="section">
            <td class="time-col">{{ section }}节</td>
            <template v-for="day in 7" :key="day">
              <!-- 跳过被合并的单元格 -->
              <td
                v-if="!isCellHidden(day, section)"
                class="course-cell"
                :rowspan="getCellRowspan(day, section)"
              >
                <div
                  v-if="getCourse(day, section)"
                  class="course-block"
                  :style="{ backgroundColor: getCourse(day, section)?.color || '#409EFF' }"
                >
                  <div class="course-name">{{ getCourse(day, section)?.courseName }}</div>
                  <div class="course-detail">{{ getCourse(day, section)?.teacher }}</div>
                  <div class="course-detail">{{ getCourse(day, section)?.location }}</div>
                </div>
              </td>
            </template>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import type { CourseEntry } from '@/types/schedule'

const props = defineProps<{
  courses: CourseEntry[]
  semester?: string
  currentWeek?: number | null
  startDate?: string | null
}>()

const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
]

const sections = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
const currentWeek = ref(1)

const parseWeeks = (weeks: string): number[] => {
  const result: number[] = []
  if (!weeks) return result
  weeks.split(',').forEach(part => {
    const trimmed = part.trim()
    const rangeMatch = trimmed.match(/^(\d+)[~-](\d+)$/)
    if (rangeMatch) {
      const start = parseInt(rangeMatch[1])
      const end = parseInt(rangeMatch[2])
      for (let i = start; i <= end; i++) result.push(i)
    } else if (trimmed) {
      const num = parseInt(trimmed)
      if (!isNaN(num)) result.push(num)
    }
  })
  return result
}

const activeCourses = computed(() => {
  return props.courses.filter(c => {
    const weeks = parseWeeks(c.weeks)
    return weeks.includes(currentWeek.value)
  })
})

const courseMap = computed(() => {
  const map = new Map<string, CourseEntry>()
  activeCourses.value.forEach(c => {
    for (let s = c.startSection; s <= c.endSection; s++) {
      map.set(`${c.dayOfWeek}-${s}`, c)
    }
  })
  return map
})

const getCourse = (day: number, section: number): CourseEntry | undefined => {
  return courseMap.value.get(`${day}-${section}`)
}

// 获取某个课程占据的连续节次数（从 startSection 开始）
const getCellRowspan = (day: number, section: number): number => {
  const course = getCourse(day, section)
  if (!course) return 1
  // 只在起始节次设置 rowspan
  if (section !== course.startSection) return 1
  return course.endSection - course.startSection + 1
}

// 判断单元格是否被合并隐藏
const isCellHidden = (day: number, section: number): boolean => {
  const course = getCourse(day, section)
  if (!course) return false
  // 非起始节次的单元格被合并
  return section > course.startSection
}

const availableWeeks = computed(() => {
  const allWeeks = new Set<number>()
  props.courses.forEach(c => parseWeeks(c.weeks).forEach(w => allWeeks.add(w)))
  return Array.from(allWeeks).sort((a, b) => a - b)
})

// 根据学期起始日期或学期字符串推算当前周
const autoSelectWeek = () => {
  if (availableWeeks.value.length === 0) return

  // 优先使用教务系统返回的当前周次
  if (props.currentWeek && availableWeeks.value.includes(props.currentWeek)) {
    currentWeek.value = props.currentWeek
    return
  }

  let semesterStart: Date | null = null

  // 优先使用数据库存储的起始日期
  if (props.startDate) {
    semesterStart = new Date(props.startDate + 'T00:00:00')
  } else if (props.semester) {
    // 回退：从学期字符串推算，如 "2025-2026-2"
    const parts = props.semester.split('-')
    if (parts.length >= 3) {
      const semesterNum = parseInt(parts[2])
      if (semesterNum === 1) {
        semesterStart = new Date(parseInt(parts[0]), 8, 1)
      } else {
        semesterStart = new Date(parseInt(parts[1]), 1, 17)
      }
    }
  }

  if (!semesterStart) return

  // 找到学期开始日期所在周的周一
  const dayOfWeek = semesterStart.getDay()
  const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  semesterStart.setDate(semesterStart.getDate() + mondayOffset)

  // 计算当前是第几周
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((today.getTime() - semesterStart.getTime()) / (1000 * 60 * 60 * 24))
  const calculatedWeek = Math.floor(diffDays / 7) + 1

  // 选择最接近的可用周次
  if (availableWeeks.value.includes(calculatedWeek)) {
    currentWeek.value = calculatedWeek
  } else {
    const closest = availableWeeks.value.reduce((prev, curr) =>
      Math.abs(curr - calculatedWeek) < Math.abs(prev - calculatedWeek) ? curr : prev
    )
    currentWeek.value = closest
  }
}

onMounted(() => {
  autoSelectWeek()
})
</script>

<style scoped>
.schedule-grid-wrapper {
  width: 100%;
}

.week-selector {
  margin-bottom: 12px;
}

.schedule-table-container {
  overflow-x: auto;
}

.schedule-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.schedule-table th,
.schedule-table td {
  border: 1px solid #e8e8e8;
  text-align: center;
  font-size: 12px;
  vertical-align: top;
}

.schedule-table th {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
  padding: 8px 4px;
  height: 40px;
}

.schedule-table td {
  padding: 0;
  height: 60px;
}

.time-col {
  width: 50px;
  background: #f5f7fa;
  font-size: 11px;
  color: #909399;
  padding: 4px !important;
}

.course-cell {
  padding: 2px;
}

.course-block {
  border-radius: 6px;
  padding: 6px 8px;
  color: white;
  font-size: 11px;
  text-align: left;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

.course-name {
  font-weight: bold;
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
}

.course-detail {
  font-size: 10px;
  opacity: 0.9;
  line-height: 1.3;
}
</style>
