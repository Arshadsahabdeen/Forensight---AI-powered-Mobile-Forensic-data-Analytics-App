# Installation Instructions

## Prerequisites

- Docker Desktop
- Node.js 22+
- Java 21
- Maven 3.9+
- Python 3.12+
- PostgreSQL 16 for non-Docker local runs

## Recommended Startup

```bash
docker compose up --build
```

Open http://localhost:3000 and sign in with:

- `admin`
- `Admin@123`

Additional development users:

- `investigator` / `Investigator@123`
- `reviewer` / `Reviewer@123`

## Local Startup Order

1. Start PostgreSQL.
2. Start backend with `mvn spring-boot:run`.
3. Start AI service with `uvicorn app.main:app --reload`.
4. Start frontend with `npm run dev`.

The frontend Vite proxy forwards `/api` requests to the backend during development.
