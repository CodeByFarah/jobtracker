import type { ApplicationSort } from '../services/api'
import type { ApplicationStatus, EmploymentType } from '../types/api'

export interface ApplicationFilters {
  status: ApplicationStatus | ''
  employmentType: EmploymentType | ''
  location: string
  sort: ApplicationSort
  direction: 'ASC' | 'DESC'
}

export const DEFAULT_FILTERS: ApplicationFilters = {
  status: '',
  employmentType: '',
  location: '',
  sort: 'APPLICATION_DATE',
  direction: 'DESC',
}
