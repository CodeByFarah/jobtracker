# JobTrack

JobTrack is a job application tracker. It keeps a job search in one place: the companies you're
talking to, every application and how it progressed, interviews, and a dashboard that shows where
things stand. Connect Gmail and a local AI model reads your job emails and keeps every status up to date.

It is a full-stack project: a Spring Boot REST API backed by PostgreSQL, and a React + TypeScript
frontend. Every number and list in the UI comes from the database; nothing is mocked.

---

## Features

- **Accounts.** Register, log in and log out, and edit your profile. Passwords are hashed with BCrypt, and the API uses stateless JWT authentication.
- **Companies.** Create, search, edit and delete companies, and see each company's applications.
- **Applications.** Track title, company, URL, location, employment type, salary range, application date and notes.
- **Status workflow.** Statuses run `SAVED → APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED`, and `REJECTED` / `WITHDRAWN` can be reached from any open stage. Only valid transitions are allowed.
- **Status history.** Every status change is recorded with the previous status, the new status and a timestamp, and shown as a timeline.
- **Interviews.** Schedule, edit and delete interviews by type (recruiter, technical, system design, …) and status. Dates are validated: a scheduled interview must be in the future, and a completed one must be in the past.
- **Gmail tracking.** Until Gmail is connected, the Dashboard and Applications pages ask you to connect it. Once connected, the AI reads job-related emails and records replies, interview invites, rejections and offers against the matching application.
- **Check for Updates.** One button scans your inbox, works out the status of every application, writes a short AI summary, and updates all records immediately. The dialog shows live progress, then the summary, each status change with the email that caused it, and the current status of every application.
- **Dashboard.** Shows totals, applications this month, active applications, interviews, offers, rejections, a status breakdown chart, applications per month, the latest inbox summary, and upcoming interviews.
- **Search, filters, sorting and pagination.** These all run in the database: search by job title or company; filter by status, location and employment type; sort by application date, last update or title.
- **API documentation.** OpenAPI 3 with Swagger UI.

## Architecture

```text
┌──────────────────────────┐
│ React + TypeScript (Vite)│  pages → components → services/api.ts (fetch + JWT)
└────────────┬─────────────┘
             │ JSON over HTTP  (Authorization: Bearer <token>)
┌────────────▼─────────────┐
│ Spring Boot REST API     │  controller → service → repository
│  • Spring Security (JWT) │  DTOs at the boundary, entities inside
│  • Bean Validation       │  @RestControllerAdvice → uniform error body
└────────────┬─────────────┘
             │ JPA / Hibernate, Flyway migrations
┌────────────▼─────────────┐
│ PostgreSQL               │
└──────────────────────────┘
```

Backend packages (`backend/src/main/java/com/jobtrack`):

| Package | Responsibility |
|---|---|
| `controller` | HTTP mapping, request validation (`@Valid`), status codes. No business logic. |
| `service` | Business rules, ownership checks, transactions. |
| `repository` | Spring Data JPA repositories, JPA Specifications for search, aggregate queries. |
| `entity` | JPA entities and enums (including the status transition rules). |
| `dto` | Request/response records exposed by the API. |
| `mapper` | Entity → DTO conversion. |
| `security` | JWT issuing, current-user resolution, JSON 401/403 responses. |
| `config` | Security, CORS, OpenAPI, clock. |
| `exception` | Exception types and the global exception handler. |

Frontend (`frontend/src`): `pages/` (routes), `components/` (reusable UI such as
`StatusBadge`, `ApplicationCard`, `Pagination`, `FilterPanel`, `Modal`, charts), `services/`
(typed API client), `hooks/` (auth context, data loading, form submission), `types/`, `utils/`.

## Technology stack

| Technology | Why |
|---|---|
| **Java 21 + Spring Boot 4** | Mature, well-understood stack for REST APIs; records keep DTOs concise. |
| **Spring Data JPA / Hibernate** | Repositories and Specifications remove boilerplate while leaving room for hand-written aggregate queries. |
| **PostgreSQL** | The data is relational (users → companies → applications → history/interviews/emails); foreign keys and check constraints enforce integrity in the database itself. |
| **Flyway** | Versioned SQL migrations; Hibernate only validates the schema (`ddl-auto=validate`). |
| **Spring Security + OAuth2 Resource Server** | Standard, well-tested JWT validation (signature, expiry, issuer) without a hand-written auth filter. |
| **Bean Validation** | Declarative input validation on DTOs, reported as field-level errors. |
| **springdoc-openapi** | Generates the OpenAPI spec and Swagger UI from the controllers. |
| **JUnit 5, Mockito, Spring Boot Test, Testcontainers** | Unit tests for business rules; integration tests against a real PostgreSQL in Docker. |
| **React + TypeScript + Vite** | Typed UI mirroring the API contract; fast dev server with an API proxy. No UI framework, so the bundle stays small. |

