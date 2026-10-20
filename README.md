# Forensight — Mobile Forensic Evidence Intelligence Platform

Forensight ingests mobile extraction files, preserves the originals with SHA-256 integrity checks, and turns their records into searchable, correlated, time-ordered evidence. Investigators curate that evidence into reviewed findings and reproducible reports.

> Correlations and AI-assisted output show how records relate. They do not establish identity, intent or wrongdoing. Hash verification shows that stored files are unchanged since import within this platform; on its own it does not establish legal admissibility.

## Features

| Area | What it does |
|---|---|
| **Evidence intake** | Upload `.xml`, `.ufdr` or `.zip` files to a case. The file type is checked from its content, not just its extension. SHA-256 is computed while streaming to read-only, content-addressed storage. Identical re-uploads are rejected. XML is parsed with XXE protection. Archives are read without extracting to disk, with size and entry limits. |
| **Processing monitor** | Imports run as background jobs with progress, a log and retry. Each import is a single all-or-nothing transaction, and duplicate records are skipped per device. Jobs interrupted by a server restart are marked for retry. |
| **Integrity & chain of custody** | Re-verify any file's hash on demand. Every receive, import, verify, download, view and export is written to an **append-only** audit log (a PostgreSQL trigger blocks UPDATE and DELETE). Each audit row is **SHA-256 hash-chained** to the previous one, and admins can verify the chain. |
| **Provenance** | Every record keeps its source file, its XPath-like location in that file, a record hash, the original timestamp text and the declared timezone. Normalized (derived) data is stored separately from the original record. |
| **Timeline** | UTC-ordered and filterable by device, type, entity and time range. Shows how each timestamp was derived (`EXACT`, `ASSUMED_UTC`, `DATE_ONLY`, `UNPARSEABLE`, `MISSING`). Flags future, post-seizure and implausible times, and the same message appearing on two devices with matching or conflicting times. Undated records are listed, not dropped. |
| **Correlation engine** | Deterministic, documented rules: communication records, address-book entries, identifiers seen on each device, identifiers shared between devices, the same communication captured on two devices, and time proximity to location records. Every relationship states its rule, whether it is **stated by records** or **inferred**, an explanation, and its supporting record IDs. Phone numbers are matched on their last 10 digits, and such merges are disclosed. |
| **Relationship explorer** | Interactive graph of people, numbers, accounts, locations, devices and, optionally, individual events. Supports search, type and basis filters, zoom, pan, focus with step-wise expansion, a detail panel with supporting records, and a list view. |
| **Evidence assistant** | Search, question answering, summaries and entity extraction for one case. The backend sends only that case's records to the AI service, then removes anything citing records it didn't send. By default the AI service runs a local **extractive** engine (BM25 retrieval, counting, pattern extraction), so no evidence leaves the platform. Generative analysis with Claude is **opt-in**. If the AI service is down, a clearly labelled keyword fallback is returned. |
| **Workspace** | Bookmarks, notes and findings. Each finding states its basis (verified fact, inference or AI-suggested) and links supporting records. Findings go through a review workflow, and authors cannot review their own work. A chronological narrative is built from the findings. |
| **Reports** | Built from a stored JSON snapshot plus its SHA-256, so they can be reproduced and checked for alteration. Sections cover case details, devices, evidence sources with hashes, an overview, findings (with AI content marked), key events, methodology, data-driven limitations and the audit trail. PDF export is rendered from the snapshot. |
| **Security** | JWT access tokens and rotating refresh tokens. Server-side role and case-assignment checks on every endpoint. Idle-session timeout is enforced by both browser and server. Maintenance mode is enforced. Accounts are disabled rather than deleted. Cases or devices holding evidence cannot be deleted. Errors never expose stack traces, SQL, tokens or evidence content. |

## Roles

Access is checked on the server for every request. A user can only reach a case's devices, evidence, timeline, relationships, assistant, workspace and reports if they can see the case itself.

