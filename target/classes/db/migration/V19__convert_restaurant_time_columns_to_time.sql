-- ============================================================
-- BusyMumKitchen - V19: Convert opening_time/closing_time to TIME
-- Ensures the columns match JPA entity LocalTime mappings
-- ============================================================

-- Backfill invalid/missing values to safe defaults (HH:MM or HH:MM:SS)
UPDATE restaurants
SET opening_time = '09:00'
WHERE opening_time IS NULL OR NOT (opening_time ~ '^\\d{2}:\\d{2}(:\\d{2})?$');

UPDATE restaurants
SET closing_time = '21:00'
WHERE closing_time IS NULL OR NOT (closing_time ~ '^\\d{2}:\\d{2}(:\\d{2})?$');

-- Remove existing VARCHAR defaults so PostgreSQL can alter the column type
ALTER TABLE restaurants
    ALTER COLUMN opening_time DROP DEFAULT,
    ALTER COLUMN closing_time DROP DEFAULT;

-- Alter columns to TIME using a safe cast
ALTER TABLE restaurants
    ALTER COLUMN opening_time TYPE TIME USING opening_time::time,
    ALTER COLUMN closing_time TYPE TIME USING closing_time::time;

-- Set sensible defaults at the schema level (as TIME values)
ALTER TABLE restaurants
    ALTER COLUMN opening_time SET DEFAULT '09:00'::time,
    ALTER COLUMN closing_time SET DEFAULT '21:00'::time;

