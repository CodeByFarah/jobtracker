import type { ApplicationStatus, InterviewStatus } from '../types/api'
import { INTERVIEW_STATUS_LABELS, STATUS_LABELS } from '../utils/format'

/** Application status pill. Colour is paired with the text label, never used alone. */
export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge badge-status-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
}

export function InterviewStatusBadge({ status }: { status: InterviewStatus }) {
  return (
    <span className={`badge badge-interview-${status.toLowerCase()}`}>{INTERVIEW_STATUS_LABELS[status]}</span>
  )
}
