ALTER TABLE IF EXISTS report
  ADD COLUMN IF NOT EXISTS report_title VARCHAR(255),
  ADD COLUMN IF NOT EXISTS report_type VARCHAR(80),
  ADD COLUMN IF NOT EXISTS version VARCHAR(40),
  ADD COLUMN IF NOT EXISTS prepared_by VARCHAR(120),
  ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(120),
  ADD COLUMN IF NOT EXISTS approved_by VARCHAR(120),
  ADD COLUMN IF NOT EXISTS generated_by VARCHAR(120),
  ADD COLUMN IF NOT EXISTS notes_html TEXT,
  ADD COLUMN IF NOT EXISTS ai_summary TEXT,
  ADD COLUMN IF NOT EXISTS timeline_summary TEXT,
  ADD COLUMN IF NOT EXISTS extracted_keywords TEXT,
  ADD COLUMN IF NOT EXISTS named_entities TEXT,
  ADD COLUMN IF NOT EXISTS suspicious_findings TEXT,
  ADD COLUMN IF NOT EXISTS risk_level VARCHAR(40),
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE report r
SET report_title = COALESCE(r.report_title, c.title || ' Investigation Report'),
    report_type = COALESCE(r.report_type, 'Investigation Report'),
    version = COALESCE(r.version, '1.0'),
    prepared_by = COALESCE(r.prepared_by, c.lead_investigator),
    reviewed_by = COALESCE(r.reviewed_by, 'Pending Review'),
    approved_by = COALESCE(r.approved_by, 'Pending Approval'),
    generated_by = COALESCE(r.generated_by, c.lead_investigator),
    notes_html = COALESCE(r.notes_html, '<p>Seeded forensic report generated from live case records.</p>'),
    ai_summary = COALESCE(r.ai_summary, 'AI Analysis Not Available Yet'),
    timeline_summary = COALESCE(r.timeline_summary, 'Timeline summary pending generation.'),
    extracted_keywords = COALESCE(r.extracted_keywords, 'wallet, messages, location, device, metadata'),
    named_entities = COALESCE(r.named_entities, 'Case participant, device, messaging app, cloud service'),
    suspicious_findings = COALESCE(r.suspicious_findings, 'Potential linkage between artifacts requires validation.'),
    risk_level = COALESCE(r.risk_level, 'Moderate')
FROM investigation_case c
WHERE r.investigation_case_id = c.id;

UPDATE report SET generated_at = COALESCE(generated_at, CURRENT_TIMESTAMP);

ALTER TABLE IF EXISTS report
  ALTER COLUMN report_title SET NOT NULL,
  ALTER COLUMN report_type SET NOT NULL,
  ALTER COLUMN version SET NOT NULL,
  ALTER COLUMN prepared_by SET NOT NULL,
  ALTER COLUMN reviewed_by SET NOT NULL,
  ALTER COLUMN approved_by SET NOT NULL,
  ALTER COLUMN generated_by SET NOT NULL,
  ALTER COLUMN notes_html SET NOT NULL,
  ALTER COLUMN ai_summary SET NOT NULL,
  ALTER COLUMN timeline_summary SET NOT NULL,
  ALTER COLUMN extracted_keywords SET NOT NULL,
  ALTER COLUMN named_entities SET NOT NULL,
  ALTER COLUMN suspicious_findings SET NOT NULL,
  ALTER COLUMN risk_level SET NOT NULL;

CREATE TABLE IF NOT EXISTS report_device_selection (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL REFERENCES report(id) ON DELETE CASCADE,
  device_id BIGINT NOT NULL REFERENCES device(id) ON DELETE CASCADE,
  selected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_report_device_selection UNIQUE (report_id, device_id)
);

CREATE TABLE IF NOT EXISTS report_evidence_selection (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL REFERENCES report(id) ON DELETE CASCADE,
  evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
  selected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_report_evidence_selection UNIQUE (report_id, evidence_id)
);

CREATE TABLE IF NOT EXISTS report_section_selection (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL REFERENCES report(id) ON DELETE CASCADE,
  section_key VARCHAR(80) NOT NULL,
  section_label VARCHAR(120) NOT NULL,
  section_order INTEGER,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT uk_report_section_selection UNIQUE (report_id, section_key)
);

CREATE INDEX IF NOT EXISTS idx_report_generated_at ON report(generated_at);
CREATE INDEX IF NOT EXISTS idx_report_status ON report(status);
CREATE INDEX IF NOT EXISTS idx_report_title ON report(report_title);