-- Phone battery state next to the live position (app 0.20.0+), so the live
-- map shows why a DC went quiet or a KM day came out short. Nullable: older
-- apps don't send it.
ALTER TABLE dc_live_location ADD COLUMN IF NOT EXISTS battery_pct smallint;
ALTER TABLE dc_live_location ADD COLUMN IF NOT EXISTS charging boolean;
ALTER TABLE dc_live_location ADD COLUMN IF NOT EXISTS power_save boolean;
