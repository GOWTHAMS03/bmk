-- ============================================================
-- BusyMumKitchen - V20: Add min_order_amount to restaurants
-- Adds decimal column expected by JPA entity
-- ============================================================

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS min_order_amount DECIMAL(10,2);

