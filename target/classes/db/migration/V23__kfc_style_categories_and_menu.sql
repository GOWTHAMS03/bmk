-- ============================================
-- BusyMumKitchen - V23: KFC-style categories & menu items
-- ============================================

-- Replace existing menu items and categories with KFC-style ones
DELETE FROM order_items;
DELETE FROM menu_items;
DELETE FROM categories;

-- ── Categories ──────────────────────────────────────────────
INSERT INTO categories (id, name, description, sort_order, is_active) VALUES
    (uuid_generate_v4(), 'Chicken',  'Crispy, grilled and saucy chicken favourites',          1, true),
    (uuid_generate_v4(), 'Burgers',  'Juicy burgers stacked with fresh toppings',              2, true),
    (uuid_generate_v4(), 'Combos',   'Value meal combos for bigger appetites',                 3, true),
    (uuid_generate_v4(), 'Sides',    'Fries, coleslaw and all the good stuff on the side',     4, true),
    (uuid_generate_v4(), 'Drinks',   'Cold beverages to wash it all down',                     5, true),
    (uuid_generate_v4(), 'Desserts', 'Sweet endings to a perfect meal',                        6, true);

-- ── Chicken ─────────────────────────────────────────────────
INSERT INTO menu_items (id, name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free,
                        is_available, preparation_time_mins, calories, sort_order, category_id)
VALUES
    (uuid_generate_v4(),
     'Original Recipe Chicken (2 pcs)',
     'Two pieces of our classic pressure-fried chicken with the original 11 herbs & spices blend. Crispy outside, juicy inside.',
     8.99, NULL, false, false, false, true, 12, 490, 1,
     (SELECT id FROM categories WHERE name = 'Chicken' LIMIT 1)),

    (uuid_generate_v4(),
     'Crispy Fried Chicken (3 pcs)',
     'Three pieces of golden, crunchy fried chicken seasoned to perfection. A fan favourite.',
     10.99, 9.49, false, false, false, true, 15, 680, 2,
     (SELECT id FROM categories WHERE name = 'Chicken' LIMIT 1)),

    (uuid_generate_v4(),
     'Spicy Zinger Strips (5 pcs)',
     'Five crispy chicken tenders with a fiery spice coating. Served with dipping sauce.',
     7.49, NULL, false, false, false, true, 10, 420, 3,
     (SELECT id FROM categories WHERE name = 'Chicken' LIMIT 1)),

    (uuid_generate_v4(),
     'Grilled Chicken (2 pcs)',
     'Two pieces of flame-grilled chicken marinated in herbs. A lighter, healthier option.',
     9.49, NULL, false, false, true, true, 18, 380, 4,
     (SELECT id FROM categories WHERE name = 'Chicken' LIMIT 1)),

    (uuid_generate_v4(),
     'Hot Wings (6 pcs)',
     'Six fiery buffalo-style wings tossed in our signature hot sauce. Perfect for sharing.',
     8.49, 6.99, false, false, false, true, 12, 540, 5,
     (SELECT id FROM categories WHERE name = 'Chicken' LIMIT 1)),

    (uuid_generate_v4(),
     'Popcorn Chicken (Regular)',
     'Bite-sized crispy chicken nuggets with a light, seasoned coating. Great as a snack.',
     5.49, NULL, false, false, false, true, 8, 310, 6,
     (SELECT id FROM categories WHERE name = 'Chicken' LIMIT 1));

-- ── Burgers ─────────────────────────────────────────────────
INSERT INTO menu_items (id, name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free,
                        is_available, preparation_time_mins, calories, sort_order, category_id)
