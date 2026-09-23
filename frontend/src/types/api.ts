// Mirrors the backend DTOs (com.jobtrack.dto.*). Dates are ISO strings as sent over JSON:
// LocalDate -> "2026-09-15", Instant -> "2026-09-15T12:00:00Z".

export const APPLICATION_STATUSES = [
  'SAVED',
  'APPLIED',
  'SCREENING',
  'INTERVIEW',
  'OFFER',
  'ACCEPTED',
  'REJECTED',
  'WITHDRAWN',
] as const
export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number]

export const EMPLOYMENT_TYPES = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'TEMPORARY'] as const
export type EmploymentType = (typeof EMPLOYMENT_TYPES)[number]

export const INTERVIEW_TYPES = ['RECRUITER', 'TECHNICAL', 'SYSTEM_DESIGN', 'BEHAVIORAL', 'FINAL', 'OTHER'] as const
export type InterviewType = (typeof INTERVIEW_TYPES)[number]

export const INTERVIEW_STATUSES = ['SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED'] as const
export type InterviewStatus = (typeof INTERVIEW_STATUSES)[number]

export interface User {
  id: number
  email: string
  fullName: string
  headline: string | null
  location: string | null
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

export interface Company {
  id: number
  name: string
  website: string | null
  industry: string | null
  location: string | null
  notes: string | null
  applicationCount: number
  createdAt: string
  updatedAt: string
}

export interface CompanyInput {
  name: string
  website?: string | null
  industry?: string | null
  location?: string | null
  notes?: string | null
}

export interface Application {
  id: number
  jobTitle: string
  company: { id: number; name: string }
  jobUrl: string | null
  location: string | null
  employmentType: EmploymentType | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  applicationDate: string | null
  status: ApplicationStatus
  allowedTransitions: ApplicationStatus[]
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface ApplicationInput {
  companyId: number
  jobTitle: string
  jobUrl?: string | null
  location?: string | null
  employmentType?: EmploymentType | null
  salaryMin?: number | null
  salaryMax?: number | null
  salaryCurrency?: string | null
  applicationDate?: string | null
  notes?: string | null
  /** Only used when creating; status changes go through the status endpoint. */
  status?: ApplicationStatus
}

export interface StatusHistoryEntry {
  id: number
  applicationId: number
  previousStatus: ApplicationStatus | null
  newStatus: ApplicationStatus
  changedAt: string
}

export interface Interview {
  id: number
  applicationId: number
  jobTitle: string
  companyName: string
  type: InterviewType
  scheduledAt: string
  durationMinutes: number | null
  interviewerName: string | null
  location: string | null
  notes: string | null
  status: InterviewStatus
  createdAt: string
  updatedAt: string
}

export interface InterviewInput {
  type: InterviewType
  scheduledAt: string
  durationMinutes?: number | null
  interviewerName?: string | null
  location?: string | null
  notes?: string | null
  status?: InterviewStatus
}

export interface Task {
  id: number
  applicationId: number
  jobTitle: string
  companyName: string
  title: string
  description: string | null
  dueDate: string | null
  completed: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface TaskInput {
  title: string
  description?: string | null
  dueDate?: string | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Dashboard {
  totalApplications: number
  applicationsThisMonth: number
  activeApplications: number
  totalInterviews: number
  upcomingInterviewCount: number
  offers: number
  rejected: number
  outstandingTasks: number
  overdueTasks: number
  statusBreakdown: { status: ApplicationStatus; count: number }[]
  applicationsPerMonth: { month: string; count: number }[]
  upcomingInterviews: Interview[]
  upcomingTasks: Task[]
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: { field: string; message: string }[]
}
