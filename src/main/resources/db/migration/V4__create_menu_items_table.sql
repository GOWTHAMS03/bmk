-- ============================================
-- BusyMumKitchen - V4: Menu Items Table
-- ============================================

CREATE TABLE menu_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    discounted_price DECIMAL(10,2),
    image_url VARCHAR(500),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_vegetarian BOOLEAN NOT NULL DEFAULT FALSE,
    is_vegan BOOLEAN NOT NULL DEFAULT FALSE,
    is_gluten_free BOOLEAN NOT NULL DEFAULT FALSE,
    spice_level INTEGER DEFAULT 0,
    preparation_time INTEGER DEFAULT 15,
    calories INTEGER,
    stock_quantity INTEGER DEFAULT -1,
    sort_order INTEGER DEFAULT 0,
    restaurant_id UUID NOT NULL,
    category_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_menuitem_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
    CONSTRAINT fk_menuitem_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT chk_price_positive CHECK (price > 0),
    CONSTRAINT chk_spice_level CHECK (spice_level BETWEEN 0 AND 5)
);

CREATE INDEX idx_menuitems_restaurant ON menu_items(restaurant_id);
CREATE INDEX idx_menuitems_category ON menu_items(category_id);
CREATE INDEX idx_menuitems_available ON menu_items(is_available);
CREATE INDEX idx_menuitems_name ON menu_items(name);
CREATE INDEX idx_menuitems_price ON menu_items(price);
CREATE INDEX idx_menuitems_vegetarian ON menu_items(is_vegetarian);
