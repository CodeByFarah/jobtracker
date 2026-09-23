import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { InterviewFormModal } from '../components/forms/InterviewFormModal'
import { InterviewCard } from '../components/InterviewCard'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { useAsync } from '../hooks/useAsync'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { interviewApi } from '../services/api'
import type { Interview } from '../types/api'

type Scope = 'UPCOMING' | 'PAST' | 'ALL'
const TABS: { value: Scope; label: string }[] = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'PAST', label: 'Past & cancelled' },
  { value: 'ALL', label: 'All' },
]

export function InterviewsPage() {
  useDocumentTitle('Interviews')
  const [scope, setScope] = useState<Scope>('UPCOMING')
  const interviews = useAsync(() => interviewApi.list(scope), [scope])
  const [editing, setEditing] = useState<Interview | null>(null)
  const [deleting, setDeleting] = useState<Interview | null>(null)

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Interviews</h1>
          <p className="muted">Schedule new interviews from an application’s page.</p>
        </div>
      </header>

      <div className="tabs" role="tablist" aria-label="Interview filter">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={scope === tab.value}
            className={scope === tab.value ? 'tab active' : 'tab'}
            onClick={() => setScope(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {interviews.error && <ErrorState error={interviews.error} onRetry={interviews.reload} />}
      {!interviews.error && !interviews.data && <LoadingState />}
      {interviews.data?.length === 0 && (
        <EmptyState
          icon="calendar"
          title={scope === 'UPCOMING' ? 'No upcoming interviews' : 'No interviews here yet'}
          text="Open an application and choose “Schedule” to add an interview."
          action={
            <Link to="/applications" className="button button-secondary">
              Go to applications
            </Link>
          }
        />
      )}
      {interviews.data && interviews.data.length > 0 && (
        <div className={`card stack ${interviews.loading ? 'is-refreshing' : ''}`}>
          {interviews.data.map((interview) => (
            <InterviewCard
              key={interview.id}
              interview={interview}
              onEdit={() => setEditing(interview)}
              onDelete={() => setDeleting(interview)}
            />
          ))}
        </div>
      )}

      {editing && (
        <InterviewFormModal
          interview={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            interviews.reload()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete interview?"
          message={`The interview for ${deleting.jobTitle} at ${deleting.companyName} will be removed permanently.`}
          onConfirm={async () => {
            await interviewApi.remove(deleting.id)
            interviews.reload()
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </>
  )
}