| Role | Access |
|---|---|
| **Admin** | View and edit every case; manage users, platform settings and maintenance mode; view the audit log and verify its hash chain. |
| **Investigator** | Create cases and devices; view and edit only the cases they lead; upload evidence, run imports and write notes and findings; draft reports. |
| **Reviewer** | View every case read-only; review findings and reports written by others. |

## Typical workflow

1. **Create a case** and register the seized devices.
2. **Upload extraction files** (`.xml`, `.ufdr`, `.zip`) in the case's *Files & integrity* tab. Hashes are recorded at upload.
3. **Watch the import** on the *Processing* page. Failed jobs can be retried.
4. **Explore** the records using evidence search, the timeline, the relationship explorer and the evidence assistant.
5. **Curate**: bookmark records, add notes and record findings with their supporting evidence.
6. **Review**: a reviewer accepts or rejects each finding.
7. **Report**: generate a snapshot report, check its hash and export the PDF.

## Architecture

```
frontend (React 19, Vite, TypeScript, Tailwind)  ──/api──▶  backend (Spring Boot 3.3, Java 21)  ──▶ PostgreSQL 16
                                                                 │  evidence files (read-only volume)
                                                                 └─▶ ai-service (FastAPI) ──(opt-in)──▶ Claude API
```

* Backend packages: `controller`, `service` (case access, ingestion, correlation, timeline, workspace, reports, assistant, audit), `ufdr` (secure parsing and extraction), `entity` and `repository` (JPA), with Flyway migrations `V1`–`V11`.
* AI service: `/health`, `/v1/config`, `/v1/assist`, with an extractive engine, an optional Claude engine (`claude-opus-5-5` by default, structured JSON output, refusal fallback) and grounding validation.

* Frontend: feature folders under `src/features` (cases, devices, evidence, files, processing, timeline, relationships, assistant, workspace, reports, audit, users, settings), a shared UI kit in `src/components/ui`, and routes split per page.

### Project structure

```
.
├── backend/            Spring Boot API (src/main/java/com/forensics/platform, Flyway migrations in src/main/resources/db/migration)
├── ai-service/         FastAPI evidence assistant (app/services: extractive, llm, grounding)
├── frontend/           React + Vite single-page app, served by Nginx in Docker
├── datasets/samples/   Synthetic extraction files and the supported XML schema
├── docs/               API, architecture, installation and development notes
├── docker-compose.yml
└── .env.example        Every configurable setting with safe local defaults
```

## Documentation

| Document | Contents |
|---|---|
| [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) | REST endpoints |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design |
| [docs/INSTALLATION.md](docs/INSTALLATION.md) | Installation steps |
| [docs/DEVELOPMENT_GUIDELINES.md](docs/DEVELOPMENT_GUIDELINES.md) | Coding conventions |
| [datasets/samples/README.md](datasets/samples/README.md) | Supported XML schema and the sample files |

## Run with Docker

```bash
cp .env.example .env        # optional; set JWT_SECRET and passwords for anything beyond local use
docker compose up --build
```

| Service | URL |
|---|---|
| Web app | http://localhost:3000 |
| Backend API / Swagger | http://localhost:8080 · http://localhost:8080/swagger-ui.html |
| AI service | http://localhost:8000/health |

Original evidence files persist in the `evidence-data` volume and the database in `postgres-data`. Back up both together.

### Configuration

All settings are environment variables. [.env.example](.env.example) lists them all. These are the main ones:

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | development value | Token signing key. **Change it** for anything beyond local use (at least 64 characters). |
| `POSTGRES_PASSWORD` | `forensics` | Database password |
| `SEED_DEVELOPMENT_USERS` / `SEED_DEMO_CASES` | `true` | Create development accounts and demo cases. Set both to `false` outside development. |
| `UPLOAD_MAX_BYTES` | `104857600` (100 MB) | Maximum evidence upload size |
| `PROCESSING_WORKERS` | `2` | Number of parallel import jobs |
| `AI_MAX_RECORDS` | `150` | Maximum records the backend sends to the AI service per request |
| `ANTHROPIC_API_KEY`, `AI_ALLOW_EXTERNAL_PROCESSING`, `AI_MODEL` | unset, `false`, `claude-opus-5-5` | Generative AI (opt-in, see below) |

