-- Fix the order status CHECK constraint.
-- The original V7 migration used 'READY' but the Java enum value is 'READY_FOR_PICKUP'.
-- Also add ACCEPTED which was added to the enum but never added to the constraint.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_status CHECK (status IN (
        'PLACED',
        'CONFIRMED',
        'ACCEPTED',
        'PREPARING',
        'READY_FOR_PICKUP',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'CANCELLED',
        'REFUNDED'
    ));
