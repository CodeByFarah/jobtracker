import type {
  Application,
  ApplicationInput,
  ApplicationStatus,
  AuthResponse,
  Company,
  CompanyInput,
  Dashboard,
  EmploymentType,
  Interview,
  InterviewInput,
  Page,
  StatusHistoryEntry,
  Task,
  TaskInput,
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
  tasks: (id: number) => http.get<Task[]>(`/api/applications/${id}/tasks`),
  createTask: (id: number, body: TaskInput) => http.post<Task>(`/api/applications/${id}/tasks`, body),
}

export const interviewApi = {
  list: (scope: 'ALL' | 'UPCOMING' | 'PAST') => http.get<Interview[]>(`/api/interviews${buildQuery({ scope })}`),
  update: (id: number, body: InterviewInput) => http.put<Interview>(`/api/interviews/${id}`, body),
  remove: (id: number) => http.delete(`/api/interviews/${id}`),
}

export const taskApi = {
  list: (completed?: boolean) => http.get<Task[]>(`/api/tasks${buildQuery({ completed })}`),
  update: (id: number, body: TaskInput & { completed: boolean }) => http.put<Task>(`/api/tasks/${id}`, body),
  setCompleted: (id: number, completed: boolean) =>
    http.patch<Task>(`/api/tasks/${id}/completion`, { completed }),
  remove: (id: number) => http.delete(`/api/tasks/${id}`),
}

export const dashboardApi = {
  get: () => {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
    return http.get<Dashboard>(`/api/dashboard${buildQuery({ timezone })}`)
  },
}
