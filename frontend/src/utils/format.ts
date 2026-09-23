import type { ApplicationStatus, EmploymentType, InterviewStatus, InterviewType } from '../types/api'

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  SCREENING: 'Screening',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export const EMPLOYMENT_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: 'Full-time',
  PART_TIME: 'Part-time',
  CONTRACT: 'Contract',
  INTERNSHIP: 'Internship',
  TEMPORARY: 'Temporary',
}

export const INTERVIEW_TYPE_LABELS: Record<InterviewType, string> = {
  RECRUITER: 'Recruiter',
  TECHNICAL: 'Technical',
  SYSTEM_DESIGN: 'System design',
  BEHAVIORAL: 'Behavioral',
  FINAL: 'Final',
  OTHER: 'Other',
}

export const INTERVIEW_STATUS_LABELS: Record<InterviewStatus, string> = {
  SCHEDULED: 'Scheduled',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  RESCHEDULED: 'Rescheduled',
}

/** Parses a LocalDate string ("2026-09-15") as a local calendar date, without time-zone shifts. */
function parseLocalDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

const dateFormat = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

export function formatDate(value: string | null | undefined): string {
  return value ? dateFormat.format(parseLocalDate(value)) : '—'
}

export function formatInstantDate(value: string | null | undefined): string {
  return value ? dateFormat.format(new Date(value)) : '—'
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTimeFormat.format(new Date(value)) : '—'
}

export function formatMonth(yearMonth: string, style: 'short' | 'long' = 'short'): string {
  const [y, m] = yearMonth.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { month: style, year: style === 'long' ? 'numeric' : undefined })
    .format(new Date(y, m - 1, 1))
}

/** Today as a LocalDate string in the browser's time zone. */
export function todayIso(): string {
  return toLocalDateString(new Date())
}

export function toLocalDateString(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** "2026-09-15T12:00:00Z" -> value for <input type="datetime-local"> in local time. */
export function instantToLocalInput(value: string): string {
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${toLocalDateString(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** <input type="datetime-local"> value (local time) -> ISO instant. */
export function localInputToInstant(value: string): string {
  return new Date(value).toISOString()
}

export function isOverdue(dueDate: string | null, completed: boolean): boolean {
  return !completed && dueDate !== null && dueDate < todayIso()
}

/** Human description of how far away a LocalDate is: "Today", "Tomorrow", "In 3 days", "2 days ago". */
export function relativeDay(value: string): string {
  const days = Math.round((parseLocalDate(value).getTime() - parseLocalDate(todayIso()).getTime()) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Tomorrow'
  if (days === -1) return 'Yesterday'
  return days > 0 ? `In ${days} days` : `${-days} days ago`
}

export function formatSalary(min: number | null, max: number | null, currency: string | null): string | null {
  if (min === null && max === null) return null
  const format = (n: number) => {
    try {
      return new Intl.NumberFormat(undefined, {
        style: currency ? 'currency' : 'decimal',
        currency: currency ?? undefined,
        notation: 'compact',
        maximumFractionDigits: 1,
      }).format(n)
    } catch {
      return `${n.toLocaleString()}${currency ? ` ${currency}` : ''}`
    }
  }
  if (min !== null && max !== null) return min === max ? format(min) : `${format(min)} – ${format(max)}`
  return min !== null ? `From ${format(min)}` : `Up to ${format(max!)}`
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`
}
