-- ============================================
-- BusyMumKitchen - V10: Favorite Items Table
-- ============================================

CREATE TABLE favorite_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_menuitem UNIQUE (user_id, menu_item_id)
);

CREATE INDEX idx_favorites_user ON favorite_items(user_id);
CREATE INDEX idx_favorites_menuitem ON favorite_items(menu_item_id);
