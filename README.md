# ForensicIQ - AI-Powered Mobile Forensic Evidence Intelligence Platform

Enterprise digital forensic investigation platform with Module 1 authentication and role-based access control integrated across Spring Boot, React, PostgreSQL, and Docker.

## Milestone Scope

Implemented:

- React 19 + Vite + TypeScript frontend with dark enterprise UI
- JWT access-token login, refresh-token rotation, logout revocation, protected routes, persistent session, Axios refresh interceptor
- Spring Boot 3 backend with Spring Security, JWT, JPA, PostgreSQL, Flyway, Swagger
- Seeded development admin, investigator, reviewer, sample cases, devices, evidence, reports, and audit logs
- Dashboard backed by `/api/v1/dashboard`
- Placeholder architecture and APIs for future forensic modules
- FastAPI AI service placeholder
- Docker Compose for PostgreSQL, backend, frontend, and AI service

Not implemented yet by design:

- Full case management
- Device acquisition workflows
- Evidence processing
- Timeline reconstruction
- Report generation
- AI analysis logic

## Default Credentials

- Username: `admin`
- Email: `admin@forensics.local`
- Password: `Admin@123`

Sample investigator:

- Username: `investigator`
- Email: `investigator@forensics.local`
- Password: `Investigator@123`

Sample reviewer:

- Username: `reviewer`
- Email: `reviewer@forensics.local`
- Password: `Reviewer@123`

These accounts are development-only. Set `SEED_DEVELOPMENT_USERS=false` outside development and change passwords through a future administrative workflow or direct controlled database maintenance.

## Authentication Architecture

The frontend signs in at `POST /api/v1/auth/login` with a username or email and password. The backend validates BCrypt password hashes, tracks failed attempts, locks accounts after five failures for `ACCOUNT_LOCK_DURATION`, records audit events, and returns a short-lived JWT access token plus an opaque refresh token.

Access tokens are bearer tokens sent as `Authorization: Bearer <token>`. They use `JWT_ACCESS_EXPIRATION` in milliseconds, defaulting to `900000` (15 minutes). Refresh tokens are stored server-side only as SHA-256 hashes, use `JWT_REFRESH_EXPIRATION` in milliseconds, defaulting to `604800000` (7 days), and are rotated on every refresh.

Roles:

- `ADMIN`: dashboard, user management, audit logs, settings, and all current/future modules.
- `INVESTIGATOR`: dashboard and investigation modules, excluding admin-only user management/settings/audit administration.
- `REVIEWER`: dashboard and permitted review-oriented investigation views.

Frontend role checks hide navigation and redirect to `/unauthorized`; backend Spring Security remains the security boundary. `/api/v1/users/**` and `/api/v1/admin/**` require `ADMIN`.

## Authentication APIs

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

Error responses are JSON and avoid stack traces, SQL details, passwords, token secrets, and complete token values.

## Run With Docker

```bash
docker compose up --build
```

Services:

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- AI Service: http://localhost:8000
- AI Health: http://localhost:8000/health

Docker uses the PostgreSQL service hostname `postgres` for backend database access. The frontend container serves `/api/**` through Nginx to the backend service.

## Local Development

Frontend:

```bash
cd frontend
npm install
npm run build
npm run dev
```

Backend:

```bash
cd backend
mvn test
mvn spring-boot:run
```

AI Service:

```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

PostgreSQL defaults:

- Database: `forensic_platform`
- User: `forensics`
- Password: `forensics`

Copy `.env.example` to `.env` for local overrides. Do not commit real `.env` files or production secrets.

## Repository Structure

```text
forensic-platform/
├── frontend/
├── backend/
├── ai-service/
├── datasets/
├── docs/
├── docker/
├── docker-compose.yml
└── README.md
```

## Testing

Backend tests cover login success, invalid credentials, disabled accounts, account lockout, and refresh-token revocation behavior. Run from `backend/`:

```bash
mvn test
mvn package
```

Frontend verification:

```bash
cd frontend
npm run build
```

This project currently has no frontend test runner configured, so no new frontend test framework was introduced.

## Troubleshooting

- 401 on protected pages: confirm the access token exists and `GET /api/v1/auth/me` succeeds.
- Refresh loops: clear browser storage for the app and sign in again.
- CORS errors: set `FRONTEND_URL` to the exact frontend origin.
- Database startup errors: ensure PostgreSQL is healthy and Flyway migrations have completed.
- Docker frontend API errors: use the container frontend at `http://localhost:3000`, which proxies `/api` to the backend.

## Security Notes

The platform uses JWT authentication with BCrypt password encoding, hashed refresh tokens, account lockout, audit logging, and role-based authorities. Browser storage is used for tokens in the current architecture; this keeps the implementation simple and centralized, but production should prefer secure, HttpOnly, SameSite cookies with HTTPS. Replace `JWT_SECRET` before production, enforce HTTPS, tighten CORS, and keep future schema changes in Flyway migrations.
