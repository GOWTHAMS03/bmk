-- ============================================
-- BusyMumKitchen - V15: Fix Coupons Table Columns
-- Rename max_discount_amount -> max_discount
-- Add missing per_user_limit column
-- ============================================

-- Rename max_discount_amount to max_discount (entity uses max_discount)
ALTER TABLE coupons RENAME COLUMN max_discount_amount TO max_discount;

-- Add per_user_limit column (entity field: perUserLimit, default 1)
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS per_user_limit INTEGER DEFAULT 1;

