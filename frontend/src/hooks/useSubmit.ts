import { useCallback, useState } from 'react'
import { ApiError } from '../services/http'

/**
 * Tracks a form submission: disables the form while it runs and exposes the server's
 * error message plus per-field validation errors so they can be shown next to inputs.
 */
export function useSubmit() {
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const run = useCallback(async (action: () => Promise<void>) => {
    setSubmitting(true)
    setFormError(null)
    setFieldErrors({})
    try {
      await action()
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        setFormError(Object.keys(err.fieldErrors).length > 0 ? 'Please fix the highlighted fields.' : err.message)
      } else {
        setFormError(err instanceof Error ? err.message : 'Something went wrong')
      }
    } finally {
      setSubmitting(false)
    }
  }, [])

  return { submitting, formError, fieldErrors, setFieldErrors, run }
}
