import { Link } from 'react-router-dom'
import type { Application } from '../types/api'
import { EMPLOYMENT_LABELS, formatDate, formatSalary } from '../utils/format'
import { StatusBadge } from './StatusBadge'

/**
 * One application in a list. Laid out as a table-like row on wide screens and as a
 * stacked card on narrow ones (see .app-row in index.css). The whole row is a link.
 */
export function ApplicationCard({ application, showCompany = true }: { application: Application; showCompany?: boolean }) {
  const salary = formatSalary(application.salaryMin, application.salaryMax, application.salaryCurrency)
  return (
    <Link to={`/applications/${application.id}`} className="app-row">
      <div className="app-row-main">
        <span className="app-row-title">{application.jobTitle}</span>
        <span className="app-row-sub">
          {showCompany && <span className="app-row-company">{application.company.name}</span>}
          {application.employmentType && <span>{EMPLOYMENT_LABELS[application.employmentType]}</span>}
          {salary && <span>{salary}</span>}
        </span>
      </div>
      <span className="app-row-location">{application.location ?? '—'}</span>
      <span className="app-row-date">
        <span className="app-row-label">Applied </span>
        {formatDate(application.applicationDate)}
      </span>
      <span className="app-row-status">
        <StatusBadge status={application.status} />
      </span>
    </Link>
  )
}

export function ApplicationListHeader({ showCompany = true }: { showCompany?: boolean }) {
  return (
    <div className="app-row app-row-header" aria-hidden="true">
      <span>{showCompany ? 'Position / company' : 'Position'}</span>
      <span>Location</span>
      <span>Applied</span>
      <span>Status</span>
    </div>
  )
}
