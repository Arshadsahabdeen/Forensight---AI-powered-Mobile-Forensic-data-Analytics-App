CREATE TABLE IF NOT EXISTS normalized_evidence (
  id BIGSERIAL PRIMARY KEY,
  artifact_type VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  source_tool VARCHAR(80) NOT NULL,
  device_id BIGINT NOT NULL REFERENCES device(id) ON DELETE CASCADE,
  case_id BIGINT NOT NULL REFERENCES investigation_case(id) ON DELETE CASCADE,
  source_evidence_id BIGINT UNIQUE REFERENCES evidence(id) ON DELETE CASCADE,
  title TEXT,
  content TEXT,
  metadata_json TEXT,
  event_time TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_normalized_evidence_device
  ON normalized_evidence(device_id);

CREATE INDEX IF NOT EXISTS idx_normalized_evidence_artifact_type
  ON normalized_evidence(artifact_type);

CREATE INDEX IF NOT EXISTS idx_normalized_evidence_category
  ON normalized_evidence(category);

CREATE INDEX IF NOT EXISTS idx_normalized_evidence_event_time
  ON normalized_evidence(event_time);

CREATE INDEX IF NOT EXISTS idx_normalized_evidence_case
  ON normalized_evidence(case_id);

CREATE INDEX IF NOT EXISTS idx_normalized_evidence_source_tool
  ON normalized_evidence(source_tool);
