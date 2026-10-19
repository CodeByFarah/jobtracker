-- Gmail integration: one connected mailbox per user, and a record of every email the AI read.

CREATE TABLE gmail_connections (
    id                      BIGSERIAL    PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    gmail_address           VARCHAR(255) NOT NULL,
    -- AES-GCM encrypted OAuth refresh token (see TokenCipher); never stored in plain text.
    refresh_token_encrypted TEXT         NOT NULL,
    last_synced_at          TIMESTAMPTZ,
    last_sync_error         TEXT,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_gmail_connections_user UNIQUE (user_id)
);

CREATE TABLE email_events (
    id               BIGSERIAL        PRIMARY KEY,
    user_id          BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- NULL when the AI decided the email is not about any tracked application.
    application_id   BIGINT           REFERENCES applications (id) ON DELETE CASCADE,
    gmail_message_id VARCHAR(64)      NOT NULL,
    sender           VARCHAR(320),
    subject          VARCHAR(500),
    received_at      TIMESTAMPTZ      NOT NULL,
    category         VARCHAR(30)      NOT NULL,
    confidence       DOUBLE PRECISION,
    summary          TEXT,
    -- Status before the email was processed, and the status it moved the application to (NULL = unchanged).
    status_before    VARCHAR(20),
    status_after     VARCHAR(20),
    created_at       TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_email_events_message UNIQUE (user_id, gmail_message_id),
    CONSTRAINT ck_email_events_category CHECK (category IN
        ('APPLICATION_RECEIVED', 'INTERVIEW', 'ASSESSMENT', 'OFFER', 'REJECTED', 'OTHER', 'NOT_JOB_RELATED'))
);
CREATE INDEX idx_email_events_user_received ON email_events (user_id, received_at);
CREATE INDEX idx_email_events_application   ON email_events (application_id);