## Database design

```text
users ─┬─< companies ──┐
       └─< applications >┘ (company_id, ON DELETE RESTRICT)
              ├─< application_status_history
              ├─< interviews
              └─< email_events >── gmail_connections (one per user)
```

| Table | Key points |
|---|---|
| `users` | Unique lower-cased `email`, BCrypt `password_hash`, profile fields. |
| `companies` | Owned by a user. Unique `(user_id, lower(name))`, so a user can't create the same company twice. |
| `applications` | Owned by a user and linked to one of their companies. Check constraints on status, employment type and salary range (`salary_min <= salary_max`). |
| `application_status_history` | `previous_status` (null for the initial entry), `new_status`, `changed_at`. |
| `interviews` | Type, scheduled time, duration, interviewer, location/link, notes, status. |
| `gmail_connections` | One per user: Gmail address, AES-GCM encrypted refresh token, last check time and error. |
| `email_events` | Every email the AI read: category, confidence, one-line summary, and the status change it caused. Unique per Gmail message, so nothing is read twice. |

All tables have `created_at` / `updated_at` (history entries are immutable and only have `changed_at`).
Indexes follow the queries: every list is scoped to a user, so composite indexes lead with `user_id`
(`(user_id, status)`, `(user_id, application_date)`, `(user_id, updated_at)`), plus foreign-key
indexes on `company_id` and `application_id`. The schema is built by the Flyway migrations in
[`db/migration`](backend/src/main/resources/db/migration) (V3 removed the old follow-up tasks table).

**Deletion strategy**
- Deleting an **application** cascades to its history, interviews and matched emails, which have no meaning on their own.
- Deleting a **company** that still has applications is **refused with `409 Conflict`**. Cascading would silently destroy application history, and nulling the reference would leave applications without an employer. The service checks first to give a clear message, and the foreign key (`ON DELETE RESTRICT`) enforces the same rule in the database.

## Authentication

- `POST /api/auth/register` and `POST /api/auth/login` return an HS256-signed JWT (`accessToken`) with the user id as the subject. It expires after `JWT_EXPIRATION_MINUTES` (24 hours by default).
- The client sends `Authorization: Bearer <token>`. Spring Security's resource server verifies the signature, expiry and issuer on every request. There is no server session and no cookie, so CSRF protection isn't needed.
- **Ownership:** the current user id is always taken from the verified token (`CurrentUser`), never from the request. Every repository lookup is scoped by owner (`findByIdAndUserId`, `findByIdAndApplicationUserId`).
- A resource that belongs to someone else returns **404 Not Found** rather than 403, so the API never confirms that another user's id exists.
- Login failures return the same `401 Invalid email or password` whether or not the email exists. A dummy BCrypt comparison keeps the timing of the two cases similar.
- **Logout** happens on the client: the frontend discards the token. JWTs are stateless and there is no server-side revocation list; that's an accepted trade-off for this project's scope.
- **Known trade-off:** the frontend keeps the token in `localStorage`. An `httpOnly` cookie would protect it better against XSS, but would require CSRF protection.

## Business logic highlights

- **Status transitions** are defined in [`ApplicationStatus`](backend/src/main/java/com/jobtrack/entity/ApplicationStatus.java).
  - Stages can be skipped going forward (`APPLIED → INTERVIEW`), but never go backwards.
  - `ACCEPTED`, `REJECTED` and `WITHDRAWN` are final.
  - Invalid transitions return `409` with the list of allowed next statuses.
- **Status changes are transactional.** `ApplicationService.changeStatus` updates the status and inserts the history row in one transaction. If the history insert fails, the status change is rolled back; an integration test covers this.
- **The status history starts at creation.** Creating an application writes its first history entry (`null → initial status`), so the timeline is complete.
- **Status can't be changed by the edit endpoint.** `PUT /api/applications/{id}` has no status field, so every status change goes through the history.
- **The application date defaults to today** when an application moves past `SAVED` without one. A date more than a day in the future is rejected.
- **Interview rules:**
  - A `SCHEDULED` or `RESCHEDULED` interview must be in the future.
  - A `COMPLETED` interview can't be in the future.
  - New interviews can't be scheduled for a closed application.
  - An interview whose time has passed can still have its notes edited.
- **Dashboard statistics** are `COUNT` / `GROUP BY` queries, not in-memory processing.
  - "Offers" counts applications that ever reached `OFFER` according to the history, so an accepted offer still counts.
  - "This month" uses the caller's time zone (the `timezone` parameter).

More detail and the reasoning behind these choices: [docs/engineering-decisions.md](docs/engineering-decisions.md).

## AI features (local and free)

