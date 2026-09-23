import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApplicationCard, ApplicationListHeader } from '../components/ApplicationCard'
import { FilterPanel } from '../components/FilterPanel'
import { DEFAULT_FILTERS, type ApplicationFilters } from '../utils/filters'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { Icon } from '../components/Icon'
import { Pagination } from '../components/Pagination'
import { SearchBar } from '../components/SearchBar'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { useAsync } from '../hooks/useAsync'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { applicationApi, type ApplicationSort } from '../services/api'
import type { ApplicationStatus, EmploymentType } from '../types/api'

const PAGE_SIZE = 10

/**
 * Search, filters, sort and page all live in the URL, so results survive a reload, the back
 * button returns to the same page, and the dashboard can link to e.g. ?status=OFFER.
 */
export function ApplicationsPage() {
  useDocumentTitle('Applications')
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [creating, setCreating] = useState(false)

  const filters: ApplicationFilters = {
    status: (params.get('status') ?? '') as ApplicationStatus | '',
    employmentType: (params.get('type') ?? '') as EmploymentType | '',
    location: params.get('location') ?? '',
    sort: (params.get('sort') ?? DEFAULT_FILTERS.sort) as ApplicationSort,
    direction: (params.get('dir') ?? DEFAULT_FILTERS.direction) as 'ASC' | 'DESC',
  }
  const page = Math.max(0, Number(params.get('page') ?? '0') || 0)
  const [search, setSearch] = useState(params.get('q') ?? '')
  const query = useDebouncedValue(search.trim(), 300)
  const location = useDebouncedValue(filters.location.trim(), 300)

  const results = useAsync(
    () =>
      applicationApi.search({
        q: query,
        status: filters.status,
        employmentType: filters.employmentType,
        location,
        sort: filters.sort,
        direction: filters.direction,
        page,
        size: PAGE_SIZE,
      }),
    [query, filters.status, filters.employmentType, location, filters.sort, filters.direction, page],
  )

  function update(changes: Record<string, string>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  function onSearch(value: string) {
    setSearch(value)
    update({ q: value.trim() })
  }

  function onFilters(next: ApplicationFilters) {
    update({
      status: next.status,
      type: next.employmentType,
      location: next.location,
      sort: next.sort === DEFAULT_FILTERS.sort ? '' : next.sort,
      dir: next.direction === DEFAULT_FILTERS.direction ? '' : next.direction,
    })
  }

  const data = results.data
  const hasFilters = query !== '' || filters.status !== '' || filters.employmentType !== '' || location !== ''

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Applications</h1>
          <p className="muted">
            {data ? `${data.totalElements} ${hasFilters ? 'matching' : 'in total'}` : 'Every job you’re tracking'}
          </p>
        </div>
        <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
          <Icon name="plus" /> New application
        </button>
      </header>

      <div className="toolbar">
        <SearchBar value={search} onChange={onSearch} placeholder="Search job title or company" label="Search applications" />
        <FilterPanel filters={filters} onChange={onFilters} />
      </div>

      {results.error && <ErrorState error={results.error} onRetry={results.reload} />}
      {!results.error && !data && <LoadingState />}

      {data && data.totalElements === 0 && (
        hasFilters ? (
          <EmptyState icon="search" title="No applications match" text="Try a different search or clear the filters." />
        ) : (
          <EmptyState
            icon="briefcase"
            title="No applications yet"
            text="Add the jobs you’ve applied for or want to apply for."
            action={
              <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
                <Icon name="plus" /> Add application
              </button>
            }
          />
        )
      )}

      {data && data.totalElements > 0 && (
        <div className={`card list-card ${results.loading ? 'is-refreshing' : ''}`} aria-busy={results.loading}>
          <ApplicationListHeader />
          {data.content.map((application) => (
            <ApplicationCard key={application.id} application={application} />
          ))}
          <Pagination
            page={data.page}
            size={data.size}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(p) => update({ page: p > 0 ? String(p) : '' }, false)}
          />
        </div>
      )}

      {creating && (
        <ApplicationFormModal
          onClose={() => setCreating(false)}
          onSaved={(application) => navigate(`/applications/${application.id}`)}
        />
      )}
    </>
  )
}
