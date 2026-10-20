-- Company logos, found automatically from the company's website and stored so they always load.

ALTER TABLE companies
    -- When the stored logo last changed (NULL = no logo); the frontend uses it to refresh its copy.
    ADD COLUMN logo_updated_at TIMESTAMPTZ,
    -- When JobTrack last looked for a logo (NULL = never), so a company is only searched once.
    ADD COLUMN logo_checked_at TIMESTAMPTZ;

CREATE TABLE company_logos (
    company_id   BIGINT        PRIMARY KEY REFERENCES companies (id) ON DELETE CASCADE,
    content_type VARCHAR(100)  NOT NULL,
    image        BYTEA         NOT NULL,
    -- Where the image was downloaded from.
    source_url   VARCHAR(1000),
    updated_at   TIMESTAMPTZ   NOT NULL
);
