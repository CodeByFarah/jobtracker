import type { CSSProperties, ReactNode } from 'react'

const STAGES = ['Applied', 'Screening', 'Interview', 'Offer']

const POINTS: { title: string; text: string; icon: ReactNode }[] = [
  {
    title: 'Reads your hiring emails',
    text: 'Connect Gmail (read-only) and every confirmation, test, invite and decision lands on the right application.',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect x="3" y="5" width="18" height="14" rx="3" />
        <path d="m4 7 8 6 8-6" />
      </svg>
    ),
  },
  {
    title: 'AI that runs where you want',
    text: 'Qwen3 on your own machine through Ollama, or an online model on a server. Same rules either way.',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1" />
        <circle cx="12" cy="12" r="3.2" />
      </svg>
    ),
  },
  {
    title: 'Yours, and only yours',
    text: 'Statuses only move forward, every change shows the email behind it, and nothing is shared with other accounts.',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3 5 6v5c0 4.4 3 8.3 7 9.5 4-1.2 7-5.1 7-9.5V6z" />
        <path d="m9 12 2.2 2.2L15.5 10" />
      </svg>
    ),
  },
]

/** Sign-in screens: what JobTrack is on the left, the form on the right (stacked on phones). */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  const base = import.meta.env.BASE_URL
  return (
    <div className="auth-page">
      <aside className="auth-story" aria-label="About JobTrack">
        <div className="auth-brand">
          <img src={`${base}favicon.svg`} alt="" width={32} height={32} />
          <span>JobTrack</span>
        </div>

        <div className="auth-pitch">
          <p className="auth-eyebrow">Job search, on autopilot</p>
          <h2 className="auth-headline">
            The job tracker that <em>updates itself.</em>
          </h2>
          <p className="auth-lede">
            Log your applications once. JobTrack reads the replies in your inbox and moves each one along, and adds
            the ones you forgot.
          </p>

          <ol className="auth-pipeline" aria-label="Application stages">
            {STAGES.map((stage, i) => (
              <li key={stage} style={{ '--i': i } as CSSProperties}>
                <span className="auth-pipeline-dot" aria-hidden="true" />
                {stage}
              </li>
            ))}
          </ol>

          <ul className="auth-points">
            {POINTS.map((point, i) => (
              <li key={point.title} style={{ '--i': i } as CSSProperties}>
                <span className="auth-point-icon">{point.icon}</span>
                <span>
                  <strong>{point.title}</strong>
                  {point.text}
                </span>
              </li>
            ))}
          </ul>

          <a
            className="auth-selfhost"
            href="https://github.com/CodeByFarah/jobtracker#self-hosting-everything-on-your-own-computer"
            target="_blank"
            rel="noreferrer"
          >
            <span className="auth-selfhost-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <rect x="3" y="4" width="18" height="12" rx="2" />
                <path d="M8 20h8M12 16v4" />
              </svg>
            </span>
            <span>
              <strong>Want it 100% private? Run it on your own computer.</strong>
              The app, the database and the AI, with one Docker command. Your emails never leave your machine.
              <span className="auth-selfhost-cta">Self-hosting guide →</span>
            </span>
          </a>
        </div>

        <footer className="auth-story-footer">
          <a href={`${base}privacy.html`}>Privacy</a>
          <span aria-hidden="true">·</span>
          <a href="https://github.com/CodeByFarah/jobtracker" target="_blank" rel="noreferrer">
            Source on GitHub
          </a>
        </footer>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <img className="auth-logo" src={`${base}favicon.svg`} alt="JobTrack" width={44} height={44} />
          <h1>{title}</h1>
          <p className="auth-subtitle">{subtitle}</p>
          {children}
          <p className="auth-legal">
            <a href={`${base}privacy.html`}>Privacy Policy</a>
          </p>
        </div>
      </main>
    </div>
  )
}
