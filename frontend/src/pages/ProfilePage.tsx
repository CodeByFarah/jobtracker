import { useState, type FormEvent } from 'react'
import { FormField } from '../components/FormField'
import { GmailCard } from '../components/GmailCard'
import { nullIfBlank } from '../utils/form'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useSubmit } from '../hooks/useSubmit'
import { userApi } from '../services/api'
import { formatInstantDate } from '../utils/format'

export function ProfilePage() {
  useDocumentTitle('Profile')
  const { user, setUser } = useAuth()
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [headline, setHeadline] = useState(user?.headline ?? '')
  const [location, setLocation] = useState(user?.location ?? '')
  const [saved, setSaved] = useState(false)
  const { submitting, formError, fieldErrors, run } = useSubmit()

  if (!user) return null

  function submit(event: FormEvent) {
    event.preventDefault()
    setSaved(false)
    run(async () => {
      const updated = await userApi.update({
        fullName: fullName.trim(),
        headline: nullIfBlank(headline),
        location: nullIfBlank(location),
      })
      setUser(updated)
      setSaved(true)
    })
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Profile</h1>
          <p className="muted">Member since {formatInstantDate(user.createdAt)}</p>
        </div>
      </header>
      <section className="card narrow-card">
        <form className="form" onSubmit={submit} noValidate>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <FormField label="Email" hint="Your email is your login and can’t be changed.">
            <input value={user.email} readOnly disabled />
          </FormField>
          <FormField label="Full name" required error={fieldErrors.fullName}>
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={100} />
          </FormField>
          <FormField label="Headline" error={fieldErrors.headline} hint="e.g. Backend engineer looking for remote roles">
            <input value={headline} onChange={(e) => setHeadline(e.target.value)} maxLength={150} />
          </FormField>
          <FormField label="Location" error={fieldErrors.location}>
            <input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={150} />
          </FormField>
          <div className="form-actions">
            <button type="submit" className="button button-primary" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save profile'}
            </button>
            {saved && (
              <span className="text-success" role="status">
                Saved
              </span>
            )}
          </div>
        </form>
      </section>
      <ChangePasswordCard />
      <GmailCard />
      <DeleteAccountCard />
    </>
  )
}

/** Changes the password. Other devices are signed out; this one gets a fresh session. */
function ChangePasswordCard() {
  const { acceptSession } = useAuth()
  const [open, setOpen] = useState(false)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [done, setDone] = useState(false)
  const { submitting, formError, fieldErrors, setFieldErrors, run } = useSubmit()

  function close() {
    setOpen(false)
    setCurrent('')
    setNext('')
    setConfirm('')
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (next.length < 8) {
      setFieldErrors({ newPassword: 'Use at least 8 characters' })
      return
    }
    if (next !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }
    run(async () => {
      acceptSession(await userApi.changePassword({ currentPassword: current, newPassword: next }))
      close()
      setDone(true)
    })
  }

  return (
    <section className="card narrow-card" aria-labelledby="password-heading">
      <header className="section-header">
        <h2 id="password-heading">Password</h2>
      </header>
      {open ? (
        <form className="form" onSubmit={submit} noValidate>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <FormField label="Current password" required error={fieldErrors.currentPassword}>
            <input
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              required
              autoFocus
            />
          </FormField>
          <FormField label="New password" required error={fieldErrors.newPassword} hint="At least 8 characters">
            <input
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              required
              minLength={8}
              maxLength={72}
            />
          </FormField>
          <FormField label="Confirm new password" required error={fieldErrors.confirm}>
            <input
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
          </FormField>
          <div className="form-actions">
            <button type="submit" className="button button-primary" disabled={submitting || !current}>
              {submitting ? 'Saving…' : 'Change password'}
            </button>
            <button type="button" className="button button-ghost" onClick={close} disabled={submitting}>
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <>
          <p className="muted gmail-intro">
            Changing your password signs you out on every other device.
          </p>
          <div className="form-actions">
            <button
              type="button"
              className="button button-ghost"
              onClick={() => {
                setDone(false)
                setOpen(true)
              }}
            >
              Change password…
            </button>
            {done && (
              <span className="text-success" role="status">
                Password changed
              </span>
            )}
          </div>
        </>
      )}
    </section>
  )
}

/** Permanently removes the account and all its data. Asks for the password first. */
function DeleteAccountCard() {
  const { logout } = useAuth()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const { submitting, formError, fieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      await userApi.deleteAccount(password)
      logout()
    })
  }

  return (
    <section className="card narrow-card" aria-labelledby="delete-account-heading">
      <header className="section-header">
        <h2 id="delete-account-heading">Delete account</h2>
      </header>
      <p className="muted gmail-intro">
        Deletes your account, every job and company in it, and disconnects Gmail. This can't be undone.
      </p>
      {open ? (
        <form className="form" onSubmit={submit} noValidate>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <FormField label="Password" required error={fieldErrors.password} hint="Enter your password to confirm.">
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoFocus
            />
          </FormField>
          <div className="form-actions">
            <button type="submit" className="button button-danger" disabled={submitting || !password}>
              {submitting ? 'Deleting…' : 'Delete my account'}
            </button>
            <button type="button" className="button button-ghost" onClick={() => setOpen(false)} disabled={submitting}>
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <div className="form-actions">
          <button type="button" className="button button-ghost-danger" onClick={() => setOpen(true)}>
            Delete account…
          </button>
        </div>
      )}
    </section>
  )
}
