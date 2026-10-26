-- Logo lookup got better at formal company names ("... Sdn Bhd", "... Group") and at recruiting-site
-- links (ocbc.wd102.myworkdayjobs.com). Companies that have no logo yet get looked at once more.
UPDATE companies SET logo_checked_at = NULL WHERE logo_updated_at IS NULL;
