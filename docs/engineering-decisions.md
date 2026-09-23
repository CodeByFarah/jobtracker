# Engineering decisions

Short notes on the main design choices in JobTrack and what each one trades off.

## Why PostgreSQL?

The data is a strict hierarchy of relationships. A user owns companies and applications. Each
application belongs to exactly one company and has its own status history, interviews and tasks.
A relational database models this directly and **enforces** it:

- Foreign keys make orphaned rows impossible. For example, an interview without an application can't exist.
- Unique and check constraints encode business rules the database can guarantee even if application code has a bug:
  - one company name per user;
  - valid status values;
  - `salary_min <= salary_max`;
  - `completed` is true exactly when `completed_at` is set.
- The dashboard is aggregate queries (`COUNT`, `GROUP BY status`, per-month grouping), which SQL handles in one round-trip.
- Transactions give atomic multi-row updates (see below).

A document store would push these guarantees into application code. The data here has no need for
schemaless documents or horizontal write scaling, so there is nothing to gain from one.

## Why a layered architecture?

`controller → service → repository → database`, with a clear job for each layer:

- **Controllers** translate HTTP into method calls: paths, validation, status codes. They contain no rules, so the same logic can't drift between endpoints.
- **Services** own the business rules (status transitions, ownership, company deletion, interview dates) and the transaction boundaries. They are plain classes, easy to unit test with mocked repositories.
- **Repositories** own data access: derived queries, JPA Specifications for the dynamic search, and hand-written aggregate queries.

The payoff is that a rule lives in exactly one place. The rule "a user only sees their own data"
is enforced by ownership-scoped repository methods (`findByIdAndUserId`) that every service goes
through. It isn't repeated in each controller.

Deliberately **not** done: interfaces for every service, a separate domain model beside the JPA
entities, or hexagonal ports and adapters. With one implementation of each and a single database,
those layers would add indirection without adding flexibility.

## Why DTOs instead of exposing entities?

- **The API contract is separate from the schema.** A column can be renamed or a relationship reshaped without breaking clients.
- **There are no accidental leaks.** `User.passwordHash` can never be serialized, because no response type contains it.
- **Requests only accept what the client may set.** `UpdateApplicationRequest` has no `status` field, so the status can only change through the endpoint that records history. Ids, owners and timestamps can't be mass-assigned.
- **Responses have a predictable shape.** Lazy-loaded relationships aren't accidentally triggered or half-serialized; each response returns exactly what the page needs (for example, the company name inside an application).
- **Validation sits on the request types**, so each operation carries its own rules.

`CompanyRequest` and `InterviewRequest` are shared between create and update, because both
operations accept exactly the same fields. Applications and tasks have separate create and update
types because their fields genuinely differ.

## Why keep a status history?

Overwriting `applications.status` would lose the most useful information in a job search: **how
an application progressed and when**. The history lets the user see how long each stage took, when
they last heard back, and how far an application got before a rejection.

It also makes the dashboard honest. "Offers" counts applications that *ever* reached `OFFER`, so an
offer that was later accepted or declined still counts. A current-status count couldn't do that.

Details:

- The first history entry is written when the application is created (`previous_status = NULL`), so the timeline is never missing its start.
- History rows are append-only. Their columns are `updatable = false` in the entity.
- The general edit endpoint cannot change status, so no status change can bypass the history.

## Why transactions (and where)?

A status change is two writes: update `applications.status`, and insert a
`application_status_history` row. If only the first succeeded, the current status would disagree
with the history. `ApplicationService.changeStatus` is `@Transactional`, so both writes commit
together or not at all. `StatusChangeTransactionIntegrationTest` forces the history insert to fail
against a real PostgreSQL and asserts that the status was rolled back.

The same applies to creating an application with its first history entry.

Read-only service methods use `@Transactional(readOnly = true)`. This isn't about consistency; it
is so lazy relationships can be loaded while mapping to DTOs, with `open-in-view` disabled. Single
inserts don't need more than the transaction the service method already provides, and transactions
aren't added in controllers or repositories.

## Other choices worth knowing

- **404 instead of 403 for other users' data.** Every lookup is scoped by owner, so "exists but isn't yours" and "doesn't exist" are the same case. This avoids revealing which ids exist.
- **Company deletion is refused while applications exist (409).** Cascading would silently delete the user's history. Setting `company_id` to null would break the "every application has an employer" invariant. The foreign key is `ON DELETE RESTRICT` as a backstop.
- **The status workflow is a small state machine in the enum.**
  - Forward moves can skip stages, because real processes do: a referral can go straight to interview.
  - Moving backwards isn't allowed.
  - `REJECTED` / `WITHDRAWN` can be reached from any open stage.
  - `ACCEPTED`, `REJECTED` and `WITHDRAWN` are final.
- **Search, filtering and pagination run in SQL.** A JPA `Specification` builds the `WHERE` clause from the optional filters. Sort fields are whitelisted by an enum. `@EntityGraph` loads the company in the same query to avoid N+1 selects. User-typed `%` and `_` are escaped before being used in `LIKE`.
- **The clock is injected.** Services read the time from an injected `Clock`, so rules like "this month", "upcoming" and "overdue" are deterministic in tests. The dashboard accepts the caller's time zone, so "this month" means the user's month rather than the server's.
- **Flyway owns the schema.** Hibernate only validates the mappings (`ddl-auto=validate`), so the schema is reviewed SQL rather than generated DDL.
- **Stateless JWT with no revocation list.** Logout discards the token on the client. Adding server-side revocation (or short-lived access tokens plus refresh tokens) is the natural next step if the app needed it; it isn't implemented.
