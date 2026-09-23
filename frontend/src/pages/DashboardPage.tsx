import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApplicationsOverTimeChart } from '../components/charts/ApplicationsOverTimeChart'
import { StatusBreakdownChart } from '../components/charts/StatusBreakdownChart'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { Icon } from '../components/Icon'
import { InterviewCard } from '../components/InterviewCard'
import { StatCard } from '../components/StatCard'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { TaskItem } from '../components/TaskItem'
import { useAsync } from '../hooks/useAsync'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { dashboardApi } from '../services/api'

export function DashboardPage() {
  useDocumentTitle('Dashboard')
  const { user } = useAuth()
  const navigate = useNavigate()
  const dashboard = useAsync(() => dashboardApi.get(), [])
  const [creating, setCreating] = useState(false)

  const firstName = user?.fullName.split(' ')[0]
  const d = dashboard.data

  return (
    <>
      <header className="page-header">
        <div>
          <h1>{firstName ? `Hi, ${firstName}` : 'Dashboard'}</h1>
          <p className="muted">Here’s where your job search stands.</p>
        </div>
        <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
          <Icon name="plus" /> New application
        </button>
      </header>

      {dashboard.error && !d && <ErrorState error={dashboard.error} onRetry={dashboard.reload} />}
      {dashboard.loading && !d && <LoadingState />}

      {d && d.totalApplications === 0 && (
        <EmptyState
          icon="briefcase"
          title="Your dashboard fills in as you go"
          text="Add the first job you’ve applied for (or want to). Statistics, charts and reminders will appear here."
          action={
            <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
              <Icon name="plus" /> Add your first application
            </button>
          }
        />
      )}

      {d && d.totalApplications > 0 && (
        <div className="dashboard">
          <section className="stat-grid" aria-label="Key numbers">
            <StatCard
              label="Total applications"
              value={d.totalApplications}
              detail={`${d.applicationsThisMonth} this month`}
              to="/applications"
            />
            <StatCard label="Active" value={d.activeApplications} detail="Applied → offer stage" />
            <StatCard
              label="Interviews"
              value={d.totalInterviews}
              detail={`${d.upcomingInterviewCount} upcoming`}
              to="/interviews"
            />
            <StatCard label="Offers" value={d.offers} to="/applications?status=OFFER" />
            <StatCard label="Rejected" value={d.rejected} to="/applications?status=REJECTED" />
            <StatCard
              label="Open tasks"
              value={d.outstandingTasks}
              detail={d.overdueTasks > 0 ? `${d.overdueTasks} overdue` : 'None overdue'}
              tone={d.overdueTasks > 0 ? 'warning' : 'default'}
              to="/tasks"
            />
          </section>

          <div className="chart-grid">
            <StatusBreakdownChart breakdown={d.statusBreakdown} />
            <ApplicationsOverTimeChart series={d.applicationsPerMonth} />
          </div>

          <div className="two-column">
            <section className="card">
              <header className="section-header">
                <h2>Upcoming interviews</h2>
                <Link to="/interviews">View all</Link>
              </header>
              {d.upcomingInterviews.length === 0 ? (
                <p className="muted empty-inline">No interviews scheduled.</p>
              ) : (
                <div className="stack">
                  {d.upcomingInterviews.map((interview) => (
                    <InterviewCard key={interview.id} interview={interview} />
                  ))}
                </div>
              )}
            </section>
            <section className="card">
              <header className="section-header">
                <h2>Follow-ups due</h2>
                <Link to="/tasks">View all</Link>
              </header>
              {d.upcomingTasks.length === 0 ? (
                <p className="muted empty-inline">You’re all caught up.</p>
              ) : (
                <div className="stack">
                  {d.upcomingTasks.map((task) => (
                    <TaskItem key={task.id} task={task} onChanged={() => dashboard.reload()} />
                  ))}
                </div>
              )}
            </section>
          </div>
        </div>
      )}

      {creating && (
        <ApplicationFormModal
          onClose={() => setCreating(false)}
          onSaved={(application) => navigate(`/applications/${application.id}`)}
        />
      )}
    </>
  )
}
