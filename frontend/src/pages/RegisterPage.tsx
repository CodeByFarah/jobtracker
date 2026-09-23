import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FormField } from '../components/FormField'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useSubmit } from '../hooks/useSubmit'
import { AuthLayout } from './AuthLayout'

export function RegisterPage() {
  useDocumentTitle('Create account')
  const { register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const { submitting, formError, fieldErrors, setFieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    if (password.length < 8) {
      setFieldErrors({ password: 'Use at least 8 characters' })
      return
    }
    if (password !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }
    run(async () => {
      await register(email, password, fullName)
      navigate('/dashboard', { replace: true })
    })
  }

  return (
    <AuthLayout title="Create your account" subtitle="Track every application, interview and follow-up in one place.">
      <form className="form" onSubmit={submit}>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <FormField label="Full name" error={fieldErrors.fullName}>
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" required maxLength={100} />
        </FormField>
        <FormField label="Email" error={fieldErrors.email}>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </FormField>
        <FormField label="Password" error={fieldErrors.password} hint="At least 8 characters">
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
          />
        </FormField>
        <FormField label="Confirm password" error={fieldErrors.confirm}>
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            required
          />
        </FormField>
        <button type="submit" className="button button-primary button-block" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="auth-switch">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  )
}
