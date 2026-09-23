import { useState, type FormEvent } from 'react'
import { useSubmit } from '../../hooks/useSubmit'
import { companyApi } from '../../services/api'
import type { Company } from '../../types/api'
import { nullIfBlank } from '../../utils/form'
import { FormField } from '../FormField'
import { Modal } from '../Modal'

interface CompanyFormModalProps {
  company?: Company
  onClose: () => void
  onSaved: (company: Company) => void
}

export function CompanyFormModal({ company, onClose, onSaved }: CompanyFormModalProps) {
  const [name, setName] = useState(company?.name ?? '')
  const [website, setWebsite] = useState(company?.website ?? '')
  const [industry, setIndustry] = useState(company?.industry ?? '')
  const [location, setLocation] = useState(company?.location ?? '')
  const [notes, setNotes] = useState(company?.notes ?? '')
  const { submitting, formError, fieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      const body = {
        name: name.trim(),
        website: nullIfBlank(website),
        industry: nullIfBlank(industry),
        location: nullIfBlank(location),
        notes: nullIfBlank(notes),
      }
      const saved = company ? await companyApi.update(company.id, body) : await companyApi.create(body)
      onSaved(saved)
    })
  }

  return (
    <Modal
      title={company ? 'Edit company' : 'New company'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button button-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="company-form" className="button button-primary" disabled={submitting}>
            {submitting ? 'Saving…' : company ? 'Save changes' : 'Create company'}
          </button>
        </>
      }
    >
      <form id="company-form" className="form" onSubmit={submit} noValidate>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <FormField label="Name" required error={fieldErrors.name}>
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={150} />
        </FormField>
        <FormField label="Website" error={fieldErrors.website} hint="Starting with https://">
          <input type="url" value={website} onChange={(e) => setWebsite(e.target.value)} maxLength={500} placeholder="https://" />
        </FormField>
        <div className="form-row">
          <FormField label="Industry" error={fieldErrors.industry}>
            <input value={industry} onChange={(e) => setIndustry(e.target.value)} maxLength={100} />
          </FormField>
          <FormField label="Location" error={fieldErrors.location}>
            <input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={150} />
          </FormField>
        </div>
        <FormField label="Notes" error={fieldErrors.notes}>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={4} />
        </FormField>
      </form>
    </Modal>
  )
}
