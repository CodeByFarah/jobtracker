import type { Application, ApplicationStatus, InterviewStatus } from '../types/api'
import { INTERVIEW_STATUS_LABELS, isAwaitingReply, NO_REPLY_DAYS, STATUS_LABELS } from '../utils/format'

/** Application status pill. Colour is paired with the text label, never used alone. */
export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge badge-status-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
}

/** Shown next to the status when an open application hasn't heard back for a while. */
export function NoReplyBadge({ application }: { application: Application }) {
  if (!isAwaitingReply(application)) return null
  return (
    <span className="badge badge-noreply" title={`No news for more than ${NO_REPLY_DAYS} days`}>
      No reply
    </span>
  )
}

export function InterviewStatusBadge({ status }: { status: InterviewStatus }) {
  return (
    <span className={`badge badge-interview-${status.toLowerCase()}`}>{INTERVIEW_STATUS_LABELS[status]}</span>
  )
}
