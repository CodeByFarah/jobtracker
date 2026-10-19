import type {
  Application,
  ApplicationStatus,
  EmailCategory,
  EmploymentType,
  InterviewStatus,
  InterviewType,
} from '../types/api'

export const EMAIL_CATEGORY_LABELS: Record<EmailCategory, string> = {
  APPLICATION_RECEIVED: 'Application received',
  INTERVIEW: 'Interview',
  ASSESSMENT: 'Assessment',
  OFFER: 'Offer',
  REJECTED: 'Rejection',
  OTHER: 'Update',
  NOT_JOB_RELATED: 'Not related',
}

/** Reuses the status badge colours: each email kind looks like the status it leads to. */
export const EMAIL_CATEGORY_TINT: Record<EmailCategory, ApplicationStatus> = {
  APPLICATION_RECEIVED: 'APPLIED',
  INTERVIEW: 'INTERVIEW',
  ASSESSMENT: 'SCREENING',
  OFFER: 'OFFER',
  REJECTED: 'REJECTED',
  OTHER: 'SAVED',
  NOT_JOB_RELATED: 'WITHDRAWN',
}

/** Days without any news after which an open application counts as "no reply". */
export const NO_REPLY_DAYS = 14

/**
 * True when the user applied (or heard back) more than {@link NO_REPLY_DAYS} days ago and the
 * application hasn't moved since. Any status change, including one made by the email sync,
 * updates `updatedAt` and resets the clock.
 */
export function isAwaitingReply(application: Application, now = new Date()): boolean {
  if (application.status !== 'APPLIED' && application.status !== 'SCREENING') return false
  const applied = application.applicationDate ? parseLocalDate(application.applicationDate).getTime() : 0
  const lastActivity = Math.max(applied, new Date(application.updatedAt).getTime())
  return now.getTime() - lastActivity > NO_REPLY_DAYS * 86_400_000
}

/** "just now", "5 min ago", "3 h ago", "2 days ago". */
export function timeAgo(value: string, now = new Date()): string {
  const seconds = Math.round((now.getTime() - new Date(value).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  return days === 1 ? 'yesterday' : `${days} days ago`
}

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
