import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TaskFormModal } from '../components/forms/TaskFormModal'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { TaskItem } from '../components/TaskItem'
import { useAsync } from '../hooks/useAsync'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { taskApi } from '../services/api'
import type { Task } from '../types/api'

type Filter = 'open' | 'done' | 'all'
const TABS: { value: Filter; label: string }[] = [
  { value: 'open', label: 'To do' },
  { value: 'done', label: 'Completed' },
  { value: 'all', label: 'All' },
]

export function TasksPage() {
  useDocumentTitle('Tasks')
  const [filter, setFilter] = useState<Filter>('open')
  const tasks = useAsync(() => taskApi.list(filter === 'all' ? undefined : filter === 'done'), [filter])
  const [editing, setEditing] = useState<Task | null>(null)
  const [deleting, setDeleting] = useState<Task | null>(null)

  function onChanged(task: Task) {
    // Keep the ticked item visible in "All"; in the filtered tabs it moves to the other list.
    tasks.setData((list) =>
      filter === 'all'
        ? list?.map((t) => (t.id === task.id ? task : t))
        : list?.filter((t) => t.id !== task.id),
    )
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Follow-up tasks</h1>
          <p className="muted">Add tasks from an application’s page; tick them off here.</p>
        </div>
      </header>

      <div className="tabs" role="tablist" aria-label="Task filter">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={filter === tab.value}
            className={filter === tab.value ? 'tab active' : 'tab'}
            onClick={() => setFilter(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {tasks.error && <ErrorState error={tasks.error} onRetry={tasks.reload} />}
      {!tasks.error && !tasks.data && <LoadingState />}
      {tasks.data?.length === 0 && (
        <EmptyState
          icon="tasks"
          title={filter === 'open' ? 'Nothing to do right now' : filter === 'done' ? 'No completed tasks yet' : 'No tasks yet'}
          text="Follow-ups like “send thank-you email” are added from an application’s page."
          action={
            <Link to="/applications" className="button button-secondary">
              Go to applications
            </Link>
          }
        />
      )}
      {tasks.data && tasks.data.length > 0 && (
        <div className={`card stack ${tasks.loading ? 'is-refreshing' : ''}`}>
          {tasks.data.map((task) => (
            <TaskItem
              key={task.id}
              task={task}
              onChanged={onChanged}
              onEdit={() => setEditing(task)}
              onDelete={() => setDeleting(task)}
            />
          ))}
        </div>
      )}

      {editing && (
        <TaskFormModal
          task={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            tasks.reload()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete task?"
          message={`“${deleting.title}” will be removed permanently.`}
          onConfirm={async () => {
            await taskApi.remove(deleting.id)
            tasks.reload()
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </>
  )
}
