import { useState } from 'react'
import { useAsync } from '../hooks/useAsync'
import { gmailApi } from '../services/api'
import { ConnectPrompt } from './CheckUpdates'
import { Icon } from './Icon'
import { Modal } from './Modal'

/**
 * Banner asking the user to connect Gmail, shown until they have. Hidden while loading and once
 * Gmail is connected, so it never flashes for connected users.
 */
export function GmailPrompt() {
  const status = useAsync(() => gmailApi.status(), [])
  const [open, setOpen] = useState(false)
  if (!status.data || status.data.connected) return null

  return (
    <section className="gmail-banner" aria-label="Connect Gmail">
      <span className="gmail-banner-icon" aria-hidden="true">
        <Icon name="mail" size={22} />
      </span>
      <div className="gmail-banner-text">
        <h2>Let your inbox update JobTrack</h2>
        <p className="muted">
          Connect Gmail and your local AI tracks replies, interviews and rejections, and keeps every application current.
        </p>
      </div>
      <button type="button" className="button button-primary" onClick={() => setOpen(true)}>
        Connect Gmail
      </button>
      {open && (
        <Modal title="Connect your Gmail" onClose={() => setOpen(false)}>
          {status.data.configured ? (
            <ConnectPrompt />
          ) : (
            <p className="notice">
              Gmail isn’t set up on this server yet. Add <code>GOOGLE_CLIENT_ID</code> and{' '}
              <code>GOOGLE_CLIENT_SECRET</code> to the <code>.env</code> file and restart the backend (see the README).
            </p>
          )}
        </Modal>
      )}
    </section>
  )
}
