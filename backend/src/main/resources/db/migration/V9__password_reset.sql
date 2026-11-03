-- Changing and resetting passwords.

-- When the password last changed. Access tokens issued before this moment stop working, so a
-- password change or reset signs out every other device.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

-- One-time "forgot password" links. Only a SHA-256 hash of the token is stored, so a leaked
-- database can't be used to reset anyone's password.
CREATE TABLE password_reset_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
