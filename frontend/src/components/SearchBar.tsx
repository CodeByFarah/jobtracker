import { Icon } from './Icon'

interface SearchBarProps {
  value: string
  onChange: (value: string) => void
  placeholder: string
  label: string
}

export function SearchBar({ value, onChange, placeholder, label }: SearchBarProps) {
  return (
    <div className="search-bar">
      <Icon name="search" className="search-icon" />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={label}
      />
      {value && (
        <button type="button" className="icon-button search-clear" onClick={() => onChange('')} aria-label="Clear search">
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  )
}
