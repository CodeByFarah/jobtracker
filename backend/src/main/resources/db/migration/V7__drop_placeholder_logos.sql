-- Google's favicon service sends the same grey globe for sites it has no icon for. Logos that are
-- that globe aren't the company's logo: remove them so those companies show their letter tile
-- (the lookup now refuses the globe; see CompanyLogoFinder.PLACEHOLDER_IMAGES).
DELETE FROM company_logos
WHERE encode(sha256(image), 'hex') IN (
    'b6b2c1e6b1a515177baf79871c21db0f13372de333bc2763f1b45c09aabd1780',
    '493506d686597b9e91c1cb2736600ac22190762e0ced2fba848b8dd623cc4f15',
    '59bfe9bc385ad69f50793ce4a53397316d7a875a7148a63c16df9b674c6cda64');

UPDATE companies SET logo_updated_at = NULL
WHERE logo_updated_at IS NOT NULL
  AND id NOT IN (SELECT company_id FROM company_logos);
