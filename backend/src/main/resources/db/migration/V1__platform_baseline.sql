-- Baseline migration for the forensic platform schema.
-- JPA creates milestone entities during local development; later milestones should move full DDL here.
CREATE TABLE IF NOT EXISTS flyway_milestone_marker (
  id BIGSERIAL PRIMARY KEY,
  marker VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
