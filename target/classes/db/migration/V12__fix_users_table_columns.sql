-- ============================================
-- BusyMumKitchen - V12: Fix users table column names
-- Aligns DB schema with Java entity mappings
-- ============================================

-- Rename 'name' → 'full_name' to match @Column(name = "full_name")
ALTER TABLE users RENAME COLUMN name TO full_name;

-- Rename 'whatsapp_verified' → 'is_whatsapp_verified' to match @Column(name = "is_whatsapp_verified")
ALTER TABLE users RENAME COLUMN whatsapp_verified TO is_whatsapp_verified;

-- Add 'profile_image_url' column (mapped in User entity)
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

-- Increase phone_number length to 20 chars (E.164 format can be +1-14-digit = 16 chars max; Java entity uses length=20)
ALTER TABLE users ALTER COLUMN phone_number TYPE VARCHAR(20);

-- Increase whatsapp_number length to 20 chars to match entity
ALTER TABLE users ALTER COLUMN whatsapp_number TYPE VARCHAR(20);

-- Update index name to reflect renamed column
DROP INDEX IF EXISTS idx_users_name;
CREATE INDEX IF NOT EXISTS idx_users_full_name ON users(full_name);
