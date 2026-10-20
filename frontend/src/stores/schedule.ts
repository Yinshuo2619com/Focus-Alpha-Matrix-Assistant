import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'
import type { ScheduleData, CourseEntry } from '@/types/schedule'

export interface SemesterInfo {
  semesterId: number
  nameZh: string
  startDate: string
  endDate: string
  imported: boolean
}

/** 预览周次：表示"仅预览，不持久化" */
export const PREVIEW_WEEK = -1
/** 结束周次：表示"已结课/无更多数据" */
export const END_WEEK = -2

export const useScheduleStore = defineStore('schedule', () => {
  const scheduleData = ref<ScheduleData>({ schedule: null, courses: [] })
  const loading = ref(false)
  const hasSchedule = ref(false)
  const semesterList = ref<SemesterInfo[]>([])
  const currentWeek = ref<number>(PREVIEW_WEEK)
  const currentSemester = ref<string>('')
  const allCourses = ref<CourseEntry[]>([])
  const weekList = ref<number[]>([])

  const startDate = ref<string | null>(null)
  const maxWeek = computed(() => weekList.value.length)

  const fetchSchedule = async () => {
    loading.value = true
    try {
      const res: any = await request.get('/schedule/current')
      if (res.code === 200 && res.data.schedule) {
        scheduleData.value = res.data
        startDate.value = res.data.schedule.startDate || null
        hasSchedule.value = res.data.courses.length > 0
        // 计算周次列表
        weekList.value = generateWeekList(res.data.courses)
        autoSelectWeek()
      }
    } catch {
      // 响应拦截器已处理错误提示，这里保持 hasSchedule = false 即可
    } finally {
      loading.value = false
    }
  }

  const loginToEdu = async (username: string, password: string) => {
    const res: any = await request.post('/schedule/login', { username, password })
    if (res.code !== 200) throw new Error(res.message)
  }

  const extractSchedule = async (semester: string) => {
    const res: any = await request.post('/schedule/extract', { semester })
    if (res.code === 200) return res.data
    throw new Error(res.message)
  }

  const saveSchedule = async (semester: string, courses: CourseEntry[], startDate?: string) => {
    const res: any = await request.post('/schedule/save', { semester, courses, startDate })
    if (res.code !== 200) throw new Error(res.message)
    await fetchSchedule()
  }

  const deleteSchedule = async (semester: string) => {
    await request.delete('/schedule', { params: { semester } })
    await fetchSchedule()
  }

  const refreshSchedule = async () => {
    const res: any = await request.post('/schedule/refresh')
    if (res.code === 200) {
      await fetchSchedule()
      return res.data as string
    }
    throw new Error(res.message)
  }

  const generateShareToken = async () => {
    const res: any = await request.post('/schedule/share')
    if (res.code === 200) return res.data as { token: string; url: string }
    throw new Error(res.message)
  }

  const fetchSharedSchedule = async (token: string) => {
    const res: any = await request.get(`/schedule/share/${token}`)
    if (res.code === 200) return res.data as ScheduleData
    throw new Error(res.message)
  }

  const importSharedSchedule = async (token: string) => {
    const res: any = await request.post('/schedule/import-shared', { token })
    if (res.code === 200) return res.data as string
    throw new Error(res.message)
  }

  const fetchSemesterList = async () => {
    try {
      const res: any = await request.get('/schedule/semesters')
      semesterList.value = res.data || []
    } catch (error) {
      console.error('获取学期列表失败:', error)
      throw error
    }
  }

  const autoSelectWeek = () => {
    if (!startDate.value || weekList.value.length === 0) {
      currentWeek.value = PREVIEW_WEEK
      return
    }

    const today = new Date()
    const start = new Date(startDate.value)
    const lastWeek = weekList.value[weekList.value.length - 1]
    const end = new Date(start)
    end.setDate(end.getDate() + lastWeek * 7)

    if (today < start) {
      currentWeek.value = PREVIEW_WEEK
    } else if (today > end) {
      currentWeek.value = END_WEEK
    } else {
      // 计算当前周数
      const diffTime = today.getTime() - start.getTime()
      const diffWeeks = Math.floor(diffTime / (7 * 24 * 60 * 60 * 1000)) + 1
      // 选择最接近的可用周次
      const closest = weekList.value.reduce((prev, curr) =>
        Math.abs(curr - diffWeeks) < Math.abs(prev - diffWeeks) ? curr : prev
      )
      currentWeek.value = closest
    }
  }

  /** 解析所有课程的周次信息，生成去重排序的周次列表 */
  const generateWeekList = (courses: CourseEntry[]): number[] => {
    const weeks = new Set<number>()
    for (const course of courses) {
      const parts = course.weeks.split(',')
      for (const part of parts) {
        if (part.includes('-')) {
          const [start, end] = part.split('-').map(Number)
          for (let i = start; i <= end; i++) {
            weeks.add(i)
          }
        } else {
          weeks.add(Number(part))
        }
      }
    }
    return [...weeks].sort((a, b) => a - b)
  }

  const fetchHistoricalSchedule = async (semester: string, semesterId: number) => {
    loading.value = true
    try {
      const res: any = await request.get('/schedule/history', {
        params: { semester, semesterId }
      })
      if (res.code === 200) {
        const { schedule, courses } = res.data
        scheduleData.value = { schedule, courses }
        currentSemester.value = semester
        startDate.value = schedule.startDate || null
        allCourses.value = courses
        weekList.value = generateWeekList(courses)
        autoSelectWeek()
      } else {
        throw new Error(res.message)
      }
    } catch (error) {
      console.error('获取历史课表失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  return {
    scheduleData, loading, hasSchedule,
    semesterList, currentWeek, currentSemester, allCourses, weekList,
    startDate, maxWeek,
    fetchSchedule, loginToEdu, extractSchedule, saveSchedule,
    deleteSchedule, refreshSchedule, generateShareToken,
    fetchSharedSchedule, importSharedSchedule,
    fetchSemesterList, autoSelectWeek, fetchHistoricalSchedule,
    PREVIEW_WEEK, END_WEEK
  }
})
