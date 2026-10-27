import { useEffect, useState } from 'react'
import { Navigate, useSearchParams } from 'react-router-dom'
import { Icon } from '../components/Icon'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { gmailApi } from '../services/api'
import { reportConnectResult, takeRedirectFlag, type ConnectResult } from '../services/gmailConnect'
import { ApiError } from '../services/http'

/** One request per sign-in, even if the page renders twice (React strict mode). */
const completions = new Map<string, Promise<ConnectResult>>()

function complete(code: string, state: string): Promise<ConnectResult> {
  let pending = completions.get(code)
  if (!pending) {
    pending = gmailApi.complete(code, state).then(
      (): ConnectResult => ({ ok: true }),
      (err: unknown): ConnectResult => ({
        ok: false,
        reason:
          err instanceof ApiError && err.status === 401
            ? 'Log in to JobTrack in this browser, then connect Gmail again.'
            : err instanceof Error
              ? err.message
              : 'Gmail wasn’t connected.',
      }),
    )
    completions.set(code, pending)
  }
  return pending
}

/**
 * Where Google sign-in ends (the backend callback redirects here with Google's code). This page
 * finishes the connection as the logged-in user, so a sign-in can only ever attach a mailbox to
 * the account that started it. Inside the popup it then reports the result to the JobTrack tab
 * and closes itself; if the popup was blocked and the whole tab went to Google, it forwards to the
 * profile page, which shows the result.
 */
export function GmailDonePage() {
  useDocumentTitle('Gmail')
  const [params] = useSearchParams()
  const code = params.get('code')
  const state = params.get('state')
  const [wasRedirect] = useState(takeRedirectFlag)
  const [result, setResult] = useState<ConnectResult | null>(() =>
    code && state ? null : { ok: false, reason: params.get('reason') || 'Gmail wasn’t connected.' },
  )

  useEffect(() => {
    if (!code || !state) return
    let active = true
    complete(code, state).then((value) => {
      if (active) setResult(value)
    })
    return () => {
      active = false
    }
  }, [code, state])

  useEffect(() => {
    if (!result || wasRedirect) return
    reportConnectResult(result)
    if (result.ok) {
      const timer = setTimeout(() => window.close(), 600)
      return () => clearTimeout(timer)
    }
  }, [result, wasRedirect])

  if (!result) {
    return (
      <main className="popup-done">
        <h1>Connecting Gmail…</h1>
        <p className="muted">This only takes a moment.</p>
      </main>
    )
  }

  if (wasRedirect) {
    const next = new URLSearchParams({ gmail: result.ok ? 'connected' : 'error' })
    if (!result.ok) next.set('reason', result.reason)
    return <Navigate to={`/profile?${next}`} replace />
  }

  return (
    <main className="popup-done">
      <span className={`popup-done-icon ${result.ok ? '' : 'popup-done-icon-error'}`} aria-hidden="true">
        <Icon name={result.ok ? 'check' : 'alert'} size={28} />
      </span>
      <h1>{result.ok ? 'Gmail connected' : 'Gmail wasn’t connected'}</h1>
      <p className="muted">{result.ok ? 'You can close this window. JobTrack is checking your inbox.' : result.reason}</p>
      <button type="button" className="button button-primary" onClick={() => window.close()}>
        Close window
      </button>
    </main>
  )
}
