# Development Guidelines

- Use DTOs for API boundaries; do not expose entities directly.
- Keep authentication and authorization centralized in Spring Security.
- Record security-relevant user actions with audit logs.
- Prefer feature folders on the frontend and keep shared UI components reusable.
- Keep dashboard data contract stable for frontend charts and cards.
- Add Flyway migrations for durable schema changes in future milestones.
- Use role checks for administrative workflows.
- Keep placeholder modules routed and secured until their implementation milestone.
- Use type-safe API clients in `frontend/src/services`.
