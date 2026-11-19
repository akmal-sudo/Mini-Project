-- ============================================================
-- Manager DB  = schema "public"  (master catalog + stock, NO vendor data)
-- Vendor  DB  = schema "vendor"  (vendors + each vendor's own catalog/stock)
-- The two sides share no foreign keys; they only meet through the SKU code
-- and the supply-order workflow (reorder_requests).
-- ============================================================

CREATE SCHEMA IF NOT EXISTS vendor;

-- the manager side must no longer know vendors
DROP VIEW IF EXISTS v_stock_status;

ALTER TABLE vendors SET SCHEMA vendor;

CREATE TABLE vendor.vendor_catalog (
  id         BIGSERIAL PRIMARY KEY,
  vendor_id  INT NOT NULL REFERENCES vendor.vendors(vendor_id),
  sku        VARCHAR(30)  NOT NULL,
  name       VARCHAR(200) NOT NULL,
  category   VARCHAR(80),
  price      NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  quantity   INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  source     VARCHAR(20) NOT NULL DEFAULT 'OWN' CHECK (source IN ('OWN','MANAGER_CATALOG')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (vendor_id, sku)
);
CREATE INDEX idx_vendor_catalog_vendor ON vendor.vendor_catalog (vendor_id);

-- carry existing data over: each vendor keeps the products it used to supply
INSERT INTO vendor.vendor_catalog (vendor_id, sku, name, category, price, quantity)
SELECT p.vendor_id, p.sku, p.name, p.category, p.price, i.initial_quantity
FROM products p JOIN inventory i USING (sku);

-- cut every link from the manager side (and auth) to the vendor schema
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_vendor_id_fkey;
ALTER TABLE reorder_requests DROP CONSTRAINT IF EXISTS reorder_requests_vendor_id_fkey;
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_vendor_id_fkey;
DROP INDEX IF EXISTS idx_products_vendor;
ALTER TABLE products DROP COLUMN vendor_id;

-- several vendors may now order the same SKU: one open order per (vendor, SKU)
DROP INDEX IF EXISTS uq_open_reorder_per_sku;
CREATE UNIQUE INDEX uq_open_reorder_per_vendor_sku ON reorder_requests (vendor_id, sku)
  WHERE status IN ('CREATED','ACKNOWLEDGED','SHIPPED');

-- stock leaving the manager for a vendor is a ledger movement too
ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS inventory_movements_reason_check;
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_reason_check
  CHECK (reason IN ('ORDER','CANCEL','RESTOCK','ADMIN_ADJUST','VENDOR_SUPPLY'));

-- manager baseline: every SKU holds 1000 units (ledger-consistent top-up)
INSERT INTO inventory_movements (sku, delta, quantity_after, reason, event_id)
SELECT sku, 1000 - quantity, 1000, 'RESTOCK', 'V3-BASELINE' FROM inventory WHERE quantity < 1000;
UPDATE inventory SET quantity = 1000, version = version + 1 WHERE quantity < 1000;

CREATE VIEW v_stock_status AS
SELECT p.sku, p.name, p.category, i.quantity, i.reorder_threshold,
       CASE WHEN i.quantity = 0 THEN 'OUT'
            WHEN i.quantity <= i.reorder_threshold THEN 'LOW'
            ELSE 'OK' END AS status
FROM products p JOIN inventory i USING (sku)
WHERE p.active;
