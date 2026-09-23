import { useState } from 'react'
import { Link } from 'react-router-dom'
import { taskApi } from '../services/api'
import type { Task } from '../types/api'
import { formatDate, formatInstantDate, isOverdue, relativeDay } from '../utils/format'
import { Icon } from './Icon'

interface TaskItemProps {
  task: Task
  showApplication?: boolean
  onChanged: (task: Task) => void
  onEdit?: () => void
  onDelete?: () => void
}

export function TaskItem({ task, showApplication = true, onChanged, onEdit, onDelete }: TaskItemProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const overdue = isOverdue(task.dueDate, task.completed)

  async function toggle() {
    setBusy(true)
    setError(null)
    try {
      onChanged(await taskApi.setCompleted(task.id, !task.completed))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update the task')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={`task-item ${task.completed ? 'task-done' : ''}`}>
      <input
        type="checkbox"
        className="task-check"
        checked={task.completed}
        onChange={toggle}
        disabled={busy}
        aria-label={task.completed ? `Mark "${task.title}" as not done` : `Mark "${task.title}" as done`}
      />
      <div className="task-body">
        <span className="task-title">{task.title}</span>
        {task.description && <span className="task-description">{task.description}</span>}
        <span className="task-meta">
          {task.completed ? (
            <span>
              <Icon name="check" size={14} /> Done {formatInstantDate(task.completedAt)}
            </span>
          ) : (
            task.dueDate && (
              <span className={overdue ? 'text-danger' : undefined}>
                <Icon name={overdue ? 'alert' : 'calendar'} size={14} /> {overdue ? 'Overdue · ' : 'Due '}
                {formatDate(task.dueDate)} ({relativeDay(task.dueDate)})
              </span>
            )
          )}
          {showApplication && (
            <Link to={`/applications/${task.applicationId}`}>
              {task.jobTitle} · {task.companyName}
            </Link>
          )}
        </span>
        {error && <span className="field-error" role="alert">{error}</span>}
      </div>
      {(onEdit || onDelete) && (
        <div className="row-actions">
          {onEdit && (
            <button type="button" className="icon-button" onClick={onEdit} aria-label={`Edit "${task.title}"`}>
              <Icon name="edit" size={16} />
            </button>
          )}
          {onDelete && (
            <button type="button" className="icon-button" onClick={onDelete} aria-label={`Delete "${task.title}"`}>
              <Icon name="trash" size={16} />
            </button>
          )}
        </div>
      )}
    </div>
  )
}
