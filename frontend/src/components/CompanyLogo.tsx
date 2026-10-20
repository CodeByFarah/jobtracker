import { useEffect, useState } from 'react'
import { companyLogoUrl } from '../services/api'

interface CompanyLogoProps {
  companyId: number
  name: string
  /** When the logo last changed; null/undefined when the company has none (yet). */
  logoUpdatedAt?: string | null
  size?: 'small' | 'medium' | 'large'
}

/** Soft tints for companies without a logo, picked from the name so a company keeps its colour. */
const TINTS = ['blue', 'purple', 'pink', 'orange', 'green', 'teal', 'indigo'] as const

function tintFor(name: string) {
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return TINTS[hash % TINTS.length]
}

/** Logos that are opaque squares (app-icon style) fill the tile instead of sitting on white. */
const fullBleed = new Set<string>()

function isSolidSquare(img: HTMLImageElement): boolean {
  if (!img.naturalWidth || Math.abs(img.naturalWidth - img.naturalHeight) > 2) return false
  try {
    const size = 16
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const context = canvas.getContext('2d')
    if (!context) return false
    context.drawImage(img, 0, 0, size, size)
    // All four corners opaque: the image has its own background.
    return [
      [1, 1],
      [size - 2, 1],
      [1, size - 2],
      [size - 2, size - 2],
    ].every(([x, y]) => context.getImageData(x, y, 1, 1).data[3] > 240)
  } catch {
    return false
  }
}

/**
 * A company's logo, found automatically from its website. Until there is one (or if it can't be
 * loaded) it shows the company's initial on a tinted tile.
 */
export function CompanyLogo({ companyId, name, logoUpdatedAt, size = 'medium' }: CompanyLogoProps) {
  const [loaded, setLoaded] = useState<{ key: string; url: string | null } | null>(null)
  const key = logoUpdatedAt ? `${companyId}:${logoUpdatedAt}` : null

  useEffect(() => {
    if (!logoUpdatedAt || !key) return
    let cancelled = false
    companyLogoUrl(companyId, logoUpdatedAt).then((url) => {
      if (!cancelled) setLoaded({ key, url })
    })
    return () => {
      cancelled = true
    }
  }, [companyId, logoUpdatedAt, key])

  const url = loaded && loaded.key === key ? loaded.url : null
  if (url) {
    return (
      <span
        className={`company-logo company-logo-${size} company-logo-image ${fullBleed.has(url) ? 'company-logo-full' : ''}`}
        aria-hidden="true"
      >
        <img
          src={url}
          alt=""
          draggable={false}
          onLoad={(event) => {
            if (!fullBleed.has(url) && isSolidSquare(event.currentTarget)) {
              fullBleed.add(url)
              setLoaded({ key: key ?? '', url })
            }
          }}
          onError={() => setLoaded({ key: key ?? '', url: null })}
        />
      </span>
    )
  }
  return (
    <span className={`company-logo company-logo-${size} company-logo-tint-${tintFor(name)}`} aria-hidden="true">
      {name.trim().charAt(0).toUpperCase() || '?'}
    </span>
  )
}
