import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state state-loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: Error; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <Icon name="alert" size={22} />
      <div>
        <p className="state-title">Couldn’t load this</p>
        <p className="state-text">{error.message}</p>
      </div>
      {onRetry && (
        <button type="button" className="button button-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({
  icon,
  title,
  text,
  action,
}: {
  icon: IconName
  title: string
  text?: string
  action?: ReactNode
}) {
  return (
    <div className="state state-empty">
      <span className="state-icon">
        <Icon name={icon} size={22} />
      </span>
      <p className="state-title">{title}</p>
      {text && <p className="state-text">{text}</p>}
      {action}
    </div>
  )
}
