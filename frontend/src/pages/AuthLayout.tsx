import type { ReactNode } from 'react'

/** Centered card used by the login and registration pages. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-card card">
        <div className="brand auth-brand">
          <img src="/favicon.svg" alt="" width={32} height={32} /> JobTrack
        </div>
        <h1>{title}</h1>
        <p className="muted">{subtitle}</p>
        {children}
      </div>
    </div>
  )
}
