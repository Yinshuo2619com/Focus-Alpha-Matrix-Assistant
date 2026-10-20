<template>
  <div class="schedule-grid-wrapper">
    <WeekSelector />

    <!-- 结束模式显示空状态 -->
    <div v-if="currentWeek === END_WEEK" class="empty-state">
      <el-empty description="课程结束" />
    </div>

    <div v-else class="schedule-table-container">
      <table class="schedule-table">
        <thead>
          <tr>
            <th class="time-col">节次</th>
            <th v-for="day in visibleWeekdays" :key="day.value">{{ day.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="section in sections" :key="section">
            <td class="time-col">{{ section }}节</td>
            <template v-for="day in visibleWeekdays" :key="day.value">
              <!-- 跳过被合并的单元格 -->
              <td
                v-if="!isCellHidden(day.value, section)"
                class="course-cell"
                :rowspan="getCellRowspan(day.value, section)"
              >
                <!-- 单门课程：全宽显示 -->
                <div
                  v-if="getCourses(day.value, section).length === 1"
                  class="course-block"
                  :style="{ backgroundColor: getCourse(day.value, section)?.color || '#409EFF' }"
                >
                  <div class="course-name">{{ getCourse(day.value, section)?.courseName }}</div>
                  <div class="course-detail">{{ getCourse(day.value, section)?.teacher }}</div>
                  <div class="course-detail">{{ getCourse(day.value, section)?.location }}</div>
                  <div v-if="currentWeek === PREVIEW_WEEK" class="course-weeks">{{ getCourse(day.value, section)?.weeks }}</div>
                </div>

                <!-- 两门冲突课程：并排显示 -->
                <div
                  v-else-if="getCourses(day.value, section).length === 2"
                  class="course-conflict-pair"
                >
                  <div
                    v-for="course in getCourses(day.value, section)"
                    :key="course.id"
                    class="course-block course-block-half"
                    :style="{ backgroundColor: course.color || '#409EFF' }"
                  >
                    <div class="course-name">{{ course.courseName }}</div>
                    <div class="course-detail">{{ course.teacher }}</div>
                    <div class="course-detail">{{ course.location }}</div>
                    <div v-if="currentWeek === PREVIEW_WEEK" class="course-weeks">{{ course.weeks }}</div>
                  </div>
                </div>

                <!-- 超过两门冲突课程：第一门 + 折叠指示器 -->
                <div
                  v-else-if="getCourses(day.value, section).length > 2"
                  class="course-conflict-pair"
                >
                  <div
                    class="course-block course-block-half"
                    :style="{ backgroundColor: getCourses(day.value, section)[0].color || '#409EFF' }"
                  >
                    <div class="course-name">{{ getCourses(day.value, section)[0].courseName }}</div>
                    <div class="course-detail">{{ getCourses(day.value, section)[0].teacher }}</div>
                  </div>
                  <div
                    class="course-block course-block-half conflict-fold"
                    @click="expandConflict(day.value, section)"
                  >
                    +{{ getCourses(day.value, section).length - 1 }}
                  </div>
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
import { storeToRefs } from 'pinia'
import { ElMessageBox } from 'element-plus'
import type { CourseEntry } from '@/types/schedule'
import { useScheduleStore } from '@/stores/schedule'
import WeekSelector from '@/components/WeekSelector.vue'

const props = defineProps<{
  courses: CourseEntry[]
  semester?: string
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

const scheduleStore = useScheduleStore()
const { currentWeek } = storeToRefs(scheduleStore)
const { PREVIEW_WEEK, END_WEEK } = scheduleStore

const parseWeeks = (weeks: string): number[] => {
  const result: number[] = []
  if (!weeks) return result
  weeks.split(',').forEach(part => {
    const trimmed = part.trim()
    // 匹配范围，可选 (单) 或 (双) 后缀
    const rangeMatch = trimmed.match(/^(\d+)[~-](\d+)(?:[（(](单|双)[）)])?$/)
    if (rangeMatch) {
      const start = parseInt(rangeMatch[1])
      const end = parseInt(rangeMatch[2])
      const parity = rangeMatch[3] // '单' | '双' | undefined
      for (let i = start; i <= end; i++) {
        if (!parity || (parity === '单' && i % 2 === 1) || (parity === '双' && i % 2 === 0)) {
          result.push(i)
        }
      }
    } else if (trimmed) {
      const num = parseInt(trimmed)
      if (!isNaN(num)) result.push(num)
    }
  })
  return result
}

const filteredCourses = computed(() => {
  if (currentWeek.value === PREVIEW_WEEK) {
    // 预览模式：显示所有课程
    return props.courses
  } else if (currentWeek.value === END_WEEK) {
    // 结束模式：不显示任何课程
    return []
  } else {
    // 普通模式：按周数过滤
    return props.courses.filter(c => {
      const weeks = parseWeeks(c.weeks)
      return weeks.includes(currentWeek.value)
    })
  }
})

const courseMap = computed(() => {
  const map = new Map<string, CourseEntry[]>()
  filteredCourses.value.forEach(c => {
    for (let s = c.startSection; s <= c.endSection; s++) {
      const key = `${c.dayOfWeek}-${s}`
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(c)
    }
  })
  return map
})

const getCourse = (day: number, section: number): CourseEntry | undefined => {
  return courseMap.value.get(`${day}-${section}`)?.[0]
}

const getCourses = (day: number, section: number): CourseEntry[] => {
  return courseMap.value.get(`${day}-${section}`) || []
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

// 展开冲突课程对话框
const expandConflict = (day: number, section: number) => {
  const courses = getCourses(day, section)
  ElMessageBox.alert(
    courses.map(c => `${c.courseName} - ${c.teacher} - ${c.location}`).join('<br>'),
    '课程冲突详情',
    {
      confirmButtonText: '确定',
      dangerouslyUseHTMLString: true
    }
  )
}

const availableWeeks = computed(() => {
  const allWeeks = new Set<number>()
  props.courses.forEach(c => parseWeeks(c.weeks).forEach(w => allWeeks.add(w)))
  return Array.from(allWeeks).sort((a, b) => a - b)
})

// 移动端自动隐藏无课的周末列
const isMobile = ref(window.innerWidth <= 768)
const visibleWeekdays = computed(() => {
  if (!isMobile.value) return weekdays
  const activeDays = new Set(filteredCourses.value.map(c => c.dayOfWeek))
  return weekdays.filter(d => activeDays.has(d.value))
})

// 根据学期起始日期或学期字符串推算当前周
const autoSelectWeek = () => {
  if (availableWeeks.value.length === 0) return

  let semesterStart: Date | null = null

  // 优先使用数据库存储的起始日期
  if (props.startDate) {
    semesterStart = new Date(props.startDate)
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
  window.addEventListener('resize', () => {
    isMobile.value = window.innerWidth <= 768
  })
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
  background: var(--page-bg);
  font-weight: 600;
  color: var(--text-regular);
  padding: 8px 4px;
  height: 40px;
}

.schedule-table td {
  padding: 0;
  height: 60px;
}

.time-col {
  width: 50px;
  background: var(--page-bg);
  font-size: 11px;
  color: var(--text-secondary);
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

.course-weeks {
  font-size: 9px;
  opacity: 0.8;
  line-height: 1.3;
  margin-top: 2px;
}

.course-conflict-pair {
  display: flex;
  gap: 2px;
  min-height: 100%;
}

.course-block-half {
  flex: 1;
  min-width: 0;
}

.conflict-fold {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #909399 !important;
  color: white;
  font-weight: bold;
  font-size: 12px;
  cursor: pointer;
  border-radius: 6px;
}

.conflict-fold:hover {
  opacity: 0.85;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

/* 手机端适配 */
@media screen and (max-width: 768px) {
  .schedule-table th {
    padding: 4px 2px;
    height: 28px;
    font-size: 11px;
  }

  .schedule-table td {
    height: 38px;
  }

  .time-col {
    width: 36px;
    font-size: 9px;
    padding: 2px !important;
  }

  .course-cell {
    padding: 1px;
  }

  .course-block {
    padding: 3px 4px;
    border-radius: 4px;
    gap: 1px;
  }

  .course-name {
    font-size: 10px;
    line-height: 1.2;
  }

  .course-detail {
    font-size: 8px;
    line-height: 1.2;
  }

  .course-conflict-pair {
    gap: 1px;
  }

  .conflict-fold {
    font-size: 10px;
    border-radius: 4px;
  }

  .week-selector {
    margin-bottom: 8px;
  }
}
</style>
