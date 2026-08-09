# API Documentation

Base URL: `/api/v1`

## Authentication

### POST `/auth/login`

Request:

```json
{
  "username": "admin",
  "password": "Admin@123"
}
```

Response:

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "System Administrator",
    "email": "admin@forensics.local",
    "roles": ["ADMIN"]
  }
}
```

### POST `/auth/refresh`

Rotates a valid refresh token and returns a new access token and refresh token.

### GET `/auth/me`

Requires bearer token. Returns the authenticated user's safe profile.

### POST `/auth/logout`

Requires bearer token. Revokes the supplied refresh token when present and records a logout audit event.

## Dashboard

### GET `/dashboard`

Returns:

- Total cases
- Active cases
- Closed cases
- Device count
- Evidence count
- Report count
- Investigator count
- Recent activities
- Recent cases
- Recent audit logs
- Chart datasets

## Placeholder Module APIs

All require bearer token and return sample JSON:

- GET `/cases`
- GET `/devices`
- GET `/evidence`
- GET `/reports`
- GET `/timeline`
- GET `/audit`
- GET `/ai`

## AI Service

Base URL: `http://localhost:8000`

- GET `/health`
- POST `/ai/analyze`
- POST `/ai/summary`
- POST `/ai/entities`
- POST `/ai/ocr`
