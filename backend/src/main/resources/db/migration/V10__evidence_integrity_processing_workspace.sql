-- Evidence integrity, processing jobs, investigation workspace, report snapshots and audit hardening.
-- Every statement is idempotent so it is safe on databases whose schema was partly created by Hibernate.

-- Columns that earlier milestones only created through Hibernate's ddl-auto. Declaring them here keeps a
-- fresh Flyway-only schema consistent with the entity model. These are no-ops where the columns exist.
ALTER TABLE IF EXISTS evidence
  ADD COLUMN IF NOT EXISTS title TEXT,
  ADD COLUMN IF NOT EXISTS content TEXT,
  ADD COLUMN IF NOT EXISTS source VARCHAR(255),
  ADD COLUMN IF NOT EXISTS target VARCHAR(255),
  ADD COLUMN IF NOT EXISTS phone_number VARCHAR(255),
  ADD COLUMN IF NOT EXISTS contact_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS location VARCHAR(255),
  ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS event_time TIMESTAMP;

-- Original evidence files received by the platform. Files are stored read-only and content-addressed;
-- rows are never deleted while evidence references them (foreign keys use the default NO ACTION).
CREATE TABLE IF NOT EXISTS evidence_source_file (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES investigation_case(id),
  device_id BIGINT REFERENCES device(id),
  original_file_name VARCHAR(255) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  content_type VARCHAR(120),
  source_format VARCHAR(20) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  imported_by VARCHAR(120) NOT NULL,
  imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processing_status VARCHAR(20) NOT NULL,
  integrity_status VARCHAR(20) NOT NULL DEFAULT 'NOT_VERIFIED',
  last_verified_at TIMESTAMP WITH TIME ZONE,
  last_verified_by VARCHAR(120),
  records_extracted INTEGER NOT NULL DEFAULT 0,
  duplicates_skipped INTEGER NOT NULL DEFAULT 0,
  timestamp_issues INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_evidence_file_case_hash UNIQUE (case_id, sha256)
);

CREATE INDEX IF NOT EXISTS idx_evidence_file_case ON evidence_source_file(case_id);
CREATE INDEX IF NOT EXISTS idx_evidence_file_device ON evidence_source_file(device_id);

CREATE TABLE IF NOT EXISTS processing_job (
  id BIGSERIAL PRIMARY KEY,
  job_type VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  case_id BIGINT NOT NULL REFERENCES investigation_case(id),
  device_id BIGINT REFERENCES device(id),
  evidence_file_id BIGINT REFERENCES evidence_source_file(id),
  progress INTEGER NOT NULL DEFAULT 0,
  stage VARCHAR(160),
  attempts INTEGER NOT NULL DEFAULT 0,
  error_message VARCHAR(2000),
  created_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMP WITH TIME ZONE,
  finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_processing_job_case ON processing_job(case_id);
CREATE INDEX IF NOT EXISTS idx_processing_job_status ON processing_job(status);
CREATE INDEX IF NOT EXISTS idx_processing_job_file ON processing_job(evidence_file_id);

CREATE TABLE IF NOT EXISTS processing_job_log (
  id BIGSERIAL PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES processing_job(id) ON DELETE CASCADE,
  logged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  level VARCHAR(10) NOT NULL,
  message VARCHAR(2000) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processing_job_log_job ON processing_job_log(job_id);

-- Provenance for every extracted record: which file, where in the file, and how its timestamp was read.
ALTER TABLE IF EXISTS evidence
  ADD COLUMN IF NOT EXISTS source_file_id BIGINT REFERENCES evidence_source_file(id),
  ADD COLUMN IF NOT EXISTS source_record_ref VARCHAR(255),
  ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS original_timestamp VARCHAR(120),
  ADD COLUMN IF NOT EXISTS original_timezone VARCHAR(40),
  ADD COLUMN IF NOT EXISTS timestamp_status VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_evidence_source_file ON evidence(source_file_id);
CREATE INDEX IF NOT EXISTS idx_evidence_device_record_hash ON evidence(device_id, record_hash);

-- Records imported before this migration did not keep their original timestamp text.
UPDATE evidence
SET timestamp_status = CASE WHEN event_time IS NULL THEN 'MISSING' ELSE 'LEGACY_IMPORT' END
WHERE timestamp_status IS NULL;

-- Investigation workspace.
CREATE TABLE IF NOT EXISTS evidence_bookmark (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES investigation_case(id) ON DELETE CASCADE,
  evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
  label VARCHAR(120),
  note VARCHAR(1000),
  created_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_bookmark_case_evidence UNIQUE (case_id, evidence_id)
);

CREATE TABLE IF NOT EXISTS investigation_note (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES investigation_case(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  created_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(120),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_investigation_note_case ON investigation_note(case_id);

CREATE TABLE IF NOT EXISTS investigation_finding (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES investigation_case(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  basis VARCHAR(30) NOT NULL,
  significance VARCHAR(20) NOT NULL,
  review_status VARCHAR(20) NOT NULL,
  ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
  ai_engine VARCHAR(120),
  include_in_report BOOLEAN NOT NULL DEFAULT TRUE,
  narrative_order INTEGER,
  created_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(120),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_by VARCHAR(120),
  reviewed_at TIMESTAMP WITH TIME ZONE,
  review_comment VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_investigation_finding_case ON investigation_finding(case_id);

CREATE TABLE IF NOT EXISTS investigation_finding_evidence (
  finding_id BIGINT NOT NULL REFERENCES investigation_finding(id) ON DELETE CASCADE,
  evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
  PRIMARY KEY (finding_id, evidence_id)
);

-- Reports keep the exact data snapshot they were rendered from, plus its SHA-256, so exports are reproducible.
ALTER TABLE IF EXISTS report
  ADD COLUMN IF NOT EXISTS device_id BIGINT,
  ADD COLUMN IF NOT EXISTS snapshot_json TEXT,
  ADD COLUMN IF NOT EXISTS content_sha256 VARCHAR(64),
  ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE;

-- Audit log: case/entity references and a SHA-256 hash chain for tamper evidence.
ALTER TABLE IF EXISTS audit_log
  ADD COLUMN IF NOT EXISTS case_id BIGINT,
  ADD COLUMN IF NOT EXISTS entity_type VARCHAR(60),
  ADD COLUMN IF NOT EXISTS entity_id VARCHAR(80),
  ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_log_case_id ON audit_log(case_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);

-- Audit records are append-only for every database role that goes through normal DML.
-- (A superuser can still disable the trigger; the hash chain makes such edits detectable.)
CREATE OR REPLACE FUNCTION forensight_audit_log_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_log is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_log_append_only ON audit_log;
CREATE TRIGGER trg_audit_log_append_only
  BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION forensight_audit_log_append_only();
