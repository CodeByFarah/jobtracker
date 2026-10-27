import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useGmailConnect } from '../hooks/useGmailConnect'
import { gmailApi } from '../services/api'
import type { GmailStatus, UpdateReport } from '../types/api'
import { formatInstantDate, pluralize, STATUS_LABELS } from '../utils/format'
import { CompanyLogo } from './CompanyLogo'
import { Icon } from './Icon'
import { Modal } from './Modal'
import { StatusBadge } from './StatusBadge'

const POLL_MS = 2500
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * "Check for Updates": scans Gmail with the local AI, updates every matching application, then
 * shows an AI-written summary, every status change and where each application stands.
 */
export function CheckUpdatesButton({
  onUpdated,
  className = 'button button-primary',
}: {
  /** Called when the dialog closes, so the page can reload its data. */
  onUpdated?: () => void
  className?: string
}) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" className={className} onClick={() => setOpen(true)}>
        <Icon name="refresh" /> Check for updates
      </button>
      {open && (
        <UpdatesDialog
          onClose={() => {
            setOpen(false)
            onUpdated?.()
          }}
        />
      )}
    </>
  )
}

type Phase =
  | { kind: 'starting' }
  | { kind: 'notConfigured' }
  | { kind: 'connect' }
  | { kind: 'checking'; status: GmailStatus }
  | { kind: 'done'; report: UpdateReport }
  | { kind: 'error'; message: string }

/**
 * The whole flow in one dialog: connect Gmail if needed (Google sign-in in a popup), then scan
 * the inbox with live progress, then the report. Connecting moves straight on to the first check.
 */
