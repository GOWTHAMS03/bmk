-- ============================================
-- BusyMumKitchen - V11: Seed Data
-- ============================================

-- Default admin user (phone: +919999999999)
INSERT INTO users (id, phone_number, name, email, role, is_active)
VALUES (
    uuid_generate_v4(),
    '+919999999999',
    'Admin',
    'admin@busymumkitchen.com',
    'ADMIN',
    true
);

-- Default restaurant
INSERT INTO restaurants (id, name, description, address, phone, opening_time, closing_time, is_active)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'BusyMum Kitchen - Main',
    'Delicious homemade food prepared with love. Fresh ingredients, authentic recipes.',
    '123 Kitchen Street, Food District, Chennai 600001',
    '+919888888888',
    '08:00',
    '22:00',
    true
);

-- Default categories
INSERT INTO categories (id, name, description, sort_order, is_active, restaurant_id) VALUES
    (uuid_generate_v4(), 'Breakfast', 'Start your day right with our fresh breakfast items', 1, true, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    (uuid_generate_v4(), 'Lunch Specials', 'Hearty homemade lunch combos', 2, true, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    (uuid_generate_v4(), 'Dinner', 'Wholesome dinner preparations', 3, true, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    (uuid_generate_v4(), 'Snacks', 'Quick bites and evening snacks', 4, true, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    (uuid_generate_v4(), 'Beverages', 'Fresh homemade drinks', 5, true, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    (uuid_generate_v4(), 'Desserts', 'Sweet treats made with love', 6, true, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');

-- Welcome coupon
INSERT INTO coupons (id, code, description, discount_type, discount_value, min_order_amount, max_discount_amount, usage_limit, valid_from, valid_until, is_active)
VALUES (
    uuid_generate_v4(),
    'WELCOME50',
    'Welcome offer - 50% off on your first order',
    'PERCENTAGE',
    50.00,
    200.00,
    150.00,
    1000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '365 days',
    true
);
