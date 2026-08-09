# Architecture

```text
┌─────────────────────┐        HTTPS/JWT         ┌──────────────────────┐
│ React Investigation │ ───────────────────────▶ │ Spring Boot API       │
│ Console             │                          │ Security + REST + JPA │
└─────────────────────┘                          └──────────┬───────────┘
                                                             │
                                                             ▼
                                                   ┌──────────────────┐
                                                   │ PostgreSQL       │
                                                   │ Evidence Metadata│
                                                   └──────────────────┘

┌─────────────────────┐       Future calls        ┌──────────────────────┐
│ Spring Boot API     │ ───────────────────────▶ │ FastAPI AI Service   │
└─────────────────────┘                          └──────────────────────┘
```

## Backend Layers

- `controller`: REST API surface
- `service`: business orchestration
- `repository`: persistence boundaries
- `entity`: JPA domain model
- `dto`: request and response contracts
- `security`: JWT and user details
- `config`: application wiring and data seeding
- `audit`, `timeline`, `storage`, `hashing`, `report`, `ai`, `dashboard`: bounded module packages for future milestones

## Frontend Layers

- `components`: reusable UI, layout, dashboard primitives
- `features`: domain feature screens
- `contexts`: auth state
- `routes`: protected routing
- `services`: Axios-backed API clients
- `types`: API and domain types

## AI Service

The AI service is intentionally a placeholder. It exposes health and module endpoints that return stable sample contracts for later OCR, entity extraction, summarization, and analysis implementation.
