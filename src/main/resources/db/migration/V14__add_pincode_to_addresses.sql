-- BusyMumKitchen - V14: add pincode column to addresses
-- Adds nullable pincode column so JPA schema validation passes

ALTER TABLE addresses
ADD COLUMN pincode VARCHAR(20);
