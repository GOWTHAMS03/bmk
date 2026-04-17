-- =====================================================
-- V24: KFC-style daily order numbering + kitchen/production management
-- =====================================================

-- 1. Daily order sequence table for KFC-style order numbers (#001, #002, ...)
CREATE TABLE daily_order_sequence (
    order_date DATE PRIMARY KEY,
    last_sequence INT NOT NULL DEFAULT 0
);

-- 2. Add daily_order_number to orders (the display number like #001)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS daily_order_number VARCHAR(10);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS prep_started_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ready_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS estimated_prep_minutes INT;

-- 3. Production queue table for kitchen management
CREATE TABLE production_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    assigned_to UUID REFERENCES users(id),
    priority INT NOT NULL DEFAULT 0,
    estimated_prep_mins INT,
    accepted_at TIMESTAMP,
    prep_started_at TIMESTAMP,
    ready_at TIMESTAMP,
    picked_up_at TIMESTAMP,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_production_queue_order UNIQUE (order_id)
);

CREATE INDEX idx_production_queue_order ON production_queue(order_id);
CREATE INDEX idx_production_queue_assigned ON production_queue(assigned_to);

-- 4. Add KITCHEN_STAFF to role column length (already VARCHAR(20), KITCHEN_STAFF is 13 chars — fits)
-- No ALTER needed for the enum column since it's stored as STRING.

-- 5. Add ACCEPTED status — the column is VARCHAR(25) with EnumType.STRING, so no DDL needed.
--    We just use the new enum value in Java code.

