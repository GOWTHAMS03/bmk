-- ============================================================
-- BusyMumKitchen - V17: Fix delivery_partners total_earnings type
-- Entity uses Double -> Hibernate expects float(53) / DOUBLE PRECISION
-- V9 created it as DECIMAL(10,2) which is numeric
-- ============================================================

ALTER TABLE delivery_partners
    ALTER COLUMN total_earnings TYPE DOUBLE PRECISION;

