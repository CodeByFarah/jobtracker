import { Icon } from './Icon'

interface PaginationProps {
  /** Zero-based page index, as returned by the API. */
  page: number
  totalPages: number
  totalElements: number
  size: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, size, onPageChange }: PaginationProps) {
  if (totalElements === 0) return null
  const first = page * size + 1
  const last = Math.min((page + 1) * size, totalElements)

  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="pagination-summary">
        {first}–{last} of {totalElements}
      </span>
      <div className="pagination-controls">
        <button
          type="button"
          className="icon-button"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          aria-label="Previous page"
        >
          <Icon name="chevronLeft" />
        </button>
        <span className="pagination-page">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <button
          type="button"
          className="icon-button"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages}
          aria-label="Next page"
        >
          <Icon name="chevronRight" />
        </button>
      </div>
    </nav>
  )
}
