-- The same physical device (IMEI) can be examined in more than one case. IMEI uniqueness is
-- therefore enforced per case instead of globally. Existing data is unaffected (global uniqueness
-- implies per-case uniqueness).

-- Remove any single-column unique constraint on device.imei (Hibernate may have created one with a generated name).
DO $$
DECLARE
  constraint_row record;
BEGIN
  IF to_regclass('public.device') IS NULL THEN
    RETURN;
  END IF;
  FOR constraint_row IN
    SELECT con.conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attname = 'imei'
    WHERE rel.relname = 'device'
      AND con.contype = 'u'
      AND con.conkey = ARRAY[att.attnum]
  LOOP
    EXECUTE format('ALTER TABLE device DROP CONSTRAINT %I', constraint_row.conname);
  END LOOP;
END $$;

DROP INDEX IF EXISTS idx_device_imei;

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_case_imei ON device (investigation_case_id, imei);
CREATE INDEX IF NOT EXISTS idx_device_imei_lookup ON device (imei);
