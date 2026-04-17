-- ============================================
-- BusyMumKitchen - V25: Admin Password Support
-- ============================================

-- Add password_hash column to support email+password login for ADMIN users
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Add full_name column alias if not already present (some migrations use 'name')
-- (safe no-op if column already exists)
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);
