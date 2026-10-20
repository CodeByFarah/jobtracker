import { useEffect, useState } from 'react'
import { Navigate, useSearchParams } from 'react-router-dom'
import { Icon } from '../components/Icon'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { reportConnectResult, takeRedirectFlag } from '../services/gmailConnect'

/**
 * Where Google sign-in ends (the backend callback redirects here). Inside the popup it reports the
 * result to the JobTrack tab and closes itself. If the popup was blocked and the whole tab went to
 * Google, it forwards to the profile page, which shows the result.
 */
export function GmailDonePage() {
  useDocumentTitle('Gmail')
  const [params] = useSearchParams()
  const ok = params.get('gmail') === 'connected'
  const reason = params.get('reason') || 'Gmail wasn’t connected.'
  const [wasRedirect] = useState(takeRedirectFlag)

  useEffect(() => {
    if (wasRedirect) return
    reportConnectResult(ok ? { ok: true } : { ok: false, reason })
    if (ok) {
      const timer = setTimeout(() => window.close(), 600)
      return () => clearTimeout(timer)
    }
  }, [ok, reason, wasRedirect])

  if (wasRedirect) {
    const next = new URLSearchParams({ gmail: ok ? 'connected' : 'error' })
    if (!ok) next.set('reason', reason)
    return <Navigate to={`/profile?${next}`} replace />
  }

  return (
    <main className="popup-done">
      <span className={`popup-done-icon ${ok ? '' : 'popup-done-icon-error'}`} aria-hidden="true">
        <Icon name={ok ? 'check' : 'alert'} size={28} />
      </span>
      <h1>{ok ? 'Gmail connected' : 'Gmail wasn’t connected'}</h1>
      <p className="muted">{ok ? 'You can close this window. JobTrack is checking your inbox.' : reason}</p>
      <button type="button" className="button button-primary" onClick={() => window.close()}>
        Close window
      </button>
    </main>
  )
}
