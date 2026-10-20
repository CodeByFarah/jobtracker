-- Gmail checks used to read only emails that named a company the user already tracked. They now read
-- every job-related email (and add applications the user hadn't entered), so start each mailbox over:
-- the next check re-reads the initial window. Emails already recorded are still skipped.
UPDATE gmail_connections SET last_synced_at = NULL;