VALUES
    (uuid_generate_v4(),
     'Zinger Burger',
     'The legendary spicy chicken fillet burger with lettuce, mayo and our signature Zinger sauce in a toasted sesame bun.',
     6.99, 5.99, false, false, false, true, 8, 520, 1,
     (SELECT id FROM categories WHERE name = 'Burgers' LIMIT 1)),

    (uuid_generate_v4(),
     'Classic Chicken Burger',
     'A crispy chicken fillet, lettuce and creamy mayo in a soft toasted bun. Simple and delicious.',
     5.99, NULL, false, false, false, true, 8, 460, 2,
     (SELECT id FROM categories WHERE name = 'Burgers' LIMIT 1)),

    (uuid_generate_v4(),
     'Bacon & Cheese Stacker',
     'Double crispy chicken, streaky bacon, cheddar cheese, pickles and BBQ sauce. The ultimate indulgence.',
     8.99, NULL, false, false, false, true, 10, 720, 3,
     (SELECT id FROM categories WHERE name = 'Burgers' LIMIT 1)),

    (uuid_generate_v4(),
     'Veggie Burger',
     'A crispy veggie patty with fresh lettuce, tomato, red onion and garlic mayo. Satisfying and meat-free.',
     5.49, NULL, true, false, false, true, 8, 390, 4,
     (SELECT id FROM categories WHERE name = 'Burgers' LIMIT 1)),

    (uuid_generate_v4(),
     'Double Crunch Burger',
     'Two layers of extra-crispy fried chicken, coleslaw and honey mustard. Crunch in every bite.',
     9.49, 7.99, false, false, false, true, 10, 810, 5,
     (SELECT id FROM categories WHERE name = 'Burgers' LIMIT 1));

-- ── Combos ──────────────────────────────────────────────────
INSERT INTO menu_items (id, name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free,
                        is_available, preparation_time_mins, calories, sort_order, category_id)
VALUES
    (uuid_generate_v4(),
     'Zinger Meal',
     'Zinger Burger + Regular Fries + Regular Drink. Best value for a quick meal.',
     10.99, 9.49, false, false, false, true, 10, 870, 1,
     (SELECT id FROM categories WHERE name = 'Combos' LIMIT 1)),

    (uuid_generate_v4(),
     'Bucket for 2',
     '4 pieces of Original Recipe Chicken + 2 Regular Fries + 2 Medium Drinks. Perfect for sharing.',
     21.99, 18.99, false, false, false, true, 15, 1750, 2,
     (SELECT id FROM categories WHERE name = 'Combos' LIMIT 1)),

    (uuid_generate_v4(),
     'Family Bucket (8 pcs)',
     '8 pieces of mixed Original Recipe Chicken + 4 Regular Fries + 4 Regular Drinks. Feeds the whole family.',
     39.99, 34.99, false, false, false, true, 20, 3400, 3,
     (SELECT id FROM categories WHERE name = 'Combos' LIMIT 1)),

    (uuid_generate_v4(),
     '3-Piece Meal',
     '3 pieces of crispy fried chicken with a side of mashed potato, coleslaw and a regular drink.',
     14.99, 12.99, false, false, false, true, 15, 1200, 4,
     (SELECT id FROM categories WHERE name = 'Combos' LIMIT 1)),

    (uuid_generate_v4(),
     'Zinger Tower Box Meal',
     'Zinger Tower Burger + Large Fries + Large Drink + Side. The ultimate KFC experience.',
     13.99, NULL, false, false, false, true, 12, 1350, 5,
     (SELECT id FROM categories WHERE name = 'Combos' LIMIT 1));

-- ── Sides ────────────────────────────────────────────────────
INSERT INTO menu_items (id, name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free,
                        is_available, preparation_time_mins, calories, sort_order, category_id)
VALUES
    (uuid_generate_v4(),
     'Regular Fries',
     'Golden, crispy fries lightly salted, served piping hot.',
     2.49, NULL, true, true, true, true, 5, 230, 1,
     (SELECT id FROM categories WHERE name = 'Sides' LIMIT 1)),

    (uuid_generate_v4(),
     'Large Fries',
     'Extra-large portion of our golden crispy fries. Perfectly salted.',
     3.49, NULL, true, true, true, true, 5, 390, 2,
     (SELECT id FROM categories WHERE name = 'Sides' LIMIT 1)),

    (uuid_generate_v4(),
     'Gravy Fries',
     'Our crispy fries smothered in rich, peppery chicken gravy. Upgraded comfort food.',
     3.99, NULL, false, false, true, true, 6, 420, 3,
     (SELECT id FROM categories WHERE name = 'Sides' LIMIT 1)),

    (uuid_generate_v4(),
     'Creamy Coleslaw',
     'Freshly made creamy coleslaw with crunchy cabbage and carrots. A classic KFC side.',
     2.49, NULL, true, false, true, true, 2, 150, 4,
     (SELECT id FROM categories WHERE name = 'Sides' LIMIT 1)),

    (uuid_generate_v4(),
     'Mashed Potato & Gravy',
     'Smooth, creamy mashed potato topped with our signature chicken gravy.',
     3.49, NULL, false, false, true, true, 5, 260, 5,
     (SELECT id FROM categories WHERE name = 'Sides' LIMIT 1)),

    (uuid_generate_v4(),
     'Corn on the Cob',
     'Sweet, tender corn on the cob grilled to perfection.',
     2.49, NULL, true, true, true, true, 6, 140, 6,
     (SELECT id FROM categories WHERE name = 'Sides' LIMIT 1));

