-- Existing installations receive indexes for repository filtering. Fresh JPA-managed schemas
-- receive the same indexes from the Evidence entity after Flyway completes.
DO $$
BEGIN
  IF to_regclass('public.evidence') IS NOT NULL THEN
    ALTER TABLE evidence
      ADD COLUMN IF NOT EXISTS event_time TIMESTAMP;
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_evidence_event_time ON evidence (event_time)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_evidence_device_event_time ON evidence (device_id, event_time)';
  END IF;
END $$;
