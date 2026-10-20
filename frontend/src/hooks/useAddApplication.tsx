import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { ImportLinkModal } from '../components/forms/ImportLinkModal'
import type { JobLinkPreview } from '../types/api'

type Step = 'closed' | 'link' | 'form'

/**
 * Adding an application, the same way on every page: paste a job link and the local AI fills the
 * form in, or type it in by hand. Render `dialogs` once; call `fromLink` / `manually` from buttons.
 */
export function useAddApplication() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('closed')
  const [prefill, setPrefill] = useState<JobLinkPreview | undefined>(undefined)

  function manually() {
    setPrefill(undefined)
    setStep('form')
  }

  const dialogs = (
    <>
      {step === 'link' && (
        <ImportLinkModal
          onClose={() => setStep('closed')}
          onManual={manually}
          onImported={(preview) => {
            setPrefill(preview)
            setStep('form')
          }}
        />
      )}
      {step === 'form' && (
        <ApplicationFormModal
          prefill={prefill}
          onClose={() => {
            setStep('closed')
            setPrefill(undefined)
          }}
          onSaved={(application) => navigate(`/applications/${application.id}`)}
        />
      )}
    </>
  )

  return { fromLink: () => setStep('link'), manually, dialogs }
}
