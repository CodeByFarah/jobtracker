import type { ReactNode } from 'react'

/** Login and registration: a large centred headline and a single form, nothing else. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <img className="auth-logo" src={`${import.meta.env.BASE_URL}favicon.svg`} alt="JobTrack" width={44} height={44} />
        <h1>{title}</h1>
        <p className="auth-subtitle">{subtitle}</p>
        {children}
      </div>
    </div>
  )
}
