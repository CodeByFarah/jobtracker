import { useState, type ReactNode } from 'react'

interface ChartCardProps {
  title: string
  subtitle?: string
  chart: ReactNode
  table: ReactNode
}

/** Card wrapper giving every chart an equivalent table view (for screen readers, exact values, print). */
export function ChartCard({ title, subtitle, chart, table }: ChartCardProps) {
  const [view, setView] = useState<'chart' | 'table'>('chart')
  return (
    <section className="card chart-card">
      <header className="chart-card-header">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="muted">{subtitle}</p>}
        </div>
        <div className="segmented" role="group" aria-label={`${title} view`}>
          <button type="button" aria-pressed={view === 'chart'} onClick={() => setView('chart')}>
            Chart
          </button>
          <button type="button" aria-pressed={view === 'table'} onClick={() => setView('table')}>
            Table
          </button>
        </div>
      </header>
      {view === 'chart' ? chart : table}
    </section>
  )
}
