-- A Gmail mailbox belongs to exactly one JobTrack account (each account already has at most one
-- mailbox: uq_gmail_connections_user). Without this, two accounts could track the same inbox.
CREATE UNIQUE INDEX uq_gmail_connections_address ON gmail_connections (lower(gmail_address));

-- Sync state lives in the database instead of one server's memory, so several backend instances
-- can run side by side: a lease stops two of them checking the same mailbox at once, and every
-- instance can show the progress and the last report.
ALTER TABLE gmail_connections
    ADD COLUMN sync_lease_until TIMESTAMPTZ,
    ADD COLUMN sync_done        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN sync_total       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_sync_report TEXT;
