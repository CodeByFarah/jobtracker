import { Link } from 'react-router-dom'
import type { Company } from '../types/api'
import { pluralize } from '../utils/format'
import { Icon } from './Icon'

export function CompanyCard({ company }: { company: Company }) {
  return (
    <Link to={`/companies/${company.id}`} className="card company-card">
      <div className="company-card-head">
        <span className="company-avatar" aria-hidden="true">
          {company.name.charAt(0).toUpperCase()}
        </span>
        <div>
          <h3>{company.name}</h3>
          {company.industry && <p className="muted">{company.industry}</p>}
        </div>
      </div>
      <div className="company-card-meta">
        {company.location && (
          <span>
            <Icon name="mapPin" size={15} /> {company.location}
          </span>
        )}
        <span>
          <Icon name="briefcase" size={15} /> {pluralize(company.applicationCount, 'application')}
        </span>
      </div>
    </Link>
  )
}
