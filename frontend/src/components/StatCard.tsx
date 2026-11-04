import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { Icon, type IconName } from './Icon'

interface StatCardProps {
  label: string
  value: number
  detail?: string
  /** Makes the tile a link to the list behind the number. */
  to?: string
  tone?: 'default' | 'warning'
  icon?: IconName
  /** Colour of the icon tile; any CSS colour. */
  color?: string
}

/** A single headline number (stat tile). */
export function StatCard({ label, value, detail, to, tone = 'default', icon, color }: StatCardProps) {
  const content = (
    <>
      <span className="stat-head">
        <span className="stat-label">{label}</span>
        {icon && (
          <span className="stat-icon" style={color ? ({ '--stat-color': color } as CSSProperties) : undefined}>
            <Icon name={icon} size={16} />
          </span>
        )}
      </span>
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
