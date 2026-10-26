import type {
  Application,
  ApplicationInput,
  ApplicationStatus,
  AuthResponse,
  Company,
  CompanyInput,
  Dashboard,
  EmailEvent,
  EmploymentType,
  GmailStatus,
  Interview,
  InterviewInput,
  JobLinkPreview,
  Page,
  StatusHistoryEntry,
  UpdateReport,
  User,
} from '../types/api'
import { buildQuery, http } from './http'

export const authApi = {
  register: (body: { email: string; password: string; fullName: string }) =>
    http.post<AuthResponse>('/api/auth/register', body),
  login: (body: { email: string; password: string }) => http.post<AuthResponse>('/api/auth/login', body),
}

export const userApi = {
  me: () => http.get<User>('/api/users/me'),
  update: (body: { fullName: string; headline: string | null; location: string | null }) =>
    http.put<User>('/api/users/me', body),
  deleteAccount: (password: string) => http.post<void>('/api/users/me/delete', { password }),
}

export const companyApi = {
  list: (q?: string) => http.get<Company[]>(`/api/companies${buildQuery({ q })}`),
  get: (id: number) => http.get<Company>(`/api/companies/${id}`),
  create: (body: CompanyInput) => http.post<Company>('/api/companies', body),
  update: (id: number, body: CompanyInput) => http.put<Company>(`/api/companies/${id}`, body),
  remove: (id: number) => http.delete(`/api/companies/${id}`),
  applications: (id: number) => http.get<Application[]>(`/api/companies/${id}/applications`),
}

export type ApplicationSort = 'APPLICATION_DATE' | 'UPDATED_AT' | 'CREATED_AT' | 'JOB_TITLE'

export interface ApplicationQuery {
  q?: string
  status?: ApplicationStatus | ''
  location?: string
  employmentType?: EmploymentType | ''
  companyId?: number
  sort?: ApplicationSort
  direction?: 'ASC' | 'DESC'
  page?: number
  size?: number
}

export const applicationApi = {
  search: (query: ApplicationQuery) => http.get<Page<Application>>(`/api/applications${buildQuery({ ...query })}`),
  get: (id: number) => http.get<Application>(`/api/applications/${id}`),
  create: (body: ApplicationInput) => http.post<Application>('/api/applications', body),
  update: (id: number, body: ApplicationInput) => http.put<Application>(`/api/applications/${id}`, body),
  changeStatus: (id: number, status: ApplicationStatus) =>
    http.patch<Application>(`/api/applications/${id}/status`, { status }),
  remove: (id: number) => http.delete(`/api/applications/${id}`),
  history: (id: number) => http.get<StatusHistoryEntry[]>(`/api/applications/${id}/history`),
  interviews: (id: number) => http.get<Interview[]>(`/api/applications/${id}/interviews`),
  createInterview: (id: number, body: InterviewInput) =>
    http.post<Interview>(`/api/applications/${id}/interviews`, body),
  emails: (id: number) => http.get<EmailEvent[]>(`/api/applications/${id}/emails`),
  /** Reads job details from a posting link with the local AI. Can take up to a minute. */
  importLink: (url: string) => http.post<JobLinkPreview>('/api/applications/import-link', { url }),
}

export const gmailApi = {
  status: () => http.get<GmailStatus>('/api/gmail'),
  connectUrl: () => http.get<{ url: string }>('/api/gmail/connect-url'),
  sync: () => http.post<GmailStatus>('/api/gmail/sync'),
  disconnect: () => http.delete('/api/gmail'),
  events: (limit = 20) => http.get<EmailEvent[]>(`/api/gmail/events${buildQuery({ limit })}`),
  report: () => http.get<UpdateReport>('/api/gmail/report'),
}

const logoCache = new Map<string, Promise<string | null>>()

/**
 * A company logo as an object URL (the image needs the auth header, so a plain <img src> can't
 * load it). Cached per company and logo version, so every list reuses one download.
 */
export function companyLogoUrl(companyId: number, version: string): Promise<string | null> {
  const key = `${companyId}:${version}`
  let url = logoCache.get(key)
  if (!url) {
    url = http
      .blob(`/api/companies/${companyId}/logo?v=${encodeURIComponent(version)}`)
      .then((blob) => (blob ? URL.createObjectURL(blob) : null))
    logoCache.set(key, url)
  }
  return url
}

export const interviewApi = {
  list: (scope: 'ALL' | 'UPCOMING' | 'PAST') => http.get<Interview[]>(`/api/interviews${buildQuery({ scope })}`),
  update: (id: number, body: InterviewInput) => http.put<Interview>(`/api/interviews/${id}`, body),
  remove: (id: number) => http.delete(`/api/interviews/${id}`),
}

export const dashboardApi = {
  get: () => {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
    return http.get<Dashboard>(`/api/dashboard${buildQuery({ timezone })}`)
  },
}
