-- ============================================
-- BusyMumKitchen - V22: Add delivery/customer fields to orders
-- ============================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivery_address TEXT,
    ADD COLUMN IF NOT EXISTS customer_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_phone    VARCHAR(20);
