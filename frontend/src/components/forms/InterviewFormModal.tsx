import { useState, type FormEvent } from 'react'
import { useSubmit } from '../../hooks/useSubmit'
import { applicationApi, interviewApi } from '../../services/api'
import {
  INTERVIEW_STATUSES,
  INTERVIEW_TYPES,
  type Interview,
  type InterviewStatus,
  type InterviewType,
} from '../../types/api'
import {
  INTERVIEW_STATUS_LABELS,
  INTERVIEW_TYPE_LABELS,
  instantToLocalInput,
  localInputToInstant,
} from '../../utils/format'
import { nullIfBlank, numberOrNull } from '../../utils/form'
import { FormField } from '../FormField'
import { Modal } from '../Modal'

interface InterviewFormModalProps {
  /** Required when creating. */
  applicationId?: number
  interview?: Interview
  onClose: () => void
  onSaved: (interview: Interview) => void
}

export function InterviewFormModal({ applicationId, interview, onClose, onSaved }: InterviewFormModalProps) {
  const [type, setType] = useState<InterviewType>(interview?.type ?? 'RECRUITER')
  const [scheduledAt, setScheduledAt] = useState(interview ? instantToLocalInput(interview.scheduledAt) : '')
  const [durationMinutes, setDurationMinutes] = useState(interview?.durationMinutes?.toString() ?? '45')
  const [interviewerName, setInterviewerName] = useState(interview?.interviewerName ?? '')
  const [location, setLocation] = useState(interview?.location ?? '')
  const [notes, setNotes] = useState(interview?.notes ?? '')
  const [status, setStatus] = useState<InterviewStatus>(interview?.status ?? 'SCHEDULED')
  const { submitting, formError, fieldErrors, setFieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!scheduledAt) {
      setFieldErrors({ scheduledAt: 'Choose the date and time' })
      return
    }
    run(async () => {
      const body = {
        type,
        scheduledAt: localInputToInstant(scheduledAt),
        durationMinutes: numberOrNull(durationMinutes),
        interviewerName: nullIfBlank(interviewerName),
        location: nullIfBlank(location),
        notes: nullIfBlank(notes),
        status,
      }
      const saved = interview
        ? await interviewApi.update(interview.id, body)
        : await applicationApi.createInterview(applicationId!, body)
      onSaved(saved)
    })
  }

  return (
    <Modal
      title={interview ? 'Edit interview' : 'Schedule interview'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button button-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="interview-form" className="button button-primary" disabled={submitting}>
            {submitting ? 'Saving…' : interview ? 'Save changes' : 'Schedule'}
          </button>
        </>
      }
    >
      <form id="interview-form" className="form" onSubmit={submit} noValidate>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <div className="form-row">
          <FormField label="Type" required error={fieldErrors.type}>
            <select value={type} onChange={(e) => setType(e.target.value as InterviewType)}>
              {INTERVIEW_TYPES.map((t) => (
                <option key={t} value={t}>
                  {INTERVIEW_TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Status" error={fieldErrors.status}>
            <select value={status} onChange={(e) => setStatus(e.target.value as InterviewStatus)}>
              {INTERVIEW_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {INTERVIEW_STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </FormField>
        </div>
        <div className="form-row">
          <FormField
            label="Date & time"
            required
            error={fieldErrors.scheduledAt}
            hint="Scheduled interviews must be in the future; log past ones as Completed."
          >
            <input type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} required />
          </FormField>
          <FormField label="Duration (minutes)" error={fieldErrors.durationMinutes}>
            <input type="number" min={1} max={1440} value={durationMinutes} onChange={(e) => setDurationMinutes(e.target.value)} />
          </FormField>
        </div>
        <div className="form-row">
          <FormField label="Interviewer" error={fieldErrors.interviewerName}>
            <input value={interviewerName} onChange={(e) => setInterviewerName(e.target.value)} maxLength={150} />
          </FormField>
          <FormField label="Location or link" error={fieldErrors.location}>
            <input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={255} placeholder="Office address or video link" />
          </FormField>
        </div>
        <FormField label="Notes" error={fieldErrors.notes}>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={4} placeholder="Topics to prepare, questions to ask, feedback…" />
        </FormField>
      </form>
    </Modal>
  )
}
