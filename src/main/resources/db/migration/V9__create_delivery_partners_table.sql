-- ============================================
-- BusyMumKitchen - V9: Delivery Partners Table
-- ============================================

CREATE TABLE delivery_partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE,
    vehicle_type VARCHAR(50),
    vehicle_number VARCHAR(30),
    license_number VARCHAR(50),
    is_available BOOLEAN NOT NULL DEFAULT FALSE,
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    rating DOUBLE PRECISION DEFAULT 0.0,
    total_deliveries INTEGER NOT NULL DEFAULT 0,
    total_earnings DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_delivery_user ON delivery_partners(user_id);
CREATE INDEX idx_delivery_available ON delivery_partners(is_available);
CREATE INDEX idx_delivery_location ON delivery_partners(current_latitude, current_longitude);