-- ── Drinks ──────────────────────────────────────────────────
INSERT INTO menu_items (id, name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free,
                        is_available, preparation_time_mins, calories, sort_order, category_id)
VALUES
    (uuid_generate_v4(),
     'Regular Pepsi',
     'Ice-cold Pepsi in a regular cup. The perfect companion to your chicken.',
     1.99, NULL, true, true, true, true, 1, 150, 1,
     (SELECT id FROM categories WHERE name = 'Drinks' LIMIT 1)),

    (uuid_generate_v4(),
     'Large Pepsi',
     'Large ice-cold Pepsi. Stay refreshed with every sip.',
     2.49, NULL, true, true, true, true, 1, 250, 2,
     (SELECT id FROM categories WHERE name = 'Drinks' LIMIT 1)),

    (uuid_generate_v4(),
     '7UP (Regular)',
     'Crisp and refreshing lemon-lime soda, served ice cold.',
     1.99, NULL, true, true, true, true, 1, 140, 3,
     (SELECT id FROM categories WHERE name = 'Drinks' LIMIT 1)),

    (uuid_generate_v4(),
     'Bottled Water',
     'Chilled still mineral water. Pure and refreshing.',
     1.49, NULL, true, true, true, true, 1, 0, 4,
     (SELECT id FROM categories WHERE name = 'Drinks' LIMIT 1)),

    (uuid_generate_v4(),
     'Krushers Oreo Shake',
     'A thick, creamy Oreo blended shake. The sweetest way to end your meal.',
     4.99, 3.99, true, false, false, true, 3, 540, 5,
     (SELECT id FROM categories WHERE name = 'Drinks' LIMIT 1));

-- ── Desserts ─────────────────────────────────────────────────
INSERT INTO menu_items (id, name, description, price, discounted_price, is_vegetarian, is_vegan, is_gluten_free,
                        is_available, preparation_time_mins, calories, sort_order, category_id)
VALUES
    (uuid_generate_v4(),
     'Soft Serve (Original)',
     'Smooth, creamy original soft serve ice cream in a cone. A KFC classic.',
     1.99, NULL, true, false, true, true, 2, 160, 1,
     (SELECT id FROM categories WHERE name = 'Desserts' LIMIT 1)),

    (uuid_generate_v4(),
     'Chocolate Brownie',
     'Warm, fudgy chocolate brownie served fresh. Pure chocolatey goodness.',
     3.49, NULL, true, false, false, true, 5, 340, 2,
     (SELECT id FROM categories WHERE name = 'Desserts' LIMIT 1)),

    (uuid_generate_v4(),
     'Georgia Peach Pie',
     'Warm, gooey peach pie filling in a crispy shell. A Southern-inspired favourite.',
     2.99, NULL, true, false, false, true, 5, 280, 3,
     (SELECT id FROM categories WHERE name = 'Desserts' LIMIT 1));

-- ── Welcome coupon (ensure it exists) ────────────────────────
INSERT INTO coupons (id, code, description, discount_type, discount_value, min_order_amount, max_discount_amount,
                     usage_limit, valid_from, valid_until, is_active)
SELECT uuid_generate_v4(), 'WELCOME10', '10% off on your first order', 'PERCENTAGE', 10.00, 5.00, 20.00,
       10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '730 days', true
WHERE NOT EXISTS (SELECT 1 FROM coupons WHERE code = 'WELCOME10');

INSERT INTO coupons (id, code, description, discount_type, discount_value, min_order_amount, max_discount_amount,
                     usage_limit, valid_from, valid_until, is_active)
SELECT uuid_generate_v4(), 'COMBO20', '20% off on any Combo meal', 'PERCENTAGE', 20.00, 10.00, 15.00,
       5000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '365 days', true
WHERE NOT EXISTS (SELECT 1 FROM coupons WHERE code = 'COMBO20');
