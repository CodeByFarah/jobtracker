import { useState } from 'react'
import { Modal } from './Modal'

interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  /** Red confirm button for deletions; neutral for other irreversible-but-positive actions. */
  destructive?: boolean
  onConfirm: () => Promise<void>
  onClose: () => void
}

/** Confirmation step for destructive actions. Shows the server's message if the action fails. */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Delete',
  destructive = true,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
      setBusy(false)
    }
  }

  return (
    <Modal
      title={title}
      onClose={onClose}
      size="small"
      footer={
        <>
          <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className={`button ${destructive ? 'button-danger' : 'button-primary'}`}
            onClick={confirm}
            disabled={busy}
          >
            {busy ? 'Working…' : confirmLabel}
          </button>
        </>
      }
    >
      <p className="confirm-message">{message}</p>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </Modal>
  )
}
