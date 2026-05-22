export interface CourseEntry {
  id?: number
  courseName: string
  teacher: string
  location: string
  dayOfWeek: number      // 1=Mon .. 7=Sun
  startSection: number   // 1-12
  endSection: number
  weeks: string          // "1-16" or "1,3,5"
  color?: string
}

export interface Schedule {
  id: number
  userId: number
  semester: string
  academicYear: string
  startDate?: string | null
  updatedAt: string
}

export interface ScheduleData {
  schedule: Schedule | null
  courses: CourseEntry[]
}
