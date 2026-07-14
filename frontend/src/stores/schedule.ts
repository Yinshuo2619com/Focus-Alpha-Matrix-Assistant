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

  const startDate = computed(() => scheduleData.value.schedule?.startDate || null)
  const maxWeek = ref<number>(0)

  const fetchSchedule = async () => {
    loading.value = true
    try {
      const res: any = await request.get('/schedule/current')
      if (res.code === 200 && res.data.schedule) {
        scheduleData.value = res.data
        hasSchedule.value = res.data.courses.length > 0
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
    if (!startDate.value || !maxWeek.value) {
      currentWeek.value = PREVIEW_WEEK
      return
    }

    const today = new Date()
    const start = new Date(startDate.value)
    const end = new Date(start)
    end.setDate(end.getDate() + maxWeek.value * 7)

    if (today < start) {
      currentWeek.value = PREVIEW_WEEK
    } else if (today > end) {
      currentWeek.value = END_WEEK
    } else {
      // 计算当前周数
      const diffTime = today.getTime() - start.getTime()
      const diffWeeks = Math.floor(diffTime / (7 * 24 * 60 * 60 * 1000)) + 1
      currentWeek.value = Math.min(diffWeeks, maxWeek.value)
    }
  }

  return {
    scheduleData, loading, hasSchedule,
    semesterList, currentWeek, startDate, maxWeek,
    fetchSchedule, loginToEdu, extractSchedule, saveSchedule,
    deleteSchedule, refreshSchedule, generateShareToken,
    fetchSharedSchedule, importSharedSchedule,
    fetchSemesterList, autoSelectWeek,
    PREVIEW_WEEK, END_WEEK
  }
})
