import { useRef, useState, type FormEvent } from 'react'
import { useAsync } from '../../hooks/useAsync'
import { useSubmit } from '../../hooks/useSubmit'
import { applicationApi, companyApi } from '../../services/api'
import {
  APPLICATION_STATUSES,
  EMPLOYMENT_TYPES,
  type Application,
  type ApplicationStatus,
  type EmploymentType,
} from '../../types/api'
import { EMPLOYMENT_LABELS, STATUS_LABELS, todayIso } from '../../utils/format'
import { nullIfBlank, numberOrNull } from '../../utils/form'
import { FormField } from '../FormField'
import { Modal } from '../Modal'

const NEW_COMPANY = 'new'

interface ApplicationFormModalProps {
  /** Edit this application; omit to create a new one. */
  application?: Application
  /** Pre-selects the company when creating from a company page. */
  defaultCompanyId?: number
  onClose: () => void
  onSaved: (application: Application) => void
}

export function ApplicationFormModal({ application, defaultCompanyId, onClose, onSaved }: ApplicationFormModalProps) {
  const isEdit = application !== undefined
  const companies = useAsync(() => companyApi.list(), [])

  const [companyChoice, setCompanyChoice] = useState<string>(
    String(application?.company.id ?? defaultCompanyId ?? ''),
  )
  const [newCompanyName, setNewCompanyName] = useState('')
  const [jobTitle, setJobTitle] = useState(application?.jobTitle ?? '')
  const [jobUrl, setJobUrl] = useState(application?.jobUrl ?? '')
  const [location, setLocation] = useState(application?.location ?? '')
  const [employmentType, setEmploymentType] = useState<EmploymentType | ''>(application?.employmentType ?? '')
  const [salaryMin, setSalaryMin] = useState(application?.salaryMin?.toString() ?? '')
  const [salaryMax, setSalaryMax] = useState(application?.salaryMax?.toString() ?? '')
  const [salaryCurrency, setSalaryCurrency] = useState(application?.salaryCurrency ?? '')
  const [status, setStatus] = useState<ApplicationStatus>('APPLIED')
  const [applicationDate, setApplicationDate] = useState(application?.applicationDate ?? (isEdit ? '' : todayIso()))
  const [notes, setNotes] = useState(application?.notes ?? '')
  const { submitting, formError, fieldErrors, setFieldErrors, run } = useSubmit()
  const createdCompany = useRef<{ id: number; name: string } | null>(null)

  // With no companies yet, go straight to "create a new company" instead of an empty dropdown.
  const noCompanies = companies.data !== undefined && companies.data.length === 0
  const creatingCompany = companyChoice === NEW_COMPANY || noCompanies

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!creatingCompany && !companyChoice) {
      setFieldErrors({ companyId: 'Choose a company' })
      return
    }
    if (creatingCompany && !newCompanyName.trim()) {
      setFieldErrors({ newCompany: 'Enter the company name' })
      return
    }
    run(async () => {
      let companyId: number
      if (creatingCompany) {
        // Remember a company created on an earlier attempt, so fixing a validation error
        // and resubmitting does not try to create it twice.
        if (createdCompany.current === null || createdCompany.current.name !== newCompanyName.trim()) {
          const created = await companyApi.create({ name: newCompanyName.trim() })
          createdCompany.current = { id: created.id, name: created.name }
        }
        companyId = createdCompany.current.id
      } else {
        companyId = Number(companyChoice)
      }
      const body = {
        companyId,
        jobTitle: jobTitle.trim(),
        jobUrl: nullIfBlank(jobUrl),
        location: nullIfBlank(location),
        employmentType: employmentType || null,
        salaryMin: numberOrNull(salaryMin),
        salaryMax: numberOrNull(salaryMax),
        salaryCurrency: nullIfBlank(salaryCurrency)?.toUpperCase() ?? null,
        applicationDate: applicationDate || null,
        notes: nullIfBlank(notes),
      }
      const saved = isEdit
        ? await applicationApi.update(application.id, body)
        : await applicationApi.create({ ...body, status })
      onSaved(saved)
    })
  }

  return (
    <Modal
      title={isEdit ? 'Edit application' : 'New application'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button button-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="application-form" className="button button-primary" disabled={submitting}>
            {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Add application'}
          </button>
        </>
      }
    >
      <form id="application-form" className="form" onSubmit={submit} noValidate>
        {formError && <p className="form-error" role="alert">{formError}</p>}

        <FormField label="Job title" required error={fieldErrors.jobTitle}>
          <input value={jobTitle} onChange={(e) => setJobTitle(e.target.value)} required maxLength={150} placeholder="e.g. Backend Engineer" />
        </FormField>

        <div className="form-row">
          {!noCompanies && (
            <FormField label="Company" required error={fieldErrors.companyId}>
              <select value={companyChoice} onChange={(e) => setCompanyChoice(e.target.value)} disabled={companies.loading && !companies.data}>
                <option value="">{companies.loading && !companies.data ? 'Loading…' : 'Select a company'}</option>
                {companies.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
                <option value={NEW_COMPANY}>+ Add a new company…</option>
              </select>
            </FormField>
          )}
          {creatingCompany && (
            <FormField label="New company name" required error={fieldErrors.newCompany ?? fieldErrors.name}>
              <input value={newCompanyName} onChange={(e) => setNewCompanyName(e.target.value)} maxLength={150} />
            </FormField>
          )}
        </div>

        <div className="form-row">
          <FormField label="Location" error={fieldErrors.location}>
            <input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={150} placeholder="City or Remote" />
          </FormField>
          <FormField label="Employment type" error={fieldErrors.employmentType}>
            <select value={employmentType} onChange={(e) => setEmploymentType(e.target.value as EmploymentType | '')}>
              <option value="">Not specified</option>
              {EMPLOYMENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {EMPLOYMENT_LABELS[t]}
                </option>
              ))}
            </select>
          </FormField>
        </div>

        <FormField label="Job posting URL" error={fieldErrors.jobUrl}>
          <input type="url" value={jobUrl} onChange={(e) => setJobUrl(e.target.value)} maxLength={500} placeholder="https://" />
        </FormField>

        <div className="form-row form-row-3">
          <FormField label="Salary from" error={fieldErrors.salaryMin}>
            <input type="number" min={0} inputMode="numeric" value={salaryMin} onChange={(e) => setSalaryMin(e.target.value)} />
          </FormField>
          <FormField label="Salary to" error={fieldErrors.salaryMax}>
            <input type="number" min={0} inputMode="numeric" value={salaryMax} onChange={(e) => setSalaryMax(e.target.value)} />
          </FormField>
          <FormField label="Currency" error={fieldErrors.salaryCurrency}>
            <input value={salaryCurrency} onChange={(e) => setSalaryCurrency(e.target.value)} maxLength={3} placeholder="USD" />
          </FormField>
        </div>

        <div className="form-row">
          {!isEdit && (
            <FormField label="Current status" error={fieldErrors.status}>
              <select value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)}>
                {APPLICATION_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABELS[s]}
                  </option>
                ))}
              </select>
            </FormField>
          )}
          <FormField label="Date applied" error={fieldErrors.applicationDate}>
            <input type="date" value={applicationDate} onChange={(e) => setApplicationDate(e.target.value)} max={todayIso()} />
          </FormField>
        </div>
        {isEdit && <p className="field-hint">To change the status, use “Change status” on the application page.</p>}

        <FormField label="Notes" error={fieldErrors.notes}>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={4} />
        </FormField>
      </form>
    </Modal>
  )
}
