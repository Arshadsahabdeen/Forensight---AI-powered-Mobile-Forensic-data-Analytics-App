# API Documentation

Base URL: `/api/v1`. Interactive documentation is available at `/swagger-ui.html` when the backend is running.

All endpoints except `POST /auth/login`, `POST /auth/refresh` and `GET /health` require `Authorization: Bearer <access token>`.
Errors are JSON: `{ "timestamp", "status", "error", "message", "fields"? }`. They never contain stack traces, SQL, evidence content or tokens.

## Roles and case access

| Role | Access |
|---|---|
| `ADMIN` | Everything, including users, settings and the audit log. |
| `INVESTIGATOR` | Read and change cases where they are the lead investigator (matched on username or full name), plus the evidence, devices, workspace and reports of those cases. |
| `REVIEWER` | Read every case. Review findings and reports. Cannot change investigation data. |

Case scoping is enforced server-side on every evidence, device, timeline, correlation, assistant, workspace, file, job and report endpoint.

## Authentication

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/login` | `{ username, password }` → access token (15 min) and refresh token. Five failures lock the account temporarily. |
| POST | `/auth/refresh` | `{ refreshToken }` → rotated tokens. Refused when the session has been idle longer than the configured timeout. |
| POST | `/auth/logout` | Revokes the refresh token. |
| GET | `/auth/me` | Current user. |
| GET | `/platform/policy` | Session timeout, maintenance mode and default page size for the UI. |
| GET | `/directory/investigators` | Names of enabled investigators (for case assignment). |

## Cases and devices

| Method | Path | Notes |
|---|---|---|
| GET | `/cases?page&size&sortBy&sortDir&search&status&priority` | Paged list of accessible cases. |
| GET | `/cases/{id}` | Details, devices, evidence summary, findings summary, reports, activity, `canEdit`/`canDelete`. Audited. |
| POST / PUT | `/cases`, `/cases/{id}` | Validated. Investigators always lead the cases they open. |
| DELETE | `/cases/{id}` | Admin only. **409** if the case holds evidence; archive it instead. |
| GET | `/devices?page&size&sortBy&sortDir&search&status&operatingSystem&caseId` | Paged, case-scoped. |
| GET / POST / PUT / DELETE | `/devices/{id}` | IMEI is optional (14–16 digits) and unique per case. Delete returns **409** if the device has evidence. |

## Evidence files, integrity and processing

| Method | Path | Notes |
|---|---|---|
| POST | `/cases/{caseId}/evidence-files` (multipart `file`, optional `deviceId`) | `.xml`, `.ufdr` or `.zip`, up to 100 MB. Content signature checked. SHA-256 computed while storing. Returns **202** with the file and its processing job. **409** for an identical file already in the case. |
| POST | `/ufdr/upload?caseId=` | Alias of the endpoint above (authenticated). |
| GET | `/cases/{caseId}/evidence-files` | Files with processing and integrity status. |
| POST | `/evidence-files/{id}/verify` | Recomputes SHA-256: `VERIFIED`, `MISMATCH` or `MISSING`. Audited. |
| GET | `/evidence-files/{id}/custody` | Audit events for the file. |
| GET | `/evidence-files/{id}/download` | Original file. Audited. |
| GET | `/processing/jobs?caseId&status&page&size` | Jobs with queued, running and failed counts. |
| GET | `/processing/jobs/{id}` | Job and its log. |
| POST | `/processing/jobs/{id}/retry` | Failed jobs only. Imports are all-or-nothing, so a retry starts clean. |

## Evidence search

| Method | Path | Notes |
|---|---|---|
| GET | `/evidence?page&size&sort&direction&search&entity&artifactType&category&caseId&deviceId&fileId&startTime&endTime&timestampStatus` | `sort` ∈ `eventTime`, `createdAt`, `id`, `artifactType`. `entity` matches sender, recipient, number or contact, and compares phone numbers on their digits. |
| GET | `/evidence/count`, `/evidence/filters` | Count with the same filters; available filter values. |
| GET | `/evidence/{id}` | Original record, provenance (source file, location in file, hashes), normalized view, bookmark and findings. Audited. |
| GET | `/evidence/{id}/related` | Related records, each with a rule, a basis and a reason. |

## Timeline and correlation

| Method | Path | Notes |
|---|---|---|
| GET | `/timeline?caseId&deviceId&entity&types&category&from&to&flaggedOnly&order&page&size` | UTC-ordered events with original timestamp, timezone, derivation status and flags (`ASSUMED_UTC`, `UNPARSEABLE_TIMESTAMP`, `FUTURE_TIMESTAMP`, `AFTER_SEIZURE`, `IMPLAUSIBLE_DATE`, `CORROBORATED`, `CONFLICTING_TIMESTAMP`). Undated records are returned separately. |
| GET | `/cases/{caseId}/correlations?deviceId&includeEvents&windowMinutes&maxEntities` | Entities and relationships. Each relationship has `basis` (`VERIFIED_RECORD` or `INFERRED`), `rule`, `explanation` and `evidenceIds`. |

## Workspace

Under `/cases/{caseId}/workspace`: `GET` (bookmarks, notes, findings and narrative), `POST /bookmarks`, `DELETE /bookmarks/{id}`, `POST|PUT|DELETE /notes[/{id}]`, `POST|PUT|DELETE /findings[/{id}]`, `POST /findings/{id}/submit`, and `POST /findings/{id}/review` (`{ decision: REVIEWED|REJECTED, comment }`, reviewer or admin, never the author). AI-generated findings are stored as `AI_SUGGESTED` with status `NEEDS_REVIEW`. Editing a finding clears its review.

## Assistant

| Method | Path | Notes |
|---|---|---|
| POST | `/cases/{caseId}/assistant` | `{ task: SEARCH|ASK|SUMMARIZE|ENTITIES, question?, deviceId?, maxRecords? }`. Every statement, citation and entity cites records that were supplied, and anything else is removed. `status` is `COMPLETED`, `FALLBACK` (AI service unavailable) or `NO_EVIDENCE`. |
| GET | `/assistant/status` | Engine (`llm` or `extractive`), reachability, configuration hint. No secrets. |

## Reports

| Method | Path | Notes |
|---|---|---|
| POST | `/cases/{caseId}/reports` | `{ title?, includeFullTimeline?, includeAuditTrail? }`. Stores a JSON snapshot and its SHA-256. |
| GET | `/reports?caseId`, `/reports/{id}` | Detail includes the snapshot and `snapshotVerified`. |
| GET | `/reports/{id}/pdf` | PDF rendered from the stored snapshot. Audited. |
| POST | `/reports/{id}/review` | Reviewer or admin, not the generator. |
| DELETE | `/reports/{id}` | Case editors. Reviewed reports can only be deleted by an admin. |

## Administration (ADMIN)

| Method | Path | Notes |
|---|---|---|
| GET / POST | `/users` | Create users with a password policy (10+ characters, upper, lower, digit). |
| PATCH | `/users/{id}/status`, `/users/{id}/role` | Self-lockout and last-admin removal are prevented. |
| POST | `/users/{id}/password` | Resets the password and revokes the user's sessions. |
| GET / PATCH | `/users/settings` | Session idle timeout and maintenance mode are enforced. |
| GET | `/audit?page&size&action&username&success&caseId&from&to&search` | Paged. |
| GET | `/audit/actions`, `/audit/export` (CSV) | |
| POST | `/audit/verify` | Verifies the SHA-256 hash chain. |
| GET | `/dashboard` | Role-scoped figures (available to all roles). |
