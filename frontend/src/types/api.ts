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
  statusBreakdown: { status: ApplicationStatus; count: number }[]
  applicationsPerMonth: { month: string; count: number }[]
  upcomingInterviews: Interview[]
}

/** Job details read from a posting link; nothing is saved until the user submits the form. */
export interface JobLinkPreview {
  jobUrl: string
  jobTitle: string | null
  companyName: string | null
  /** The user's existing company with the same name, if any. */
  matchedCompanyId: number | null
  location: string | null
  employmentType: EmploymentType | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  notes: string | null
  source: 'PAGE_DATA' | 'AI' | 'PAGE_DATA_AND_AI'
}

export const EMAIL_CATEGORIES = [
  'APPLICATION_RECEIVED',
  'INTERVIEW',
  'ASSESSMENT',
  'OFFER',
  'REJECTED',
  'OTHER',
  'NOT_JOB_RELATED',
] as const
export type EmailCategory = (typeof EMAIL_CATEGORIES)[number]

export interface StatusChange {
  applicationId: number
  jobTitle: string
  companyName: string
  from: ApplicationStatus
  to: ApplicationStatus
  emailSummary: string | null
}

export interface SyncSummary {
  startedAt: string
  finishedAt: string
  messagesScanned: number
  emailsAnalyzed: number
  statusesUpdated: number
  error: string | null
  changes: StatusChange[]
  /** Short summary written by the local AI. */
  overview: string | null
}

export interface GmailStatus {
  /** False when the server has no Google OAuth credentials. */
  configured: boolean
  connected: boolean
  gmailAddress: string | null
  lastSyncedAt: string | null
  lastSyncError: string | null
  syncing: boolean
  /** While checking: emails processed so far / emails to look at (0 while listing). */
  progressDone: number
  progressTotal: number
  lastRun: SyncSummary | null
}

/** Where one application stands, with the latest email the AI matched to it. */
export interface ApplicationUpdate {
  id: number
  jobTitle: string
  companyName: string
  status: ApplicationStatus
  applicationDate: string | null
  updatedAt: string
  lastEmailAt: string | null
  lastEmailCategory: EmailCategory | null
  lastEmailSummary: string | null
  changedInLastCheck: boolean
  noReply: boolean
}

export interface UpdateReport {
  lastCheck: SyncSummary | null
  applications: ApplicationUpdate[]
}

export interface EmailEvent {
  id: number
  applicationId: number | null
  jobTitle: string | null
  companyName: string | null
  sender: string | null
  subject: string | null
  receivedAt: string
  category: EmailCategory
  confidence: number | null
  summary: string | null
  statusBefore: ApplicationStatus | null
  /** The status the email moved the application to; null if it didn't change it. */
  statusAfter: ApplicationStatus | null
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: { field: string; message: string }[]
}
