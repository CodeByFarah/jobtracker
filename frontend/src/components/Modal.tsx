import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** Footer content, typically the form's action buttons. */
  footer?: ReactNode
  size?: 'default' | 'small'
}

/** Accessible dialog: closes on Escape or backdrop click, moves focus inside, restores it on close. */
export function Modal({ title, onClose, children, footer, size = 'default' }: ModalProps) {
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null
    const firstField = dialogRef.current?.querySelector<HTMLElement>('input, select, textarea, button:not(.modal-close)')
    ;(firstField ?? dialogRef.current)?.focus()

    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    document.body.classList.add('modal-open')
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.classList.remove('modal-open')
      previouslyFocused?.focus()
    }
  }, [onClose])

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={dialogRef}
        className={`modal modal-${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <header className="modal-header">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="icon-button modal-close" onClick={onClose} aria-label="Close">
            <Icon name="close" />
          </button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-footer">{footer}</footer>}
      </div>
    </div>
  )
}
