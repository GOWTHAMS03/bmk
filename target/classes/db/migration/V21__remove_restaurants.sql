-- ============================================
-- BusyMumKitchen - V21: Remove restaurants and related FKs/columns
-- ============================================

-- Drop foreign key constraints referencing restaurants
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_menuitem_restaurant') THEN
        ALTER TABLE menu_items DROP CONSTRAINT fk_menuitem_restaurant;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_category_restaurant') THEN
        ALTER TABLE categories DROP CONSTRAINT fk_category_restaurant;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_order_restaurant') THEN
        ALTER TABLE orders DROP CONSTRAINT fk_order_restaurant;
    END IF;
EXCEPTION WHEN others THEN
    -- ignore
END$$;

-- Drop restaurant_id columns if present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='menu_items' AND column_name='restaurant_id') THEN
        ALTER TABLE menu_items DROP COLUMN restaurant_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='categories' AND column_name='restaurant_id') THEN
        ALTER TABLE categories DROP COLUMN restaurant_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='restaurant_id') THEN
        ALTER TABLE orders DROP COLUMN restaurant_id;
    END IF;
EXCEPTION WHEN others THEN
    -- ignore
END$$;

-- Drop associated indexes if present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_menuitems_restaurant') THEN
        DROP INDEX idx_menuitems_restaurant;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_categories_restaurant') THEN
        DROP INDEX idx_categories_restaurant;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'idx_orders_restaurant') THEN
        DROP INDEX idx_orders_restaurant;
    END IF;
EXCEPTION WHEN others THEN
    -- ignore
END$$;

-- Drop the restaurants table
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'restaurants') THEN
        DROP TABLE restaurants CASCADE;
    END IF;
EXCEPTION WHEN others THEN
    -- ignore
END$$;

