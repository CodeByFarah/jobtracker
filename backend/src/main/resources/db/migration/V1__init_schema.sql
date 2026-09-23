-- JobTrack initial schema.
-- Every row of job-search data hangs off a user, either directly (companies, applications)
-- or through its application (status history, interviews, tasks).

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    headline      VARCHAR(150),
    location      VARCHAR(150),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE companies (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(150) NOT NULL,
    website    VARCHAR(500),
    industry   VARCHAR(100),
    location   VARCHAR(150),
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
-- A user cannot have two companies with the same name (case-insensitive).
-- The leading user_id column also serves "list my companies" queries.
CREATE UNIQUE INDEX uq_companies_user_name ON companies (user_id, lower(name));

CREATE TABLE applications (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- RESTRICT: a company that still has applications cannot be deleted (see CompanyService).
    company_id      BIGINT       NOT NULL REFERENCES companies (id) ON DELETE RESTRICT,
    job_title       VARCHAR(150) NOT NULL,
    job_url         VARCHAR(500),
    location        VARCHAR(150),
    employment_type VARCHAR(20),
    salary_min      INTEGER,
    salary_max      INTEGER,
    salary_currency VARCHAR(3),
    application_date DATE,
    status          VARCHAR(20)  NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_applications_status CHECK (status IN
        ('SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_applications_employment_type CHECK (employment_type IN
        ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'TEMPORARY')),
    CONSTRAINT ck_applications_salary_non_negative CHECK (
        (salary_min IS NULL OR salary_min >= 0) AND (salary_max IS NULL OR salary_max >= 0)),
    CONSTRAINT ck_applications_salary_range CHECK (
        salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
);
-- Composite indexes lead with user_id because every query is scoped to the current user.
CREATE INDEX idx_applications_user_status     ON applications (user_id, status);
CREATE INDEX idx_applications_user_app_date   ON applications (user_id, application_date);
CREATE INDEX idx_applications_user_updated_at ON applications (user_id, updated_at);
CREATE INDEX idx_applications_company         ON applications (company_id);

CREATE TABLE application_status_history (
    id              BIGSERIAL   PRIMARY KEY,
    application_id  BIGINT      NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    -- NULL for the entry recorded when the application is created
    previous_status VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    changed_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_status_history_application ON application_status_history (application_id, changed_at);

CREATE TABLE interviews (
    id               BIGSERIAL    PRIMARY KEY,
    application_id   BIGINT       NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    interview_type   VARCHAR(20)  NOT NULL,
    scheduled_at     TIMESTAMPTZ  NOT NULL,
    duration_minutes INTEGER,
    interviewer_name VARCHAR(150),
    location         VARCHAR(255),
    notes            TEXT,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_interviews_type CHECK (interview_type IN
        ('RECRUITER', 'TECHNICAL', 'SYSTEM_DESIGN', 'BEHAVIORAL', 'FINAL', 'OTHER')),
    CONSTRAINT ck_interviews_status CHECK (status IN
        ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED')),
    CONSTRAINT ck_interviews_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0)
);
CREATE INDEX idx_interviews_application ON interviews (application_id);

CREATE TABLE tasks (
    id             BIGSERIAL    PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    title          VARCHAR(150) NOT NULL,
    description    TEXT,
    due_date       DATE,
    completed      BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_tasks_completed_at CHECK (completed = (completed_at IS NOT NULL))
);
CREATE INDEX idx_tasks_application ON tasks (application_id);
CREATE INDEX idx_tasks_due_date    ON tasks (due_date);
