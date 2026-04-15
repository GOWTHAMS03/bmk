-- ============================================================
-- BusyMumKitchen - V18: Add average_prep_time_mins to restaurants
-- Adds the column referenced by the Restaurant JPA entity so
-- Hibernate schema validation succeeds (keeps column nullable).
-- ============================================================

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS average_prep_time_mins INTEGER;

-- If you prefer a default for existing rows, you can instead use:
-- ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS average_prep_time_mins INTEGER DEFAULT 15;
-- UPDATE restaurants SET average_prep_time_mins = 15 WHERE average_prep_time_mins IS NULL;
-- ALTER TABLE restaurants ALTER COLUMN average_prep_time_mins DROP DEFAULT;

