import { Link } from 'react-router-dom'
import { EmptyState } from '../components/States'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <EmptyState
      icon="search"
      title="Page not found"
      text="The page you’re looking for doesn’t exist."
      action={
        <Link to="/dashboard" className="button button-primary">
          Back to dashboard
        </Link>
      }
    />
  )
}
