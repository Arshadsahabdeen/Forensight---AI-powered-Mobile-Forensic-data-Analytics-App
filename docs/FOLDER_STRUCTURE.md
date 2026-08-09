# Folder Structure Guide

## Frontend

```text
src/
├── assets/
├── components/
│   ├── common/
│   ├── ui/
│   ├── dashboard/
│   └── layout/
├── features/
│   ├── authentication/
│   ├── dashboard/
│   ├── cases/
│   ├── devices/
│   ├── evidence/
│   ├── timeline/
│   ├── reports/
│   ├── audit/
│   └── ai/
├── hooks/
├── contexts/
├── routes/
├── services/
├── types/
├── utils/
├── styles/
├── App.tsx
└── main.tsx
```

Feature folders are reserved for milestone-specific domain logic. Shared visual pieces belong in `components`; API clients belong in `services`.

## Backend

```text
com.forensics.platform
├── config
├── security
├── controller
├── service
├── repository
├── entity
├── dto
├── mapper
├── exception
├── audit
├── timeline
├── storage
├── hashing
├── report
├── ai
├── dashboard
└── util
```

Keep controllers thin, services focused, DTOs stable, and entities persistence-oriented.
