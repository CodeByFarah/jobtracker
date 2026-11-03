import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { FormField } from '../components/FormField'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useSubmit } from '../hooks/useSubmit'
import { authApi } from '../services/api'
import { AuthLayout } from './AuthLayout'

/** Asks for the account email and sends a one-time reset link to it. */
export function ForgotPasswordPage() {
  useDocumentTitle('Forgot password')
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const { submitting, formError, fieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      await authApi.forgotPassword(email.trim())
      setSent(true)
    })
  }

  if (sent) {
    return (
      <AuthLayout title="Check your email" subtitle="If an account uses that address, a reset link is on its way.">
        <p className="muted">
          The link works once, for 30 minutes. Nothing there? Check your spam folder, or try again in a minute.
        </p>
        <p className="auth-switch">
          <Link to="/login">Back to log in</Link>
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Forgot your password?" subtitle="Enter your account email and we'll send you a link to choose a new one.">
      <form className="form" onSubmit={submit}>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <FormField label="Email" error={fieldErrors.email}>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus />
        </FormField>
        <button type="submit" className="button button-primary button-block" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send reset link'}
        </button>
      </form>
      <p className="auth-switch">
        Remembered it? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  )
}
