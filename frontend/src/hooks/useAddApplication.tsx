import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { ImportLinkModal } from '../components/forms/ImportLinkModal'
import type { JobLinkPreview } from '../types/api'

/**
 * Adding an application, the same way on every page: paste a job link, the local AI reads the
 * posting, and the user checks the filled-in details before saving. Render `dialogs` once and
 * call `fromLink` from a button.
 */
export function useAddApplication() {
  const navigate = useNavigate()
  const [prefill, setPrefill] = useState<JobLinkPreview | null>(null)
  const [pasting, setPasting] = useState(false)

  const dialogs = (
    <>
      {pasting && (
        <ImportLinkModal
          onClose={() => setPasting(false)}
          onImported={(preview) => {
            setPasting(false)
            setPrefill(preview)
          }}
        />
      )}
      {prefill && (
        <ApplicationFormModal
          prefill={prefill}
          onClose={() => setPrefill(null)}
          onSaved={(application) => navigate(`/applications/${application.id}`)}
        />
      )}
    </>
  )

  return { fromLink: () => setPasting(true), dialogs }
}
