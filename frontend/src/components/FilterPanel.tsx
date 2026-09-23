import type { ApplicationSort } from '../services/api'
import { APPLICATION_STATUSES, EMPLOYMENT_TYPES, type ApplicationStatus, type EmploymentType } from '../types/api'
import { DEFAULT_FILTERS, type ApplicationFilters } from '../utils/filters'
import { EMPLOYMENT_LABELS, STATUS_LABELS } from '../utils/format'

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'APPLICATION_DATE:DESC', label: 'Newest applied' },
  { value: 'APPLICATION_DATE:ASC', label: 'Oldest applied' },
  { value: 'UPDATED_AT:DESC', label: 'Recently updated' },
  { value: 'UPDATED_AT:ASC', label: 'Least recently updated' },
  { value: 'JOB_TITLE:ASC', label: 'Job title A–Z' },
]

interface FilterPanelProps {
  filters: ApplicationFilters
  onChange: (filters: ApplicationFilters) => void
}

/** Status / type / location filters and sort order for the application list. */
export function FilterPanel({ filters, onChange }: FilterPanelProps) {
  const set = <K extends keyof ApplicationFilters>(key: K, value: ApplicationFilters[K]) =>
    onChange({ ...filters, [key]: value })
  const active =
    filters.status !== '' || filters.employmentType !== '' || filters.location !== ''

  return (
    <div className="filter-panel">
      <label className="filter">
        <span>Status</span>
        <select value={filters.status} onChange={(e) => set('status', e.target.value as ApplicationStatus | '')}>
          <option value="">All statuses</option>
          {APPLICATION_STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABELS[s]}
            </option>
          ))}
        </select>
      </label>
      <label className="filter">
        <span>Type</span>
        <select
          value={filters.employmentType}
          onChange={(e) => set('employmentType', e.target.value as EmploymentType | '')}
        >
          <option value="">All types</option>
          {EMPLOYMENT_TYPES.map((t) => (
            <option key={t} value={t}>
              {EMPLOYMENT_LABELS[t]}
            </option>
          ))}
        </select>
      </label>
      <label className="filter">
        <span>Location</span>
        <input value={filters.location} onChange={(e) => set('location', e.target.value)} placeholder="Any" />
      </label>
      <label className="filter">
        <span>Sort</span>
        <select
          value={`${filters.sort}:${filters.direction}`}
          onChange={(e) => {
            const [sort, direction] = e.target.value.split(':') as [ApplicationSort, 'ASC' | 'DESC']
            onChange({ ...filters, sort, direction })
          }}
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
      {active && (
        <button
          type="button"
          className="button button-ghost filter-reset"
          onClick={() => onChange({ ...DEFAULT_FILTERS, sort: filters.sort, direction: filters.direction })}
        >
          Clear filters
        </button>
      )}
    </div>
  )
}
