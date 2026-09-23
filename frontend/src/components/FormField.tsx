import { cloneElement, isValidElement, useId, type ReactElement } from 'react'

interface FormFieldProps {
  label: string
  error?: string
  hint?: string
  required?: boolean
  className?: string
  children: ReactElement<{ id?: string; 'aria-invalid'?: boolean; 'aria-describedby'?: string }>
}

/** Label + control + hint/error, wiring up ids and aria attributes on the child control. */
export function FormField({ label, error, hint, required, className, children }: FormFieldProps) {
  const id = useId()
  const messageId = `${id}-message`
  const control = isValidElement(children)
    ? cloneElement(children, {
        id,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': error || hint ? messageId : undefined,
      })
    : children

  return (
    <div className={`field ${error ? 'field-invalid' : ''} ${className ?? ''}`}>
      <label htmlFor={id}>
        {label}
        {required && <span className="required" aria-hidden="true"> *</span>}
      </label>
      {control}
      {error ? (
        <p id={messageId} className="field-error">
          {error}
        </p>
      ) : (
        hint && (
          <p id={messageId} className="field-hint">
            {hint}
          </p>
        )
      )}
    </div>
  )
}
