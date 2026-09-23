import { Link } from 'react-router-dom'
import type { Dashboard } from '../../types/api'
import { pluralize, STATUS_LABELS } from '../../utils/format'
import { ChartCard } from './ChartCard'

type Breakdown = Dashboard['statusBreakdown']

/**
 * Applications by status as a horizontal bar chart: one series, so one hue and no legend.
 * Each bar's value sits at its tip; hovering/focusing a row adds the share of the total.
 * Rows link to the application list filtered by that status.
 */
export function StatusBreakdownChart({ breakdown }: { breakdown: Breakdown }) {
  const total = breakdown.reduce((sum, s) => sum + s.count, 0)
  const max = Math.max(1, ...breakdown.map((s) => s.count))

  const chart = (
    <div className="hbar-chart" role="list">
      {breakdown.map(({ status, count }) => {
        const share = total === 0 ? 0 : Math.round((count / total) * 100)
        return (
          <Link
            key={status}
            to={`/applications?status=${status}`}
            className="hbar-row"
            role="listitem"
            aria-label={`${STATUS_LABELS[status]}: ${pluralize(count, 'application')}, ${share}% of total`}
          >
            <span className="hbar-label">{STATUS_LABELS[status]}</span>
            <span className="hbar-track">
              {count > 0 && <span className="hbar-bar" style={{ width: `${(count / max) * 100}%` }} />}
              <span className="hbar-value">{count}</span>
              <span className="viz-tip" role="tooltip">
                <strong>{STATUS_LABELS[status]}</strong> {pluralize(count, 'application')} · {share}%
              </span>
            </span>
          </Link>
        )
      })}
    </div>
  )

  const table = (
    <table className="data-table">
      <thead>
        <tr>
          <th scope="col">Status</th>
          <th scope="col" className="num">Applications</th>
          <th scope="col" className="num">Share</th>
        </tr>
      </thead>
      <tbody>
        {breakdown.map(({ status, count }) => (
          <tr key={status}>
            <th scope="row">{STATUS_LABELS[status]}</th>
            <td className="num">{count}</td>
            <td className="num">{total === 0 ? '—' : `${Math.round((count / total) * 100)}%`}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row">Total</th>
          <td className="num">{total}</td>
          <td />
        </tr>
      </tfoot>
    </table>
  )

  return <ChartCard title="Applications by status" subtitle="Where every application stands today" chart={chart} table={table} />
}
