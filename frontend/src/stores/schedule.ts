import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import type { ScheduleData, CourseEntry } from '@/types/schedule'

export const useScheduleStore = defineStore('schedule', () => {
  const scheduleData = ref<ScheduleData>({ schedule: null, courses: [] })
  const loading = ref(false)
  const hasSchedule = ref(false)

  const fetchSchedule = async () => {
    loading.value = true
    try {
      const res: any = await request.get('/schedule/current')
      if (res.code === 200) {
        scheduleData.value = res.data
        hasSchedule.value = res.data.courses.length > 0
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

  return { scheduleData, loading, hasSchedule, fetchSchedule, loginToEdu, extractSchedule, saveSchedule, deleteSchedule, refreshSchedule }
})