export function UpdatesDialog({ onClose }: { onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>({ kind: 'starting' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    async function run() {
      const status = await gmailApi.status()
      if (cancelled) return
      if (!status.configured) return setPhase({ kind: 'notConfigured' })
      if (!status.connected) return setPhase({ kind: 'connect' })

      let current = await gmailApi.sync()
      while (!cancelled) {
        setPhase({ kind: 'checking', status: current })
        if (!current.syncing) break
        await sleep(POLL_MS)
        current = await gmailApi.status()
      }
      if (cancelled) return
      setPhase({ kind: 'done', report: await gmailApi.report() })
    }
    run().catch((err: unknown) => {
      if (!cancelled) setPhase({ kind: 'error', message: err instanceof Error ? err.message : 'Something went wrong' })
    })
    return () => {
      cancelled = true
    }
  }, [attempt])

  const busy = phase.kind === 'starting' || phase.kind === 'checking'
  return (
    <Modal
      title={phase.kind === 'connect' ? 'Connect your Gmail' : 'Check for updates'}
      onClose={onClose}
      footer={
        <button type="button" className={`button ${busy ? 'button-secondary' : 'button-primary'}`} onClick={onClose}>
          {busy ? 'Continue in background' : phase.kind === 'connect' ? 'Not now' : 'Done'}
        </button>
      }
    >
      {phase.kind === 'starting' && <Working title="Connecting to Gmail…" />}
      {phase.kind === 'checking' && <Progress status={phase.status} />}
      {phase.kind === 'connect' && (
        <ConnectPrompt
          onConnected={() => {
            setPhase({ kind: 'starting' })
            setAttempt((n) => n + 1)
          }}
        />
      )}
      {phase.kind === 'notConfigured' && (
        <p className="notice">
          Connecting Gmail isn’t available right now. Please try again later.
        </p>
      )}
      {phase.kind === 'error' && (
        <p className="notice notice-error" role="alert">
          {phase.message}
        </p>
      )}
      {phase.kind === 'done' && <Report report={phase.report} onNavigate={onClose} />}
    </Modal>
  )
}

function Working({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="updates-working" role="status" aria-live="polite">
      <span className="spinner spinner-large" aria-hidden="true" />
      <p className="updates-working-title">{title}</p>
      {detail && <p className="muted">{detail}</p>}
    </div>
  )
}

function Progress({ status }: { status: GmailStatus }) {
  const { progressDone: done, progressTotal: total } = status
  const percent = total > 0 ? Math.round((done / total) * 100) : 0
  return (
    <div className="updates-working" role="status" aria-live="polite">
      <span className="spinner spinner-large" aria-hidden="true" />
      <p className="updates-working-title">{total > 0 ? 'Reading your job emails…' : 'Looking for new emails…'}</p>
      {total > 0 && (
        <>
          <div className="progress" aria-hidden="true">
            <span style={{ width: `${percent}%` }} />
          </div>
          <p className="muted">
            {done} of {pluralize(total, 'new email')}
          </p>
        </>
      )}
      <p className="muted updates-working-note">
        The AI only reads emails that mention a company you applied to. This can take a few minutes; you can
        close this window and the check keeps running.
      </p>
    </div>
  )
}

function ConnectPrompt({ onConnected }: { onConnected: () => void }) {
  const { state, connect, cancel } = useGmailConnect(onConnected)
  if (state.kind === 'waiting') {
    return (
      <div className="updates-working" role="status" aria-live="polite">
        <span className="spinner spinner-large" aria-hidden="true" />
        <p className="updates-working-title">Waiting for Google…</p>
        <p className="muted">
          Choose your account and allow read-only access in the Google window. It closes by itself when you’re done.
        </p>
        <div className="connect-waiting-actions">
          <button type="button" className="button button-secondary" onClick={connect}>
            Open the Google window again
          </button>
          <button type="button" className="button button-ghost" onClick={cancel}>
            Cancel
          </button>
        </div>
      </div>
    )
  }
  return (
    <div className="connect-prompt">
      <span className="connect-icon" aria-hidden="true">
        <Icon name="mail" size={28} />
      </span>
      <p className="connect-lead">
        Connect Gmail and JobTrack keeps every application up to date for you: confirmations, interview invites, offers
        and rejections are read by AI and applied automatically.
      </p>
      <ul className="connect-points">
        <li>Read-only access. JobTrack can never send, delete or change email.</li>
        <li>Only job-related emails are read, and only from the Gmail account you connect.</li>
        <li>Disconnect any time from your profile.</li>
      </ul>
      {state.kind === 'error' && (
        <p className="notice notice-error" role="alert">
          {state.message}
        </p>
      )}
      <button type="button" className="button button-primary button-large" onClick={connect}>
        <Icon name="mail" /> {state.kind === 'error' ? 'Try again' : 'Connect Gmail'}
      </button>
    </div>
  )
}

function Report({ report, onNavigate }: { report: UpdateReport; onNavigate: () => void }) {
  const check = report.lastCheck
  if (check?.error) {
    return (
      <p className="notice notice-error" role="alert">
        The check stopped: {check.error}
      </p>
    )
  }
  const open = report.applications.filter((a) => !['ACCEPTED', 'REJECTED', 'WITHDRAWN'].includes(a.status))
  return (
    <div className="report">
      {check?.overview && <p className="report-overview">{check.overview}</p>}
      {check && (
        <p className="muted report-stats">
          {pluralize(check.messagesScanned, 'new email')} · {check.emailsAnalyzed} read by AI ·{' '}
          {pluralize(check.statusesUpdated, 'application')} updated
        </p>
      )}

      {check && check.changes.length > 0 && (
        <section>
          <h3 className="report-heading">Updated and added</h3>
          <ul className="report-list">
            {check.changes.map((change) => (
              <li key={`${change.applicationId}-${change.to}`} className="report-item">
                <div className="report-item-main">
                  <Link to={`/applications/${change.applicationId}`} onClick={onNavigate} className="report-item-title">
                    {change.companyName} · {change.jobTitle}
                  </Link>
                  {change.emailSummary && <p className="report-item-text">{change.emailSummary}</p>}
                </div>
                <span className="report-change">
                  {change.from ? (
                    <>
                      <span className="muted">{STATUS_LABELS[change.from]}</span>
                      <span aria-label="changed to">→</span>
                    </>
                  ) : (
                    <span className="badge badge-new">New</span>
                  )}
                  <StatusBadge status={change.to} />
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h3 className="report-heading">
          All applications <span className="muted">({open.length} open)</span>
        </h3>
        {report.applications.length === 0 ? (
          <p className="muted">You haven’t added any applications yet.</p>
        ) : (
          <ul className="report-list">
            {report.applications.map((app) => (
              <li key={app.id} className={`report-item ${app.changedInLastCheck ? 'report-item-new' : ''}`}>
                <CompanyLogo
                  companyId={app.companyId}
                  name={app.companyName}
                  logoUpdatedAt={app.companyLogoUpdatedAt}
                  size="small"
                />
                <div className="report-item-main">
                  <Link to={`/applications/${app.id}`} onClick={onNavigate} className="report-item-title">
                    {app.companyName} · {app.jobTitle}
                  </Link>
                  <p className="report-item-text">
                    {app.lastEmailSummary
                      ? `${app.lastEmailSummary} (${formatInstantDate(app.lastEmailAt)})`
                      : app.noReply
                        ? 'No reply yet for over two weeks.'
                        : 'No emails about this application yet.'}
                  </p>
                </div>
                <span className="report-badges">
                  <StatusBadge status={app.status} />
                  {app.noReply && <span className="badge badge-noreply">No reply</span>}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
