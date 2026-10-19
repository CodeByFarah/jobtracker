import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAsync } from '../hooks/useAsync'
import { gmailApi } from '../services/api'
import { pluralize, timeAgo } from '../utils/format'
import { ConfirmDialog } from './ConfirmDialog'
import { EmailEventList } from './EmailEventList'
import { Icon } from './Icon'
import { ErrorState, LoadingState } from './States'

const POLL_MS = 5000

/**
 * Gmail connection + AI email tracking. Connecting sends the browser to Google; Google sends it
 * back to /profile?gmail=connected (or ?gmail=error&reason=...), which this card reports.
 */
export function GmailCard() {
  const status = useAsync(() => gmailApi.status(), [])
  const events = useAsync(() => gmailApi.events(10), [])
  const [params, setParams] = useSearchParams()
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [confirmDisconnect, setConfirmDisconnect] = useState(false)
  const [flash] = useState(() => ({ result: params.get('gmail'), reason: params.get('reason') }))
  const wasSyncing = useRef(false)

  // Drop ?gmail=... from the URL so a reload doesn't show the message again.
  useEffect(() => {
    if (params.has('gmail')) {
      const next = new URLSearchParams(params)
      next.delete('gmail')
      next.delete('reason')
      setParams(next, { replace: true })
    }
  }, [params, setParams])

  // While a sync runs, poll its status; when it finishes, refresh the activity list.
  const syncing = status.data?.syncing ?? false
  const { reload: reloadStatus } = status
  const { reload: reloadEvents } = events
  useEffect(() => {
    if (syncing) {
      wasSyncing.current = true
      const timer = setInterval(reloadStatus, POLL_MS)
      return () => clearInterval(timer)
    }
    if (wasSyncing.current) {
      wasSyncing.current = false
      reloadEvents()
    }
  }, [syncing, reloadStatus, reloadEvents])

  async function act(action: () => Promise<void>) {
    setBusy(true)
    setActionError(null)
    try {
      await action()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  const connect = () =>
    act(async () => {
      const { url } = await gmailApi.connectUrl()
      window.location.assign(url)
    })
  const syncNow = () =>
    act(async () => {
      const next = await gmailApi.sync()
      status.setData(() => next)
    })

  const data = status.data
  return (
    <section className="card narrow-card" aria-labelledby="gmail-heading">
      <header className="section-header">
        <h2 id="gmail-heading">
          <Icon name="mail" /> Gmail tracking
        </h2>
      </header>
      <p className="muted gmail-intro">
        Your local AI reads job-related emails and updates your applications: interview invites, rejections,
        offers and confirmations. Access is read-only and nothing leaves this computer except the requests to Gmail.
      </p>

      {flash.result === 'connected' && (
        <p className="notice" role="status">
          Gmail connected. The first check is running and can take a few minutes on a laptop.
        </p>
      )}
      {flash.result === 'error' && (
        <p className="notice notice-error" role="alert">
          Couldn’t connect Gmail. {flash.reason}
        </p>
      )}
      {actionError && (
        <p className="notice notice-error" role="alert">
          {actionError}
        </p>
      )}

      {status.error && <ErrorState error={status.error} onRetry={status.reload} />}
      {!status.error && !data && <LoadingState />}

      {data && !data.configured && (
        <p className="notice">
          Gmail isn’t set up on this server yet. Add <code>GOOGLE_CLIENT_ID</code> and <code>GOOGLE_CLIENT_SECRET</code>{' '}
          to the <code>.env</code> file and restart the backend (see the README).
        </p>
      )}

      {data && data.configured && !data.connected && (
        <div className="gmail-actions">
          <button type="button" className="button button-primary" onClick={connect} disabled={busy}>
            <Icon name="mail" /> {busy ? 'Opening Google…' : 'Connect Gmail'}
          </button>
        </div>
      )}

      {data && data.connected && (
        <>
          <div className="gmail-status">
            <span className="gmail-dot" aria-hidden="true" />
            <span>
              Connected as <strong>{data.gmailAddress}</strong>
            </span>
          </div>
          <p className="muted">
            {data.syncing
              ? 'Checking your inbox now…'
              : data.lastSyncedAt
                ? `Last checked ${timeAgo(data.lastSyncedAt)}. Checks again automatically every 30 minutes.`
                : 'Not checked yet.'}
          </p>
          {data.lastRun && !data.syncing && !data.lastRun.error && (
            <p className="muted">
              Last check: {pluralize(data.lastRun.messagesScanned, 'new email')}, {data.lastRun.emailsAnalyzed} read by
              the AI, {pluralize(data.lastRun.statusesUpdated, 'application')} updated.
            </p>
          )}
          {data.lastSyncError && (
            <p className="notice notice-error" role="alert">
              {data.lastSyncError}
            </p>
          )}
          <div className="gmail-actions">
            <button type="button" className="button button-secondary" onClick={syncNow} disabled={busy || data.syncing}>
              <Icon name="refresh" /> {data.syncing ? 'Checking…' : 'Check now'}
            </button>
            {data.lastSyncError && (
              <button type="button" className="button button-secondary" onClick={connect} disabled={busy}>
                Reconnect
              </button>
            )}
            <button type="button" className="button button-ghost-danger" onClick={() => setConfirmDisconnect(true)} disabled={busy}>
              Disconnect
            </button>
          </div>

          <h3 className="gmail-activity-heading">Recent activity</h3>
          {events.error && <ErrorState error={events.error} onRetry={events.reload} />}
          {events.data?.length === 0 && (
            <p className="muted empty-inline">
              No job emails matched yet. Emails are matched by the company names of your open applications.
            </p>
          )}
          {events.data && events.data.length > 0 && <EmailEventList events={events.data} showApplication />}
        </>
      )}

      {confirmDisconnect && (
        <ConfirmDialog
          title="Disconnect Gmail?"
          message="JobTrack will stop checking your inbox and Google access will be revoked. Emails already matched stay on your applications."
          confirmLabel="Disconnect"
          destructive
          onConfirm={async () => {
            await gmailApi.disconnect()
            status.reload()
          }}
          onClose={() => setConfirmDisconnect(false)}
        />
      )}
    </section>
  )
}
