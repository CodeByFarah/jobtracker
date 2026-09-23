import { useState, type FormEvent } from 'react'
import { useSubmit } from '../../hooks/useSubmit'
import { applicationApi, taskApi } from '../../services/api'
import type { Task } from '../../types/api'
import { nullIfBlank } from '../../utils/form'
import { FormField } from '../FormField'
import { Modal } from '../Modal'

const SUGGESTIONS = [
  'Follow up with recruiter',
  'Send thank-you email',
  'Prepare for interview',
  'Submit additional documents',
  'Check application status',
]

interface TaskFormModalProps {
  /** Required when creating. */
  applicationId?: number
  task?: Task
  onClose: () => void
  onSaved: (task: Task) => void
}

export function TaskFormModal({ applicationId, task, onClose, onSaved }: TaskFormModalProps) {
  const [title, setTitle] = useState(task?.title ?? '')
  const [description, setDescription] = useState(task?.description ?? '')
  const [dueDate, setDueDate] = useState(task?.dueDate ?? '')
  const { submitting, formError, fieldErrors, run } = useSubmit()

  function submit(event: FormEvent) {
    event.preventDefault()
    run(async () => {
      const body = { title: title.trim(), description: nullIfBlank(description), dueDate: dueDate || null }
      const saved = task
        ? await taskApi.update(task.id, { ...body, completed: task.completed })
        : await applicationApi.createTask(applicationId!, body)
      onSaved(saved)
    })
  }

  return (
    <Modal
      title={task ? 'Edit task' : 'New follow-up task'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button button-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="task-form" className="button button-primary" disabled={submitting}>
            {submitting ? 'Saving…' : task ? 'Save changes' : 'Add task'}
          </button>
        </>
      }
    >
      <form id="task-form" className="form" onSubmit={submit} noValidate>
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <FormField label="Title" required error={fieldErrors.title}>
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={150} list="task-suggestions" />
        </FormField>
        <datalist id="task-suggestions">
          {SUGGESTIONS.map((s) => (
            <option key={s} value={s} />
          ))}
        </datalist>
        <FormField label="Due date" error={fieldErrors.dueDate}>
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </FormField>
        <FormField label="Description" error={fieldErrors.description}>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
        </FormField>
      </form>
    </Modal>
  )
}
