import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { PublicOnly, RequireAuth } from './components/RouteGuards'
import { AuthProvider } from './hooks/useAuth'
import { ApplicationDetailPage } from './pages/ApplicationDetailPage'
import { ApplicationsPage } from './pages/ApplicationsPage'
import { CompaniesPage } from './pages/CompaniesPage'
import { CompanyDetailPage } from './pages/CompanyDetailPage'
import { DashboardPage } from './pages/DashboardPage'
import { InterviewsPage } from './pages/InterviewsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { TasksPage } from './pages/TasksPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<PublicOnly />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>
          <Route element={<RequireAuth />}>
            <Route element={<Layout />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/applications" element={<ApplicationsPage />} />
              <Route path="/applications/:id" element={<ApplicationDetailPage />} />
              <Route path="/companies" element={<CompaniesPage />} />
              <Route path="/companies/:id" element={<CompanyDetailPage />} />
              <Route path="/interviews" element={<InterviewsPage />} />
              <Route path="/tasks" element={<TasksPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
