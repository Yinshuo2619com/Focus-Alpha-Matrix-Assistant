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

function getContiguousRanges(weeks: number[]): [number, number][] {
  const sorted = [...weeks].sort((a, b) => a - b)
  const ranges: [number, number][] = []
  let start = sorted[0]
  let end = sorted[0]
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i] === end + 1) {
      end = sorted[i]
    } else {
      ranges.push([start, end])
      start = sorted[i]
      end = sorted[i]
    }
  }
  ranges.push([start, end])
  return ranges
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

  // 第一周的该星期几的日期（作为计算基准）
  const baseDate = new Date(startDate)
  baseDate.setDate(startDate.getDate() + (course.dayOfWeek - 1))

  const weeksDesc = weeksToRanges(weeks)
  const ranges = getContiguousRanges(weeks)
  const events: string[] = []

  for (let ri = 0; ri < ranges.length; ri++) {
    const [rangeStart, rangeEnd] = ranges[ri]
    const count = rangeEnd - rangeStart + 1

    // 该范围第一周的日期
    const rangeStartDate = new Date(baseDate)
    rangeStartDate.setDate(baseDate.getDate() + (rangeStart - 1) * 7)

    const dtStart = `${toIcsDate(rangeStartDate)}T${time.start}`
    const dtEnd = `${toIcsDate(rangeStartDate)}T${endSectionTime.end}`

    const fields = [
      'BEGIN:VEVENT',
      `DTSTART;TZID=Asia/Shanghai:${dtStart}`,
      `DTEND;TZID=Asia/Shanghai:${dtEnd}`,
    ]

    if (count > 1) {
      fields.push(`RRULE:FREQ=WEEKLY;COUNT=${count};WKST=SU`)
    }

    fields.push(
      `SUMMARY:${course.courseName}`,
      `LOCATION:${course.location || ''}`,
      `DESCRIPTION:教师: ${course.teacher || '未知'}\\n周次: ${weeksDesc} (${weeks.length}周)\\n节次: ${course.startSection}-${course.endSection}节\\n学期: ${semester}`,
      `CATEGORIES:${semester}`,
      `UID:course-${index}-${ri}-${Date.now()}@campus-assistant`,
      'END:VEVENT',
    )
    events.push(fields.join('\r\n'))
  }

  return events.join('\r\n')
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
