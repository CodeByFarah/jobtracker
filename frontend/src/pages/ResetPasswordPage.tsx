import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { FormField } from '../components/FormField'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useSubmit } from '../hooks/useSubmit'
import { authApi } from '../services/api'
import { AuthLayout } from './AuthLayout'

/** Opened from the emailed link: sets a new password and logs the user in. */
export function ResetPasswordPage() {
  useDocumentTitle('Choose a new password')
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { acceptSession } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const { submitting, formError, fieldErrors, setFieldErrors, run } = useSubmit()

  if (!token) {
    return (
      <AuthLayout title="This link isn't complete" subtitle="Open the link from the email again, or ask for a new one.">
        <p className="auth-switch">
          <Link to="/forgot-password">Send a new link</Link>
        </p>
      </AuthLayout>
    )
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (password.length < 8) {
      setFieldErrors({ newPassword: 'Use at least 8 characters' })
      return
    }
    if (password !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }
    run(async () => {
      acceptSession(await authApi.resetPassword({ token, newPassword: password }))
      navigate('/dashboard', { replace: true })
    })
  }

  return (
    <AuthLayout title="Choose a new password" subtitle="You'll be logged in, and signed out everywhere else.">
      <form className="form" onSubmit={submit}>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        {fieldErrors.token && (
          <p className="form-error" role="alert">
            {fieldErrors.token} <Link to="/forgot-password">Send a new link</Link>
          </p>
        )}
        <FormField label="New password" error={fieldErrors.newPassword} hint="At least 8 characters">
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            autoFocus
          />
        </FormField>
        <FormField label="Confirm new password" error={fieldErrors.confirm}>
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            required
          />
        </FormField>
        <button type="submit" className="button button-primary button-block" disabled={submitting}>
          {submitting ? 'Saving…' : 'Set new password'}
        </button>
      </form>
    </AuthLayout>
  )
}
