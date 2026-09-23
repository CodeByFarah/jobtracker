import { useState, type FormEvent } from 'react'
import { FormField } from '../components/FormField'
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
    </>
  )
}
