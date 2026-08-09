CREATE TABLE IF NOT EXISTS investigation_case (
  id BIGSERIAL PRIMARY KEY,
  case_number VARCHAR(80) NOT NULL UNIQUE,
  title VARCHAR(180) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  priority VARCHAR(20) NOT NULL,
  category VARCHAR(120) NOT NULL,
  case_type VARCHAR(120) NOT NULL,
  location VARCHAR(255),
  notes VARCHAR(4000),
  lead_investigator VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE IF EXISTS investigation_case
  ADD COLUMN IF NOT EXISTS description VARCHAR(2000),
  ADD COLUMN IF NOT EXISTS priority VARCHAR(20) DEFAULT 'MEDIUM',
  ADD COLUMN IF NOT EXISTS category VARCHAR(120) DEFAULT 'General',
  ADD COLUMN IF NOT EXISTS case_type VARCHAR(120) DEFAULT 'Mobile Forensics',
  ADD COLUMN IF NOT EXISTS location VARCHAR(255),
  ADD COLUMN IF NOT EXISTS notes VARCHAR(4000),
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE investigation_case SET status = 'ON_HOLD' WHERE status = 'REVIEW';
UPDATE investigation_case SET description = COALESCE(description, 'Forensic investigation case record.');
UPDATE investigation_case SET priority = COALESCE(priority, 'MEDIUM');
UPDATE investigation_case SET category = COALESCE(category, 'General');
UPDATE investigation_case SET case_type = COALESCE(case_type, 'Mobile Forensics');
UPDATE investigation_case SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP);
UPDATE investigation_case SET updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP);

CREATE INDEX IF NOT EXISTS idx_case_number ON investigation_case(case_number);
CREATE INDEX IF NOT EXISTS idx_case_status ON investigation_case(status);
CREATE INDEX IF NOT EXISTS idx_case_priority ON investigation_case(priority);
CREATE INDEX IF NOT EXISTS idx_case_created_at ON investigation_case(created_at);

CREATE TABLE IF NOT EXISTS device (
  id BIGSERIAL PRIMARY KEY,
  device_name VARCHAR(255),
  platform VARCHAR(255),
  acquisition_status VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  investigation_case_id BIGINT REFERENCES investigation_case(id)
);

ALTER TABLE IF EXISTS device
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS evidence (
  id BIGSERIAL PRIMARY KEY,
  artifact_type VARCHAR(255),
  file_name VARCHAR(255),
  hash_sha256 VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  device_id BIGINT REFERENCES device(id)
);

ALTER TABLE IF EXISTS evidence
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS report (
  id BIGSERIAL PRIMARY KEY,
  report_number VARCHAR(255),
  status VARCHAR(255),
  generated_at TIMESTAMP,
  investigation_case_id BIGINT REFERENCES investigation_case(id)
);
