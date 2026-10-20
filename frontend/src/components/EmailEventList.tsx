import { Link } from 'react-router-dom'
import type { EmailEvent } from '../types/api'
import { EMAIL_CATEGORY_LABELS, EMAIL_CATEGORY_TINT, formatDateTime, STATUS_LABELS } from '../utils/format'

/** Senders look like `"Acme Recruiting" <jobs@acme.com>`; show just the name when there is one. */
function senderName(sender: string | null): string | null {
  if (!sender) return null
  const name = sender.replace(/<[^>]*>/, '').replace(/"/g, '').trim()
  return name || sender
}

/** Emails the AI read, with what it understood and any status change it made. */
export function EmailEventList({ events, showApplication = false }: { events: EmailEvent[]; showApplication?: boolean }) {
  return (
    <ul className="email-list">
      {events.map((event) => (
        <li key={event.id} className="email-item">
          <div className="email-item-top">
            <span className={`badge badge-status-${EMAIL_CATEGORY_TINT[event.category].toLowerCase()}`}>
              {EMAIL_CATEGORY_LABELS[event.category]}
            </span>
            {showApplication && event.applicationId !== null && (
              <Link to={`/applications/${event.applicationId}`} className="email-item-app">
                {event.jobTitle} · {event.companyName}
              </Link>
            )}
            <time className="email-item-date" dateTime={event.receivedAt}>
              {formatDateTime(event.receivedAt)}
            </time>
          </div>
          {event.summary && <p className="email-item-summary">{event.summary}</p>}
          {event.statusAfter && (
            <p className="email-item-change">
              {event.statusBefore
                ? `Status changed: ${STATUS_LABELS[event.statusBefore]} → ${STATUS_LABELS[event.statusAfter]}`
                : `Added to your applications as ${STATUS_LABELS[event.statusAfter]}`}
            </p>
          )}
          <p className="email-item-meta">
            {[senderName(event.sender), event.subject && `“${event.subject}”`].filter(Boolean).join(' · ')}
          </p>
        </li>
      ))}
    </ul>
  )
}
