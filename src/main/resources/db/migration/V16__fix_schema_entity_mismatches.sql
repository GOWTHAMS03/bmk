-- ============================================================
-- BusyMumKitchen - V16: Fix all entity-to-schema mismatches
-- Aligns DB tables with JPA entity @Column mappings
-- ============================================================

-- ── delivery_partners ────────────────────────────────────────
-- Entity: isVerified -> @Column(name = "is_verified")
ALTER TABLE delivery_partners
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- ── orders ───────────────────────────────────────────────────
-- Entity: finalAmount -> @Column(name = "final_amount")
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS final_amount DECIMAL(10,2) NOT NULL DEFAULT 0;

-- Entity: coupon -> @JoinColumn(name = "coupon_id") FK to coupons.id
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS coupon_id UUID NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_coupon') THEN
        ALTER TABLE orders ADD CONSTRAINT fk_order_coupon
            FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL;
    END IF;
END
$$;

-- Entity: notes -> @Column(length = 500) (field name = notes, default column name)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS notes VARCHAR(500);

-- Entity: cancelledReason -> @Column(name = "cancelled_reason")
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cancelled_reason VARCHAR(500);

-- Entity: cancelledAt -> @Column(name = "cancelled_at")
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

-- ── payments ─────────────────────────────────────────────────
-- Entity: stripePaymentId -> @Column(name = "stripe_payment_id")
-- V8 created stripe_charge_id; entity expects stripe_payment_id
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS stripe_payment_id VARCHAR(200);

-- ── order_items ──────────────────────────────────────────────
-- Entity: specialRequest -> @Column(name = "special_request", length = 500)
-- V7 created special_instructions; entity expects special_request
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS special_request VARCHAR(500);