## Local development

Requirements: JDK 21+ (tested with 21 in Docker and 25 locally), Maven 3.9, Node 20+, Python 3.12+.

```bash
# Backend — needs PostgreSQL on localhost:5432 (database/user/password: forensic_platform/forensics/forensics)
cd backend && mvn spring-boot:run

# Backend without Docker or a local PostgreSQL (embedded PostgreSQL 16, data in backend/target/dev-postgres)
cd backend && mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.forensics.platform.dev.LocalDevServer -Dexec.classpathScope=test

# AI service
cd ai-service && python -m venv .venv && .venv/Scripts/activate   # source .venv/bin/activate on macOS/Linux
pip install -r requirements-dev.txt && uvicorn app.main:app --port 8000

# Frontend (proxies /api to localhost:8080)
cd frontend && npm install && npm run dev        # http://localhost:5173
```

Development accounts (created only when missing, and only with `SEED_DEVELOPMENT_USERS=true`): `admin / Admin@123`, `investigator / Investigator@123` (Aarav Sharma, leads the first three demo cases), `reviewer / Reviewer@123`, and `inv01`–`inv05 / Investigator@123`. Demo cases are labelled synthetic and contain no evidence. Import the files in `datasets/samples` to create real records.

### Enabling generative AI (optional)

Set both variables on the AI service and restart it. Only do this where policy allows evidence to be sent to the model provider.

```
ANTHROPIC_API_KEY=...            # never commit
AI_ALLOW_EXTERNAL_PROCESSING=true
AI_MODEL=claude-opus-5-5         # optional
```

Without both, the local extractive engine is used and the UI says so.

## Tests

There are 56 backend tests, 8 AI-service tests and 22 frontend tests.

```bash
cd backend && mvn test           # unit, MockMvc + H2 integration, and PostgreSQL 16 (embedded) migration tests
cd ai-service && python -m pytest
cd frontend && npm test && npm run build
docker compose config -q         # compose validation
```

## Database migrations

* `V10` adds evidence provenance columns, source files, processing jobs and logs, workspace tables, report snapshot columns, audit chain columns, and the append-only audit trigger.
* `V11` makes IMEI unique per case instead of globally.
* All statements are idempotent. Existing rows are preserved: older records get `timestamp_status = LEGACY_IMPORT` (or `MISSING`), and pre-chain audit rows are reported as legacy and unverifiable.
* **If your database already ran `V5` or `V9` before their recent edits** (columns added so fresh databases migrate cleanly), Flyway reports a checksum mismatch at startup. Run `flyway repair` once, or start once with `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false`, then remove the flag.

## Known limitations

* Only the documented XML schema is decoded. Vendor-specific report formats (e.g. Cellebrite model/field XML) inside UFDR archives are not interpreted natively.
* Evidence and correlation analysis load a case's records into memory, which suits cases up to tens of thousands of records. Larger datasets would need streaming or database-side aggregation.
* The audit hash chain detects edits, deletions and reordering of chained rows, but not removal of the most recent rows (tail truncation) or changes by a database superuser who also recomputes the chain. Audit writes are serialized within one backend instance; multi-instance deployments would need a database-level lock.
* Tokens are stored in browser localStorage (existing design). Production deployments should use HTTPS, and ideally HttpOnly cookies.
* The PDF uses the standard PDF fonts, so characters outside Latin-1 render as `?`.
* Generative AI has been tested with a mocked client only; no live model call was made during development.
