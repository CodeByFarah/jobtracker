import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApplicationsOverTimeChart } from '../components/charts/ApplicationsOverTimeChart'
import { StatusBreakdownChart } from '../components/charts/StatusBreakdownChart'
import { CheckUpdatesButton } from '../components/CheckUpdates'
import { EmailEventList } from '../components/EmailEventList'
import { ApplicationFormModal } from '../components/forms/ApplicationFormModal'
import { GmailPrompt } from '../components/GmailPrompt'
import { Icon } from '../components/Icon'
import { InterviewCard } from '../components/InterviewCard'
import { StatCard } from '../components/StatCard'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { useAsync } from '../hooks/useAsync'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { dashboardApi, gmailApi } from '../services/api'

export function DashboardPage() {
  useDocumentTitle('Dashboard')
  const { user } = useAuth()
  const navigate = useNavigate()
  const dashboard = useAsync(() => dashboardApi.get(), [])
  const gmail = useAsync(() => gmailApi.status(), [])
  const inbox = useAsync(() => gmailApi.events(5), [])
  const [creating, setCreating] = useState(false)

  const firstName = user?.fullName.split(' ')[0]
  const d = dashboard.data
  const lastCheck = gmail.data?.lastRun

  function reloadAll() {
    dashboard.reload()
    gmail.reload()
    inbox.reload()
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1>{firstName ? `Hi, ${firstName}` : 'Dashboard'}</h1>
          <p className="muted">Here’s where your job search stands.</p>
        </div>
        <div className="header-actions">
          <button type="button" className="button button-secondary" onClick={() => setCreating(true)}>
            <Icon name="plus" /> New application
          </button>
          <CheckUpdatesButton onUpdated={reloadAll} />
        </div>
      </header>

      <GmailPrompt />

      {dashboard.error && !d && <ErrorState error={dashboard.error} onRetry={dashboard.reload} />}
      {dashboard.loading && !d && <LoadingState />}

      {d && d.totalApplications === 0 && (
        <EmptyState
          icon="briefcase"
          title="Your dashboard fills in as you go"
          text="Add the first job you’ve applied for (or want to). Statistics, charts and email updates will appear here."
          action={
            <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
              <Icon name="plus" /> Add your first application
            </button>
          }
        />
      )}

      {d && d.totalApplications > 0 && (
        <div className="dashboard">
          <section className="stat-grid stat-grid-5" aria-label="Key numbers">
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
          </section>

          {gmail.data?.connected && (
            <section className="card">
              <header className="section-header">
                <h2>Latest from your inbox</h2>
                <Link to="/profile">Gmail settings</Link>
              </header>
              {lastCheck?.overview && <p className="inbox-overview">{lastCheck.overview}</p>}
              {inbox.data && inbox.data.length > 0 ? (
                <EmailEventList events={inbox.data} showApplication />
              ) : (
                <p className="muted empty-inline">
                  No job emails matched yet. Click “Check for updates” to scan your inbox.
                </p>
              )}
            </section>
          )}

          <div className="chart-grid">
            <StatusBreakdownChart breakdown={d.statusBreakdown} />
            <ApplicationsOverTimeChart series={d.applicationsPerMonth} />
          </div>

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
