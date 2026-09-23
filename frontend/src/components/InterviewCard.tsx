import { Link } from 'react-router-dom'
import type { Interview } from '../types/api'
import { INTERVIEW_TYPE_LABELS } from '../utils/format'
import { Icon } from './Icon'
import { InterviewStatusBadge } from './StatusBadge'

interface InterviewCardProps {
  interview: Interview
  /** Show which application the interview belongs to (off on the application page itself). */
  showApplication?: boolean
  onEdit?: () => void
  onDelete?: () => void
}

export function InterviewCard({ interview, showApplication = true, onEdit, onDelete }: InterviewCardProps) {
  const when = new Date(interview.scheduledAt)
  const time = when.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  return (
    <div className="interview-card">
      <div className="date-chip" aria-hidden="true">
        <span className="date-chip-month">{when.toLocaleDateString(undefined, { month: 'short' })}</span>
        <span className="date-chip-day">{when.getDate()}</span>
      </div>
      <div className="interview-card-body">
        <div className="interview-card-title">
          <strong>{INTERVIEW_TYPE_LABELS[interview.type]} interview</strong>
          <InterviewStatusBadge status={interview.status} />
        </div>
        {showApplication && (
          <Link to={`/applications/${interview.applicationId}`} className="interview-card-app">
            {interview.jobTitle} · {interview.companyName}
          </Link>
        )}
        <p className="interview-card-meta">
          <span>
            <Icon name="clock" size={14} />{' '}
            {when.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })}, {time}
            {interview.durationMinutes ? ` · ${interview.durationMinutes} min` : ''}
          </span>
          {interview.interviewerName && (
            <span>
              <Icon name="user" size={14} /> {interview.interviewerName}
            </span>
          )}
          {interview.location && (
            <span className="truncate">
              <Icon name="mapPin" size={14} /> {interview.location}
            </span>
          )}
        </p>
        {interview.notes && <p className="interview-card-notes">{interview.notes}</p>}
      </div>
      {(onEdit || onDelete) && (
        <div className="row-actions">
          {onEdit && (
            <button type="button" className="icon-button" onClick={onEdit} aria-label="Edit interview">
              <Icon name="edit" size={16} />
            </button>
          )}
          {onDelete && (
            <button type="button" className="icon-button" onClick={onDelete} aria-label="Delete interview">
              <Icon name="trash" size={16} />
            </button>
          )}
        </div>
      )}
    </div>
  )
}