Two features use a small open-source model, **Qwen3-4B**, running on your own machine through
[Ollama](https://ollama.com). There is no API key, no per-request cost, and job pages and emails
never go to a third-party AI service.

- **Add from link.** Paste a job posting URL and the form fills itself in: title, company, location,
  employment type, salary and a short summary. Most job boards embed a schema.org `JobPosting`
  object, which is read directly (instant and exact); the AI only reads the page text when that is
  missing. Nothing is saved until you check the values and click *Add application*.
- **Gmail tracking.** Connect Gmail (read-only) from the prompt on the Dashboard or Applications
  page, or on the Profile page. Every 30 minutes, and whenever you click *Check for updates*, new
  emails are fetched; those that mention the company of one of your open applications are read by
  the AI, which decides what each one means:

  | Email | Application moves to |
  |---|---|
  | Application received | Applied |
  | Assessment / take-home / online test | Screening |
  | Interview invite or scheduling | Interview |
  | Offer | Offer |
  | Rejection, "position filled" | Rejected |
  | Job alerts, newsletters, anything else | (no change) |

  Statuses only move forward along the normal workflow, and only when the model is at least 60%
  confident. Every email the AI read is listed on its application with the AI's one-line summary and
  the status change it made, so a wrong call is easy to spot and undo by hand.
- **No reply.** An application still *Applied* or *Screening* with no news for 14 days gets a
  *No reply* badge.

How it fits together (`backend/src/main/java/com/jobtrack`):

| Package | Responsibility |
|---|---|
| `ai` | `OllamaClient` (JSON-schema constrained answers), `JobPostingParser` (schema.org data), `JobLinkImportService` |
| `gmail` | OAuth (`GmailConnectionService`, signed `OAuthStateSigner`, AES-GCM `TokenCipher` for the stored refresh token), `GmailApiClient` (plain REST, no Google SDK), `CompanyMatcher` pre-filter, `EmailClassifier`, `EmailStatusRules`, `EmailSyncService` + scheduler |

Emails are processed oldest-first on a single background worker (the local model handles one request
at a time). Each processed message id is stored, so nothing is read twice, and a failed sync doesn't
move the "read up to" mark, so it is retried. Expect roughly 10–25 seconds per job-related email on a
laptop CPU; unrelated emails are skipped before they reach the AI.

### Setting up the local AI

1. Install Ollama (Windows: `winget install Ollama.Ollama`) and start it.
2. `ollama pull qwen3:4b-instruct-2507-q4_K_M` (2.5 GB).
3. Optional: point to another model or server with `OLLAMA_MODEL` / `OLLAMA_URL` in `.env`.

### Setting up Gmail (one-time, ~10 minutes)

Gmail access needs an OAuth client of your own in Google Cloud. It is free.

1. Open <https://console.cloud.google.com>, create a project (e.g. "JobTrack").
2. *APIs & Services → Library*: enable the **Gmail API**.
3. *Google Auth Platform → Branding* (OAuth consent screen): app name "JobTrack", your email as
   support and developer contact. Audience: **External**, and add your Gmail address under
   **Test users**.
4. *Data access*: add the scope `https://www.googleapis.com/auth/gmail.readonly`.
5. *Clients → Create client*: type **Web application**, authorised redirect URI
   `http://localhost:5173/api/gmail/oauth/callback` (it goes through the Vite dev server's `/api`
   proxy, so it works whatever port the backend uses).
6. Copy the client id and secret into `.env` as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`,
   restart the backend, and click **Connect Gmail** on the Profile page.

While the app is in Google's *Testing* mode, Google expires the access after 7 days; JobTrack then
shows "reconnect" on the Profile page. Publishing the app (no verification is needed for your own
use, but Google shows an "unverified app" warning) removes that limit.

### Testing the AI against the real model

```bash
cd backend
./mvnw test -Dtest=EmailClassifierLiveTest -Dollama.live=true
```

Runs eight realistic emails (rejection, interview, job alert, assessment, offer, ...) through the
local model and checks each answer. It is skipped in the normal test run because it needs Ollama.

## Testing

```bash
cd backend
./mvnw test          # Windows: mvnw.cmd test
```

Integration tests start a PostgreSQL 16 container with Testcontainers, so **Docker must be running**.
No local database or configuration is needed.

| Kind | What is covered |
|---|---|
| Unit tests (JUnit 5 + Mockito) | Status transitions and history, application creation and update, validation rules, ownership (not-found for foreign ids), company creation, duplicate names and deletion rules, interview date rules, email status rules and the update summary, dashboard calculations (zero-filling, time zones), registration and login. |
| Integration tests (`@SpringBootTest` + MockMvc + Testcontainers) | The full acceptance workflow (register → … → dashboard → search). User A vs. user B on every resource type. Page size, page number and filters combined with pagination. Sorting. Validation and error bodies. Transaction rollback when the history insert fails. |

122 tests; all pass (plus the opt-in live AI test described under *AI features*).

## Setup

**Prerequisites:** Java 21, Node.js 20+ and Docker. Maven isn't needed because the Maven wrapper is included.
For the AI features, also Ollama (see *AI features*).

**Windows shortcut:** after step 1, `powershell -ExecutionPolicy Bypass -File .\dev.ps1` starts the
database, backend and frontend in one go. If ports 5432 or 8080 are already taken by another project,
set `DB_PORT` / `SERVER_PORT` in `.env` (and `VITE_API_PROXY_TARGET` in `frontend/.env.local`).

### 1. Configure the environment

```bash
cp .env.example .env
```

Edit `.env` and set a database password and a JWT secret. Generate a secret with
`openssl rand -base64 48`. The `.env` file is git-ignored.

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL 16 on `localhost:5432`, using the credentials from `.env`.

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The API runs on `http://localhost:8080`. Flyway creates the schema on first start. The backend
reads `../.env` automatically; real environment variables take precedence.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` and create an account. The Vite dev server proxies `/api` to the
backend. To proxy to a different backend address, set `VITE_API_PROXY_TARGET`.

For a production build, run `npm run build`; the output goes to `frontend/dist`. If the built app is
served from a different origin than the API, set `VITE_API_BASE_URL` at build time and add that
origin to `CORS_ALLOWED_ORIGINS`.

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/jobtrack` |
| `DATABASE_USERNAME` | yes | Database user (also used by `docker compose` to create it) |
| `DATABASE_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | HMAC signing key, **at least 32 bytes**; startup fails if it is missing or too short |
| `JWT_EXPIRATION_MINUTES` | no | Token lifetime, default `1440` (24 h) |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated origins allowed to call the API, default `http://localhost:5173` |

## API documentation

While the backend is running:

- **Swagger UI:** http://localhost:8080/swagger-ui.html. Click **Authorize** and paste the `accessToken` from login.
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

Endpoint overview. Everything except register and login requires a bearer token.

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account (201) and receive a token |
| POST | `/api/auth/login` | Exchange credentials for a token |
| GET / PUT | `/api/users/me` | View / update the profile |
| GET / POST | `/api/companies` | List (`?q=` name search) / create |
| GET / PUT / DELETE | `/api/companies/{id}` | Read / update / delete (409 if it has applications) |
| GET | `/api/companies/{id}/applications` | A company's applications |
| GET / POST | `/api/applications` | Search & paginate (`q`, `status`, `location`, `employmentType`, `companyId`, `sort`, `direction`, `page`, `size`) / create |
| GET / PUT / DELETE | `/api/applications/{id}` | Read / update details / delete |
| PATCH | `/api/applications/{id}/status` | Change status (409 on an invalid transition) |
| GET | `/api/applications/{id}/history` | Status history, oldest first |
| GET / POST | `/api/applications/{id}/interviews` | List / schedule interviews |
| GET | `/api/interviews` | All interviews (`scope=ALL\|UPCOMING\|PAST`) |
| PUT / DELETE | `/api/interviews/{id}` | Update / delete an interview |
| GET | `/api/dashboard` | Statistics (`timezone=Europe/London`) |
| POST | `/api/applications/import-link` | Read job details from a posting URL (nothing is saved) |
| GET | `/api/applications/{id}/emails` | Emails the AI matched to an application |
| GET / DELETE | `/api/gmail` | Gmail connection, sync status and progress / disconnect |
| GET | `/api/gmail/connect-url` | Google sign-in URL |
| GET | `/api/gmail/oauth/callback` | Called by Google after sign-in (public; uses the signed `state`) |
| POST | `/api/gmail/sync` | Check for updates (runs in the background, 202) |
| GET | `/api/gmail/report` | Result of the last check: AI summary, status changes, and every application's current status |
| GET | `/api/gmail/events` | Recent emails matched to applications |

Errors always have the same shape:

```json
{
  "timestamp": "2026-09-23T20:41:07.812Z",
  "status": 404,
  "error": "Not Found",
  "message": "Application 123 not found",
  "path": "/api/applications/123"
}
```

Validation errors (400) also include `fieldErrors: [{ "field": "jobTitle", "message": "must not be blank" }]`.
Stack traces and SQL details are never returned.

## Project structure

```text
jobtracker/
├── backend/              Spring Boot API (Maven wrapper, Flyway migrations, tests)
├── frontend/             React + TypeScript + Vite app
├── docs/                 Engineering decisions
├── docker-compose.yml    Local PostgreSQL
├── .env.example          Configuration template
└── README.md
```
