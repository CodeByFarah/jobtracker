import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CompanyCard } from '../components/CompanyCard'
import { CompanyFormModal } from '../components/forms/CompanyFormModal'
import { Icon } from '../components/Icon'
import { SearchBar } from '../components/SearchBar'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { useAsync } from '../hooks/useAsync'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { companyApi } from '../services/api'

export function CompaniesPage() {
  useDocumentTitle('Companies')
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const query = useDebouncedValue(search.trim(), 300)
  const companies = useAsync(() => companyApi.list(query), [query])
  const [creating, setCreating] = useState(false)

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Companies</h1>
          <p className="muted">Employers you’re applying to or keeping an eye on.</p>
        </div>
        <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
          <Icon name="plus" /> New company
        </button>
      </header>

      <div className="toolbar">
        <SearchBar value={search} onChange={setSearch} placeholder="Search companies" label="Search companies" />
      </div>

      {companies.error && <ErrorState error={companies.error} onRetry={companies.reload} />}
      {!companies.error && !companies.data && <LoadingState />}
      {companies.data?.length === 0 &&
        (query ? (
          <EmptyState icon="search" title={`No companies match “${query}”`} />
        ) : (
          <EmptyState
            icon="building"
            title="No companies yet"
            text="Add a company to start tracking applications there."
            action={
              <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
                <Icon name="plus" /> Add company
              </button>
            }
          />
        ))}
      {companies.data && companies.data.length > 0 && (
        <div className={`card-grid ${companies.loading ? 'is-refreshing' : ''}`}>
          {companies.data.map((company) => (
            <CompanyCard key={company.id} company={company} />
          ))}
        </div>
      )}

      {creating && (
        <CompanyFormModal onClose={() => setCreating(false)} onSaved={(company) => navigate(`/companies/${company.id}`)} />
      )}
    </>
  )
}
