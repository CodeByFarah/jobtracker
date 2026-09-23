import type { Dashboard } from '../../types/api'
import { formatMonth, pluralize } from '../../utils/format'
import { ChartCard } from './ChartCard'

type Series = Dashboard['applicationsPerMonth']

/** Rounds the axis maximum up to a clean number (1, 2, 5 × 10^n) so ticks read naturally. */
function niceMax(value: number): number {
  if (value <= 4) return 4
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const step = [1, 2, 5, 10].find((m) => m * magnitude >= value)!
  return step * magnitude
}

/**
 * Applications per month (by application date) as a column chart: a single series in one
 * hue, recessive gridlines, and direct labels only on the latest month and the peak.
 */
export function ApplicationsOverTimeChart({ series }: { series: Series }) {
  const peak = Math.max(0, ...series.map((m) => m.count))
  const axisMax = niceMax(peak)
  const ticks = [axisMax, axisMax / 2, 0]
  const lastIndex = series.length - 1
  const peakIndex = series.findIndex((m) => m.count === peak)

  const chart = (
    <div className="col-chart">
      <div className="col-chart-plot">
        <div className="col-chart-grid" aria-hidden="true">
          {ticks.map((t) => (
            <div key={t} className="col-chart-gridline">
              <span>{Number.isInteger(t) ? t : t.toFixed(1)}</span>
            </div>
          ))}
        </div>
        <div className="col-chart-columns" role="list">
          {series.map((m, i) => {
            const labelled = m.count > 0 && (i === lastIndex || i === peakIndex)
            return (
              <div
                key={m.month}
                className="col-chart-slot"
                role="listitem"
                tabIndex={0}
                aria-label={`${formatMonth(m.month, 'long')}: ${pluralize(m.count, 'application')}`}
              >
                <div className="col-chart-bar-area">
                  {m.count > 0 && (
                    <div className="col-chart-bar" style={{ height: `${(m.count / axisMax) * 100}%` }}>
                      {labelled && <span className="col-chart-value">{m.count}</span>}
                    </div>
                  )}
                  <span className="viz-tip" role="tooltip">
                    <strong>{formatMonth(m.month, 'long')}</strong> {pluralize(m.count, 'application')}
                  </span>
                </div>
                <span className="col-chart-label">{formatMonth(m.month)}</span>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )

  const table = (
    <table className="data-table">
      <thead>
        <tr>
          <th scope="col">Month</th>
          <th scope="col" className="num">Applications</th>
        </tr>
      </thead>
      <tbody>
        {series.map((m) => (
          <tr key={m.month}>
            <th scope="row">{formatMonth(m.month, 'long')}</th>
            <td className="num">{m.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )

  return (
    <ChartCard
      title="Applications over time"
      subtitle="Applications submitted per month, last 6 months"
      chart={chart}
      table={table}
    />
  )
}
