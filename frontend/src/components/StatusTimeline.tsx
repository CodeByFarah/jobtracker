import type { StatusHistoryEntry } from '../types/api'
import { formatDateTime, STATUS_LABELS } from '../utils/format'
import { StatusBadge } from './StatusBadge'

/** Chronological status history (oldest first, as returned by the API). */
export function StatusTimeline({ entries }: { entries: StatusHistoryEntry[] }) {
  return (
    <ol className="timeline">
      {entries.map((entry, index) => (
        <li key={entry.id} className={index === entries.length - 1 ? 'timeline-current' : undefined}>
          <span className="timeline-dot" aria-hidden="true" />
          <div className="timeline-content">
            <StatusBadge status={entry.newStatus} />
            <span className="timeline-text">
              {entry.previousStatus
                ? `Moved from ${STATUS_LABELS[entry.previousStatus]}`
                : 'Added to JobTrack'}
            </span>
            <time dateTime={entry.changedAt} className="timeline-time">
              {formatDateTime(entry.changedAt)}
            </time>
          </div>
        </li>
      ))}
    </ol>
  )
}
