import { useState } from 'react'
import { useAsync } from '../hooks/useAsync'
import { gmailApi } from '../services/api'
import { UpdatesDialog } from './CheckUpdates'
import { Icon } from './Icon'

/**
 * Banner asking the user to connect Gmail, shown until they have. Hidden while loading and once
 * Gmail is connected, so it never flashes for connected users. Connecting runs the first check
 * in the same dialog, and `onUpdated` lets the page reload once it closes.
 */
export function GmailPrompt({ onUpdated }: { onUpdated?: () => void }) {
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
        <UpdatesDialog
          onClose={() => {
            setOpen(false)
            status.reload()
            onUpdated?.()
          }}
        />
      )}
    </section>
  )
}
