-- ============ CORE ============
CREATE TABLE vendors (
  vendor_id      SERIAL PRIMARY KEY,
  name           VARCHAR(100) NOT NULL UNIQUE,
  contact_email  VARCHAR(100),
  phone          VARCHAR(20),
  lead_time_days INT NOT NULL DEFAULT 3 CHECK (lead_time_days >= 0),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
  user_id              SERIAL PRIMARY KEY,
  username             VARCHAR(50)  NOT NULL UNIQUE,
  email                VARCHAR(100) NOT NULL UNIQUE,
  password_hash        VARCHAR(100) NOT NULL,
  role                 VARCHAR(20)  NOT NULL
                       CHECK (role IN ('ADMIN','WAREHOUSE_MANAGER','VENDOR')),
  vendor_id            INT REFERENCES vendors(vendor_id),
  enabled              BOOLEAN NOT NULL DEFAULT TRUE,
  failed_attempts      INT NOT NULL DEFAULT 0,
  locked_until         TIMESTAMPTZ,
  must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login           TIMESTAMPTZ,
  CONSTRAINT chk_vendor_role CHECK (
    (role = 'VENDOR' AND vendor_id IS NOT NULL) OR
    (role <> 'VENDOR' AND vendor_id IS NULL))
);

CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  token_hash VARCHAR(100) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE products (
  sku        VARCHAR(30)  PRIMARY KEY,
  name       VARCHAR(200) NOT NULL,
  category   VARCHAR(80),
  price      NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  vendor_id  INT NOT NULL REFERENCES vendors(vendor_id),
  active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
  sku               VARCHAR(30) PRIMARY KEY REFERENCES products(sku),
  quantity          INT NOT NULL CHECK (quantity >= 0),
  reorder_threshold INT NOT NULL CHECK (reorder_threshold >= 0),
  reorder_quantity  INT NOT NULL CHECK (reorder_quantity > 0),
  initial_quantity  INT NOT NULL CHECK (initial_quantity >= 0),
  version           BIGINT NOT NULL DEFAULT 0,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- append-only stock ledger: every stock change writes one row in the SAME transaction
CREATE TABLE inventory_movements (
  id             BIGSERIAL PRIMARY KEY,
  sku            VARCHAR(30) NOT NULL REFERENCES products(sku),
  delta          INT NOT NULL CHECK (delta <> 0),
  quantity_after INT NOT NULL CHECK (quantity_after >= 0),
  reason         VARCHAR(20) NOT NULL
                 CHECK (reason IN ('ORDER','CANCEL','RESTOCK','ADMIN_ADJUST')),
  event_id       VARCHAR(64),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ EVENTS (no FKs on purpose) ============
CREATE TABLE orders (
  order_id       BIGSERIAL PRIMARY KEY,
  event_id       VARCHAR(64) NOT NULL UNIQUE,
  sku            VARCHAR(30) NOT NULL,
  quantity       INT NOT NULL CHECK (quantity > 0),
  status         VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','PROCESSING','COMPLETED','REJECTED','FAILED')),
  failure_reason VARCHAR(200),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at   TIMESTAMPTZ,
  processed_by   VARCHAR(50),
  CONSTRAINT chk_reason CHECK (status NOT IN ('REJECTED','FAILED') OR failure_reason IS NOT NULL)
);

CREATE TABLE event_logs (
  log_id      BIGSERIAL PRIMARY KEY,
  event_id    VARCHAR(64),
  event_type  VARCHAR(20),
  sku         VARCHAR(30),
  level       VARCHAR(10) NOT NULL CHECK (level IN ('INFO','WARN','ERROR')),
  message     TEXT NOT NULL,
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE failed_events (
  id            BIGSERIAL PRIMARY KEY,
  event_id      VARCHAR(64),
  payload       JSONB,
  error         TEXT,
  attempts      INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  status        VARCHAR(15) NOT NULL DEFAULT 'PENDING_RETRY'
                CHECK (status IN ('PENDING_RETRY','DEAD')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE log_patterns (
  id            SERIAL PRIMARY KEY,
  name          VARCHAR(30) NOT NULL UNIQUE,
  pattern_text  VARCHAR(100) NOT NULL,
  severity_hint VARCHAR(10) CHECK (severity_hint IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  enabled       BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============ ALERTS & REORDERS ============
CREATE TABLE alerts (
  alert_id           BIGSERIAL PRIMARY KEY,
  sku                VARCHAR(30) NOT NULL REFERENCES products(sku),
  pattern            VARCHAR(30) NOT NULL,
  severity           VARCHAR(10) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  message            TEXT,
  status             VARCHAR(15) NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
  source_ingested_at TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  acknowledged_by    INT REFERENCES users(user_id),
  acknowledged_at    TIMESTAMPTZ,
  resolved_at        TIMESTAMPTZ
);

CREATE TABLE reorder_requests (
  reorder_id     BIGSERIAL PRIMARY KEY,
  sku            VARCHAR(30) NOT NULL REFERENCES products(sku),
  vendor_id      INT NOT NULL REFERENCES vendors(vendor_id),
  quantity       INT NOT NULL CHECK (quantity > 0),
  priority_score NUMERIC(8,2) NOT NULL,
  severity       VARCHAR(10),
  status         VARCHAR(15) NOT NULL DEFAULT 'CREATED'
                 CHECK (status IN ('CREATED','ACKNOWLEDGED','SHIPPED','DELIVERED','CANCELLED')),
  alert_id       BIGINT REFERENCES alerts(alert_id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  delivered_at   TIMESTAMPTZ
);

CREATE TABLE reorder_status_history (
  id         BIGSERIAL PRIMARY KEY,
  reorder_id BIGINT NOT NULL REFERENCES reorder_requests(reorder_id),
  old_status VARCHAR(15),
  new_status VARCHAR(15) NOT NULL,
  changed_by INT REFERENCES users(user_id),
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
  id         BIGSERIAL PRIMARY KEY,
  user_id    INT,
  action     VARCHAR(60) NOT NULL,
  entity     VARCHAR(40),
  entity_id  VARCHAR(60),
  details    JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ INDEXES ============
CREATE INDEX idx_products_category   ON products (category);
CREATE INDEX idx_products_name_lower ON products (lower(name));
CREATE INDEX idx_products_vendor     ON products (vendor_id);
CREATE INDEX idx_inventory_low       ON inventory (sku) WHERE quantity <= reorder_threshold;
CREATE INDEX idx_movements_sku_time  ON inventory_movements (sku, created_at);
CREATE INDEX idx_orders_status       ON orders (status);
CREATE INDEX idx_orders_sku_time     ON orders (sku, created_at);
CREATE INDEX idx_logs_sku_time       ON event_logs (sku, created_at);
CREATE INDEX idx_logs_time           ON event_logs (created_at);
CREATE INDEX idx_logs_type           ON event_logs (event_type);
CREATE INDEX idx_failed_retry        ON failed_events (status, next_retry_at);
CREATE INDEX idx_alerts_status_sev   ON alerts (status, severity);
CREATE INDEX idx_reorders_vendor     ON reorder_requests (vendor_id, status);
CREATE INDEX idx_audit_entity        ON audit_log (entity, entity_id);
CREATE INDEX idx_audit_time          ON audit_log (created_at);

-- at most ONE open reorder per SKU (AC-05)
CREATE UNIQUE INDEX uq_open_reorder_per_sku ON reorder_requests (sku)
  WHERE status IN ('CREATED','ACKNOWLEDGED','SHIPPED');
-- at most ONE active alert per SKU + pattern (no alert spam)
CREATE UNIQUE INDEX uq_active_alert ON alerts (sku, pattern)
  WHERE status IN ('OPEN','ACKNOWLEDGED');

-- ============ VIEWS ============
CREATE VIEW v_stock_status AS
SELECT p.sku, p.name, p.category, p.vendor_id, i.quantity, i.reorder_threshold,
       CASE WHEN i.quantity = 0 THEN 'OUT'
            WHEN i.quantity <= i.reorder_threshold THEN 'LOW'
            ELSE 'OK' END AS status
FROM products p JOIN inventory i USING (sku)
WHERE p.active;

-- empty result = consistent; any row = a violation (backs the consistency check)
CREATE VIEW v_inventory_consistency AS
SELECT i.sku, i.quantity AS actual,
       i.initial_quantity + COALESCE(SUM(m.delta), 0) AS expected
FROM inventory i LEFT JOIN inventory_movements m USING (sku)
GROUP BY i.sku, i.quantity, i.initial_quantity
HAVING i.quantity <> i.initial_quantity + COALESCE(SUM(m.delta), 0);

-- ============ TRIGGERS ============
CREATE FUNCTION fn_touch_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE FUNCTION fn_reorder_touch() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_touch BEFORE UPDATE ON inventory
  FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
CREATE TRIGGER trg_reorder_touch BEFORE UPDATE ON reorder_requests
  FOR EACH ROW EXECUTE FUNCTION fn_reorder_touch();

-- ledger, history and audit can never be edited or deleted (NFR-20)
CREATE FUNCTION fn_append_only() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION '% is append-only', TG_TABLE_NAME; END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movements_ao BEFORE UPDATE OR DELETE ON inventory_movements
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();
CREATE TRIGGER trg_history_ao BEFORE UPDATE OR DELETE ON reorder_status_history
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();
CREATE TRIGGER trg_audit_ao BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- only legal reorder transitions are allowed, enforced inside the database
CREATE FUNCTION fn_reorder_guard() RETURNS trigger AS $$
BEGIN
  IF NEW.status <> OLD.status AND NOT (
       (OLD.status = 'CREATED'      AND NEW.status IN ('ACKNOWLEDGED','CANCELLED')) OR
       (OLD.status = 'ACKNOWLEDGED' AND NEW.status IN ('SHIPPED','CANCELLED')) OR
       (OLD.status = 'SHIPPED'      AND NEW.status IN ('DELIVERED','CANCELLED')))
  THEN RAISE EXCEPTION 'Illegal reorder transition % -> %', OLD.status, NEW.status;
  END IF;
  RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reorder_guard BEFORE UPDATE ON reorder_requests
  FOR EACH ROW EXECUTE FUNCTION fn_reorder_guard();
