import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { InterviewFormModal } from '../components/forms/InterviewFormModal'
import { TaskFormModal } from '../components/forms/TaskFormModal'
import { Icon } from '../components/Icon'
import { InterviewCard } from '../components/InterviewCard'
import { ErrorState, LoadingState } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { StatusTimeline } from '../components/StatusTimeline'
import { TaskItem } from '../components/TaskItem'
import { useAsync } from '../hooks/useAsync'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { applicationApi, interviewApi, taskApi } from '../services/api'
import type { Application, ApplicationStatus, Interview, Task } from '../types/api'
import { EMPLOYMENT_LABELS, formatDate, formatInstantDate, formatSalary, STATUS_LABELS } from '../utils/format'

const FINAL_STATUSES: ApplicationStatus[] = ['ACCEPTED', 'REJECTED', 'WITHDRAWN']

type Dialog =
  | { kind: 'edit' }
  | { kind: 'delete' }
  | { kind: 'status'; status: ApplicationStatus }
  | { kind: 'interview'; interview?: Interview }
  | { kind: 'deleteInterview'; interview: Interview }
  | { kind: 'task'; task?: Task }
  | { kind: 'deleteTask'; task: Task }
  | null

export function ApplicationDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const application = useAsync(() => applicationApi.get(id), [id])
  const history = useAsync(() => applicationApi.history(id), [id])
  const interviews = useAsync(() => applicationApi.interviews(id), [id])
  const tasks = useAsync(() => applicationApi.tasks(id), [id])
  const [dialog, setDialog] = useState<Dialog>(null)
  const [statusError, setStatusError] = useState<string | null>(null)
  const [changingStatus, setChangingStatus] = useState(false)

  const app = application.data
  useDocumentTitle(app ? `${app.jobTitle} at ${app.company.name}` : 'Application')

  if (application.error && !app) {
    return (
      <>
        <BackLink />
        <ErrorState error={application.error} onRetry={application.reload} />
      </>
    )
  }
  if (!app) return <LoadingState />

  async function changeStatus(status: ApplicationStatus) {
    setChangingStatus(true)
    setStatusError(null)
    try {
      const updated = await applicationApi.changeStatus(id, status)
      application.setData(() => updated)
      history.reload()
    } catch (err) {
      setStatusError(err instanceof Error ? err.message : 'Could not change the status')
    } finally {
      setChangingStatus(false)
    }
  }

  function requestStatus(status: ApplicationStatus) {
    // Final statuses cannot be undone, so ask first.
    if (FINAL_STATUSES.includes(status)) setDialog({ kind: 'status', status })
    else void changeStatus(status)
  }

  const replaceTask = (task: Task) => tasks.setData((list) => list?.map((t) => (t.id === task.id ? task : t)))
  const salary = formatSalary(app.salaryMin, app.salaryMax, app.salaryCurrency)

  return (
    <>
      <BackLink />
      <header className="page-header detail-header">
        <div>
          <div className="detail-title">
            <h1>{app.jobTitle}</h1>
            <StatusBadge status={app.status} />
          </div>
          <p className="detail-subtitle">
            <Link to={`/companies/${app.company.id}`}>{app.company.name}</Link>
            {app.location && <span> · {app.location}</span>}
          </p>
        </div>
        <div className="header-actions">
          <button type="button" className="button button-secondary" onClick={() => setDialog({ kind: 'edit' })}>
            <Icon name="edit" /> Edit
          </button>
          <button type="button" className="button button-ghost-danger" onClick={() => setDialog({ kind: 'delete' })}>
            <Icon name="trash" /> Delete
          </button>
        </div>
      </header>

      <section className="card status-panel" aria-labelledby="status-heading">
        <h2 id="status-heading">Change status</h2>
        {app.allowedTransitions.length === 0 ? (
          <p className="muted">
            This application is <strong>{STATUS_LABELS[app.status].toLowerCase()}</strong>, which is a final status.
          </p>
        ) : (
          <div className="status-actions">
            {app.allowedTransitions.map((status) => (
              <button
                key={status}
                type="button"
                className={`button ${FINAL_STATUSES.includes(status) && status !== 'ACCEPTED' ? 'button-secondary' : 'button-outline'}`}
                disabled={changingStatus}
                onClick={() => requestStatus(status)}
              >
                Move to {STATUS_LABELS[status]}
              </button>
            ))}
          </div>
        )}
        {statusError && <p className="form-error" role="alert">{statusError}</p>}
      </section>

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card">
            <h2>Details</h2>
            <dl className="details-list">
              <div>
                <dt>Company</dt>
                <dd><Link to={`/companies/${app.company.id}`}>{app.company.name}</Link></dd>
              </div>
              <div>
                <dt>Date applied</dt>
                <dd>{formatDate(app.applicationDate)}</dd>
              </div>
              <div>
                <dt>Employment type</dt>
                <dd>{app.employmentType ? EMPLOYMENT_LABELS[app.employmentType] : '—'}</dd>
              </div>
              <div>
                <dt>Salary</dt>
                <dd>{salary ?? '—'}</dd>
              </div>
              <div>
                <dt>Location</dt>
                <dd>{app.location ?? '—'}</dd>
              </div>
              <div>
                <dt>Job posting</dt>
                <dd>
                  {app.jobUrl ? (
                    <a href={app.jobUrl} target="_blank" rel="noopener noreferrer" className="external-link">
                      Open posting <Icon name="external" size={14} />
                    </a>
                  ) : (
                    '—'
                  )}
                </dd>
              </div>
              <div>
                <dt>Added</dt>
                <dd>{formatInstantDate(app.createdAt)}</dd>
              </div>
              <div>
                <dt>Last updated</dt>
                <dd>{formatInstantDate(app.updatedAt)}</dd>
              </div>
            </dl>
            <h3 className="notes-heading">Notes</h3>
            {app.notes ? <p className="notes">{app.notes}</p> : <p className="muted">No notes yet.</p>}
          </section>

          <section className="card">
            <header className="section-header">
              <h2>Interviews</h2>
              {!FINAL_STATUSES.includes(app.status) && (
                <button type="button" className="button button-secondary button-small" onClick={() => setDialog({ kind: 'interview' })}>
                  <Icon name="plus" size={16} /> Schedule
                </button>
              )}
            </header>
            {interviews.error && <ErrorState error={interviews.error} onRetry={interviews.reload} />}
            {interviews.data?.length === 0 && <p className="muted empty-inline">No interviews yet.</p>}
            <div className="stack">
              {interviews.data?.map((interview) => (
                <InterviewCard
                  key={interview.id}
                  interview={interview}
                  showApplication={false}
                  onEdit={() => setDialog({ kind: 'interview', interview })}
                  onDelete={() => setDialog({ kind: 'deleteInterview', interview })}
                />
              ))}
            </div>
          </section>

          <section className="card">
            <header className="section-header">
              <h2>Follow-up tasks</h2>
              <button type="button" className="button button-secondary button-small" onClick={() => setDialog({ kind: 'task' })}>
                <Icon name="plus" size={16} /> Add task
              </button>
            </header>
            {tasks.error && <ErrorState error={tasks.error} onRetry={tasks.reload} />}
            {tasks.data?.length === 0 && <p className="muted empty-inline">No follow-ups planned.</p>}
            <div className="stack">
              {tasks.data?.map((task) => (
                <TaskItem
                  key={task.id}
                  task={task}
                  showApplication={false}
                  onChanged={replaceTask}
                  onEdit={() => setDialog({ kind: 'task', task })}
                  onDelete={() => setDialog({ kind: 'deleteTask', task })}
                />
              ))}
            </div>
          </section>
        </div>

        <aside className="card detail-side">
          <h2>Status history</h2>
          {history.error && <ErrorState error={history.error} onRetry={history.reload} />}
          {history.data && <StatusTimeline entries={history.data} />}
        </aside>
      </div>

      {renderDialog()}
    </>
  )

  function renderDialog() {
    if (!dialog || !app) return null
    const close = () => setDialog(null)
    switch (dialog.kind) {
      case 'edit':
        return (
          <ApplicationFormModal
            application={app}
            onClose={close}
            onSaved={(updated: Application) => {
              application.setData(() => updated)
              close()
            }}
          />
        )
      case 'delete':
        return (
          <ConfirmDialog
            title="Delete application?"
            message={`“${app.jobTitle}” at ${app.company.name} will be deleted together with its status history, interviews and tasks. This cannot be undone.`}
            onConfirm={async () => {
              await applicationApi.remove(app.id)
              navigate('/applications', { replace: true })
            }}
            onClose={close}
          />
        )
      case 'status':
        return (
          <ConfirmDialog
            title={`Mark as ${STATUS_LABELS[dialog.status].toLowerCase()}?`}
            message={`${STATUS_LABELS[dialog.status]} is a final status: the application can’t be moved to another status afterwards.`}
            confirmLabel={`Mark as ${STATUS_LABELS[dialog.status].toLowerCase()}`}
            destructive={dialog.status !== 'ACCEPTED'}
            onConfirm={() => changeStatus(dialog.status)}
            onClose={close}
          />
        )
      case 'interview':
        return (
          <InterviewFormModal
            applicationId={app.id}
            interview={dialog.interview}
            onClose={close}
            onSaved={() => {
              interviews.reload()
              close()
            }}
          />
        )
      case 'deleteInterview':
        return (
          <ConfirmDialog
            title="Delete interview?"
            message="This interview will be removed permanently."
            onConfirm={async () => {
              await interviewApi.remove(dialog.interview.id)
              interviews.reload()
            }}
            onClose={close}
          />
        )
      case 'task':
        return (
          <TaskFormModal
            applicationId={app.id}
            task={dialog.task}
            onClose={close}
            onSaved={() => {
              tasks.reload()
              close()
            }}
          />
        )
      case 'deleteTask':
        return (
          <ConfirmDialog
            title="Delete task?"
            message={`“${dialog.task.title}” will be removed permanently.`}
            onConfirm={async () => {
              await taskApi.remove(dialog.task.id)
              tasks.reload()
            }}
            onClose={close}
          />
        )
    }
  }
}

function BackLink() {
  return (
    <Link to="/applications" className="back-link">
      <Icon name="arrowLeft" size={16} /> Applications
    </Link>
  )
}
