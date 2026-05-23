import type { CourseEntry } from '@/types/schedule'

// 节次对应的上课/下课时间
const SECTION_TIMES: Record<number, { start: string; end: string }> = {
  1:  { start: '080000', end: '084500' },
  2:  { start: '085500', end: '094000' },
  3:  { start: '100000', end: '104500' },
  4:  { start: '105500', end: '114000' },
  5:  { start: '140000', end: '144500' },
  6:  { start: '145500', end: '154000' },
  7:  { start: '160000', end: '164500' },
  8:  { start: '165500', end: '174000' },
  9:  { start: '190000', end: '194500' },
  10: { start: '195500', end: '204000' },
  11: { start: '210000', end: '214500' },
  12: { start: '215500', end: '224000' },
}

function pad(n: number): string {
  return n.toString().padStart(2, '0')
}

function toIcsDate(date: Date): string {
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
}

function parseWeeks(weeksStr: string): number[] {
  const result: number[] = []
  weeksStr.split(',').forEach(part => {
    const trimmed = part.trim()
    const rangeMatch = trimmed.match(/^(\d+)[~-](\d+)(?:[（(](单|双)[）)])?$/)
    if (rangeMatch) {
      const start = parseInt(rangeMatch[1])
      const end = parseInt(rangeMatch[2])
      const parity = rangeMatch[3]
      for (let i = start; i <= end; i++) {
        if (!parity || (parity === '单' && i % 2 === 1) || (parity === '双' && i % 2 === 0)) {
          result.push(i)
        }
      }
    } else {
      const num = parseInt(trimmed)
      if (!isNaN(num)) result.push(num)
    }
  })
  return result
}

function weeksToRanges(weeks: number[]): string {
  if (weeks.length === 0) return ''
  const sorted = [...weeks].sort((a, b) => a - b)
  const ranges: string[] = []
  let start = sorted[0]
  let end = sorted[0]
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i] === end + 1) {
      end = sorted[i]
    } else {
      ranges.push(start === end ? `${start}` : `${start}-${end}`)
      start = sorted[i]
      end = sorted[i]
    }
  }
  ranges.push(start === end ? `${start}` : `${start}-${end}`)
  return ranges.join(',')
}

function buildEvent(
  course: CourseEntry,
  startDateStr: string,
  semester: string,
  index: number
): string {
  const time = SECTION_TIMES[course.startSection]
  const endSectionTime = SECTION_TIMES[course.endSection]
  if (!time || !endSectionTime) return ''

  const startDate = new Date(startDateStr)
  const weeks = parseWeeks(course.weeks)
  if (weeks.length === 0) return ''

  // 第一周的该星期几的日期
  const firstWeekDate = new Date(startDate)
  firstWeekDate.setDate(startDate.getDate() + (course.dayOfWeek - 1))

  // 最后一周的日期
  const lastWeek = Math.max(...weeks)
  const lastDate = new Date(startDate)
  lastDate.setDate(startDate.getDate() + (lastWeek - 1) * 7 + (course.dayOfWeek - 1))

  const dtStart = `${toIcsDate(firstWeekDate)}T${time.start}`
  const dtEnd = `${toIcsDate(firstWeekDate)}T${endSectionTime.end}`
  const until = `${toIcsDate(lastDate)}T235959`

  // 不上课的周次作为 EXDATE
  const allWeeks = Array.from({ length: lastWeek }, (_, i) => i + 1)
  const offWeeks = allWeeks.filter(w => !weeks.includes(w))
  const exdates = offWeeks.map(w => {
    const d = new Date(startDate)
    d.setDate(startDate.getDate() + (w - 1) * 7 + (course.dayOfWeek - 1))
    return toIcsDate(d)
  })

  const weeksDesc = weeksToRanges(weeks)

  let vevent = [
    'BEGIN:VEVENT',
    `DTSTART;TZID=Asia/Shanghai:${dtStart}`,
    `DTEND;TZID=Asia/Shanghai:${dtEnd}`,
    `RRULE:FREQ=WEEKLY;UNTIL=${until};WKST=SU`,
    `SUMMARY:${course.courseName}`,
    `LOCATION:${course.location || ''}`,
    `DESCRIPTION:教师: ${course.teacher || '未知'}\\n周次: ${weeksDesc} (${weeks.length}周)\\n节次: ${course.startSection}-${course.endSection}节\\n学期: ${semester}`,
    `CATEGORIES:${semester}`,
    `UID:course-${index}-${Date.now()}@campus-assistant`,
  ]

  if (exdates.length > 0) {
    vevent.push(`EXDATE;TZID=Asia/Shanghai:${exdates.join(',')}`)
  }

  vevent.push('END:VEVENT')
  return vevent.join('\r\n')
}

export function generateICS(
  courses: CourseEntry[],
  startDate: string,
  semester: string
): string {
  const events = courses
    .map((c, i) => buildEvent(c, startDate, semester, i))
    .filter(Boolean)
    .join('\r\n')

  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//CampusAssistant//Schedule//CN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    `X-WR-CALNAME:${semester} 课表`,
    'X-WR-TIMEZONE:Asia/Shanghai',
    'BEGIN:VTIMEZONE',
    'TZID:Asia/Shanghai',
    'BEGIN:STANDARD',
    'DTSTART:19700101T000000',
    'TZOFFSETFROM:+0800',
    'TZOFFSETTO:+0800',
    'TZNAME:CST',
    'END:STANDARD',
    'END:VTIMEZONE',
    events,
    'END:VCALENDAR',
  ].join('\r\n')
}

export function downloadICS(filename: string, content: string): void {
  const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
