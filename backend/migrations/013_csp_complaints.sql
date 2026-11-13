-- CSP complaints written by the DC at the shop (app 0.20.0+, sync op
-- csp.complaint). Append-only like the other evidence tables.
CREATE TABLE IF NOT EXISTS csp_complaints (
  id uuid PRIMARY KEY,
  tenant_id text NOT NULL,
  dc_user_id uuid NOT NULL REFERENCES users (id),
  device_id uuid NOT NULL,
  location_id uuid NOT NULL REFERENCES locations (id),
  category text NOT NULL,
  text text NOT NULL,
  fix jsonb,
  device_wall_time timestamptz NOT NULL,
  monotonic_ms bigint,
  server_received_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS csp_complaints_tenant_time ON csp_complaints (tenant_id, device_wall_time DESC);
