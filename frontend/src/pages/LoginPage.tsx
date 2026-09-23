import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { FormField } from '../components/FormField'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useSubmit } from '../hooks/useSubmit'
import { AuthLayout } from './AuthLayout'

export function LoginPage() {
  useDocumentTitle('Log in')
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { submitting, formError, fieldErrors, run } = useSubmit()

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/dashboard'

  function submit(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      await login(email, password)
      navigate(redirectTo, { replace: true })
    })
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Log in to continue your job search.">
      <form className="form" onSubmit={submit}>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <FormField label="Email" error={fieldErrors.email}>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </FormField>
        <FormField label="Password" error={fieldErrors.password}>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </FormField>
        <button type="submit" className="button button-primary button-block" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className="auth-switch">
        New to JobTrack? <Link to="/register">Create an account</Link>
      </p>
    </AuthLayout>
  )
}
