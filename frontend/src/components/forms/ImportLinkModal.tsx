import { useState, type FormEvent } from 'react'
import { useSubmit } from '../../hooks/useSubmit'
import { applicationApi } from '../../services/api'
import type { JobLinkPreview } from '../../types/api'
import { FormField } from '../FormField'
import { Modal } from '../Modal'

interface ImportLinkModalProps {
  onClose: () => void
  /** Called with the details read from the page; the caller opens the application form with them. */
  onImported: (preview: JobLinkPreview) => void
}

/** Step 1 of "Add from link": paste a posting URL and let the local AI read it. */
export function ImportLinkModal({ onClose, onImported }: ImportLinkModalProps) {
  const [url, setUrl] = useState('')
  const { submitting, formError, fieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      onImported(await applicationApi.importLink(url.trim()))
    })
  }

  return (
    <Modal
      title="Add a new application"
      size="small"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button button-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="import-link-form" className="button button-primary" disabled={submitting || !url.trim()}>
            {submitting ? 'Reading…' : 'Read posting'}
          </button>
        </>
      }
    >
      <form id="import-link-form" className="form" onSubmit={submit} noValidate>
        <p className="muted import-intro">
          Paste the link to the job posting. Your local AI reads it and fills in the title, company, location and
          salary for you to check.
        </p>
        {formError && !fieldErrors.url && <p className="form-error" role="alert">{formError}</p>}
        <FormField
          label="Job posting link"
          error={fieldErrors.url}
          hint="Works best with company career pages, Greenhouse, Lever, Workable and similar. LinkedIn often blocks automatic reading."
        >
          <input
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://"
            maxLength={500}
            disabled={submitting}
            autoFocus
            required
          />
        </FormField>
        {submitting && (
          <p className="import-progress" role="status">
            <span className="spinner" aria-hidden="true" />
            Reading the posting with your local AI. This can take up to a minute.
          </p>
        )}
      </form>
    </Modal>
  )
}
