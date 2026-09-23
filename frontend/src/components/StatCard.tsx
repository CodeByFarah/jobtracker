import { Link } from 'react-router-dom'

interface StatCardProps {
  label: string
  value: number
  detail?: string
  /** Makes the tile a link to the list behind the number. */
  to?: string
  tone?: 'default' | 'warning'
}

/** A single headline number (stat tile). */
export function StatCard({ label, value, detail, to, tone = 'default' }: StatCardProps) {
  const content = (
    <>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value.toLocaleString()}</span>
      {detail && <span className={`stat-detail ${tone === 'warning' ? 'text-warning' : ''}`}>{detail}</span>}
    </>
  )
  return to ? (
    <Link to={to} className="card stat-card stat-card-link">
      {content}
    </Link>
  ) : (
    <div className="card stat-card">{content}</div>
  )
}
