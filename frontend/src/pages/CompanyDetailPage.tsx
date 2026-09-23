import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApplicationCard, ApplicationListHeader } from '../components/ApplicationCard'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { CompanyFormModal } from '../components/forms/CompanyFormModal'
import { Icon } from '../components/Icon'
import { Modal } from '../components/Modal'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { useAsync } from '../hooks/useAsync'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { companyApi } from '../services/api'

export function CompanyDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const company = useAsync(() => companyApi.get(id), [id])
  const applications = useAsync(() => companyApi.applications(id), [id])
  const [dialog, setDialog] = useState<'edit' | 'delete' | 'application' | null>(null)
  useDocumentTitle(company.data?.name ?? 'Company')

  const back = (
    <Link to="/companies" className="back-link">
      <Icon name="arrowLeft" size={16} /> Companies
    </Link>
  )
  if (company.error && !company.data) {
    return (
      <>
        {back}
        <ErrorState error={company.error} onRetry={company.reload} />
      </>
    )
  }
  const c = company.data
  if (!c) return <LoadingState />
  const hasApplications = (applications.data?.length ?? c.applicationCount) > 0

  return (
    <>
      {back}
      <header className="page-header detail-header">
        <div className="company-card-head">
          <span className="company-avatar company-avatar-large" aria-hidden="true">
            {c.name.charAt(0).toUpperCase()}
          </span>
          <div>
            <h1>{c.name}</h1>
            <p className="detail-subtitle">{[c.industry, c.location].filter(Boolean).join(' · ') || 'No details yet'}</p>
          </div>
        </div>
        <div className="header-actions">
          <button type="button" className="button button-secondary" onClick={() => setDialog('edit')}>
            <Icon name="edit" /> Edit
          </button>
          <button type="button" className="button button-ghost-danger" onClick={() => setDialog('delete')}>
            <Icon name="trash" /> Delete
          </button>
        </div>
      </header>

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card">
            <header className="section-header">
              <h2>Applications</h2>
              <button type="button" className="button button-secondary button-small" onClick={() => setDialog('application')}>
                <Icon name="plus" size={16} /> Add application
              </button>
            </header>
            {applications.error && <ErrorState error={applications.error} onRetry={applications.reload} />}
            {applications.data?.length === 0 && (
              <EmptyState icon="briefcase" title="No applications at this company yet" />
            )}
            {applications.data && applications.data.length > 0 && (
              <div className="list-card list-card-flush">
                <ApplicationListHeader showCompany={false} />
                {applications.data.map((a) => (
                  <ApplicationCard key={a.id} application={a} showCompany={false} />
                ))}
              </div>
            )}
          </section>
        </div>
        <aside className="card detail-side">
          <h2>About</h2>
          <dl className="details-list details-list-single">
            <div>
              <dt>Website</dt>
              <dd>
                {c.website ? (
                  <a href={c.website} target="_blank" rel="noopener noreferrer" className="external-link">
                    {c.website.replace(/^https?:\/\//, '')} <Icon name="external" size={14} />
                  </a>
                ) : (
                  '—'
                )}
              </dd>
            </div>
            <div>
              <dt>Industry</dt>
              <dd>{c.industry ?? '—'}</dd>
            </div>
            <div>
              <dt>Location</dt>
              <dd>{c.location ?? '—'}</dd>
            </div>
          </dl>
          <h3 className="notes-heading">Notes</h3>
          {c.notes ? <p className="notes">{c.notes}</p> : <p className="muted">No notes yet.</p>}
        </aside>
      </div>

      {dialog === 'edit' && (
        <CompanyFormModal
          company={c}
          onClose={() => setDialog(null)}
          onSaved={(saved) => {
            company.setData(() => saved)
            setDialog(null)
          }}
        />
      )}
      {dialog === 'delete' && hasApplications && (
        <Modal
          title="Can’t delete this company yet"
          size="small"
          onClose={() => setDialog(null)}
          footer={
            <button type="button" className="button button-primary" onClick={() => setDialog(null)}>
              OK
            </button>
          }
        >
          <p className="confirm-message">
            {c.name} still has applications. To keep your application history intact, a company can only be
            deleted once its applications have been deleted or moved to another company.
          </p>
        </Modal>
      )}
      {dialog === 'delete' && !hasApplications && (
        <ConfirmDialog
          title="Delete company?"
          message={`${c.name} will be deleted permanently.`}
          onConfirm={async () => {
            await companyApi.remove(c.id)
            navigate('/companies', { replace: true })
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'application' && (
        <ApplicationFormModal
          defaultCompanyId={c.id}
          onClose={() => setDialog(null)}
          onSaved={(a) => navigate(`/applications/${a.id}`)}
        />
      )}
    </>
  )
}
