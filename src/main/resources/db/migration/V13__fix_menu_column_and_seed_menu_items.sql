-- ============================================================
-- BusyMumKitchen - V13: Fix menu_items column + seed data
-- ============================================================
-- 1. Rename preparation_time → preparation_time_mins
--    (Entity maps to preparation_time_mins; V4 created preparation_time)
-- 2. Seed 20 authentic South Indian menu items across all categories
-- ============================================================

-- ── 1. Column rename ──────────────────────────────────────
ALTER TABLE menu_items RENAME COLUMN preparation_time TO preparation_time_mins;

-- ── 2. Seed menu items ────────────────────────────────────
-- Uses sub-selects to resolve category IDs by name so this migration
-- is safe on both fresh and existing databases regardless of the UUIDs
-- that uuid_generate_v4() produced in V11.

-- ╔══════════════════╗
-- ║   BREAKFAST      ║
-- ╚══════════════════╝
INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Idly (4 pcs)',
    'Soft, pillowy steamed rice cakes made from our signature batter. Served with sambar and two fresh chutneys – coconut and tomato.',
    80.00, NULL, true, false, false, true, 20, 240, 1,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Breakfast' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Masala Dosa',
    'Golden, crispy crepe folded around a spiced potato-onion filling. Served with sambar and chutneys. A South Indian classic.',
    120.00, 99.00, true, false, false, true, 25, 350, 2,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Breakfast' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Plain Dosa',
    'Thin, lacy, crispy dosa made from fermented rice-lentil batter. Simple, light and delicious with chutneys and sambar.',
    80.00, NULL, true, true, false, true, 20, 230, 3,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Breakfast' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Rava Upma',
    'Fluffy semolina porridge tempered with mustard seeds, curry leaves, onions and green chillies. Light, filling and wholesome.',
    90.00, NULL, true, false, false, true, 20, 280, 4,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Breakfast' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Medu Vada (2 pcs)',
    'Crispy, golden doughnut-shaped lentil fritters seasoned with pepper, ginger and curry leaves. Served with sambar and chutney.',
    70.00, NULL, true, true, false, true, 15, 200, 5,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Breakfast' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

-- ╔══════════════════════╗
-- ║   LUNCH SPECIALS     ║
-- ╚══════════════════════╝
INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Mini Meals (Thali)',
    'A wholesome South Indian thali with rice, sambar, rasam, two vegetable curries, curd, papad, pickle and a dessert. Pure home taste.',
    200.00, 179.00, true, false, false, true, 30, 680, 1,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Lunch Specials' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Sambar Rice',
    'Steamed rice cooked with aromatic sambar, lentils and tender vegetables. A comforting one-pot South Indian classic.',
    130.00, NULL, true, false, true, true, 20, 380, 2,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Lunch Specials' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Lemon Rice',
    'Fluffy rice tossed with fresh lemon juice, turmeric, groundnuts and a fragrant tempering of mustard, urad dal and curry leaves.',
    100.00, NULL, true, true, true, true, 15, 290, 3,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Lunch Specials' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Curd Rice',
    'Creamy rice mixed with fresh yogurt, tempered with mustard seeds, grated ginger and pomegranate. Cooling and refreshing.',
    120.00, NULL, true, false, true, true, 10, 320, 4,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Lunch Specials' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

-- ╔══════════════╗
-- ║   DINNER     ║
-- ╚══════════════╝
INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Chapati (3 pcs) with Dal',
    'Soft whole-wheat flatbreads paired with a rich, slow-cooked yellow dal. A simple, nourishing home-style dinner.',
    140.00, NULL, true, false, false, true, 20, 390, 1,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Dinner' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Rasam Rice',
    'Light, tangy tamarind rasam with pepper and cumin served over steamed rice. The ultimate South Indian comfort food.',
    130.00, NULL, true, true, true, true, 20, 350, 2,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Dinner' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Kootu Curry with Rice',
    'Hearty mixed vegetable and lentil curry cooked with freshly grated coconut and cumin. Served with steamed rice and papad.',
    160.00, NULL, true, false, true, true, 25, 430, 3,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Dinner' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

-- ╔══════════════╗
-- ║   SNACKS     ║
-- ╚══════════════╝
INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Murukku',
    'Crunchy rice-flour spirals seasoned with sesame seeds and ajwain. Made fresh daily using our grandmother''s recipe. 150g pack.',
    60.00, NULL, true, true, false, true, 5, 280, 1,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Snacks' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Ribbon Pakoda',
    'Crispy, ribbon-shaped rice and chickpea flour snack with chilli and turmeric. A festive South Indian savoury. 150g pack.',
    70.00, NULL, true, true, false, true, 5, 310, 2,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Snacks' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Kara Boondhi',
    'Tiny, spiced chickpea flour pearls fried to crispy perfection with curry leaves and peanuts. Addictive snack mix. 150g pack.',
    60.00, NULL, true, true, false, true, 5, 260, 3,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Snacks' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

-- ╔══════════════════╗
-- ║   BEVERAGES      ║
-- ╚══════════════════╝
INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Filter Coffee',
    'Traditional South Indian filter coffee made with freshly ground beans and frothed with warm milk. Served in the classic tumbler-dabarah set.',
    40.00, NULL, true, false, true, true, 5, 80, 1,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Beverages' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Salted Buttermilk',
    'Cooling homemade spiced buttermilk with ginger, green chilli, cumin and curry leaves. The perfect digestive after a South Indian meal.',
    50.00, NULL, true, false, true, true, 5, 60, 2,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Beverages' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Mango Lassi',
    'Thick, creamy yogurt blended with Alphonso mango pulp and a hint of cardamom. Rich, sweet and refreshing.',
    80.00, NULL, true, false, true, true, 5, 180, 3,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Beverages' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

-- ╔══════════════════╗
-- ║   DESSERTS       ║
-- ╚══════════════════╝
INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Kesari Bath',
    'Saffron-hued semolina pudding cooked with generous ghee, cashews, raisins and fragrant cardamom. Melt-in-the-mouth sweetness.',
    80.00, NULL, true, false, false, true, 10, 350, 1,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Desserts' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);

INSERT INTO menu_items (name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free, is_available, preparation_time_mins, calories, sort_order, restaurant_id, category_id)
VALUES (
    'Sweet Pongal',
    'Slow-cooked creamy rice and lentil pudding sweetened with jaggery, enriched with ghee, cashews and cardamom. A festive favourite.',
    90.00, NULL, true, false, true, true, 15, 380, 2,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    (SELECT id FROM categories WHERE name = 'Desserts' AND restaurant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11' LIMIT 1)
);
