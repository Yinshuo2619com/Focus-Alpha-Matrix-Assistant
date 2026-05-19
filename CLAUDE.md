# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Campus educational assistant (校园助手) with course schedule auto-import from university educational systems. Front-back separated SPA + REST API architecture.

## Build & Run Commands

### Frontend (Vue 3 + Vite)
```bash
cd frontend
npm install          # Install dependencies
npm run dev          # Dev server at http://localhost:5173
npm run build        # vue-tsc type-check + Vite production build
npm run preview      # Preview production build
```

### Backend (Spring Boot + Maven)
```bash
cd backend/assistant
./mvnw spring-boot:run           # Start at http://localhost:8080
./mvnw compile                   # Compile only
./mvnw test                      # Run tests (only placeholder test exists)
```

### Database
- MySQL database `edu_assistant`, tables: `user`, `schedule`, `course_entry`, `school_config`
- Schema DDL in `backend/assistant/src/main/resources/schema-schedule.sql`
- Credentials in `backend/assistant/src/main/resources/application.yml`

## Architecture

### Request Flow
Frontend (port 5173) → Vite proxy → Backend (port 8080) → MySQL

### Backend Layers
- **Controllers**: `AuthController` (public), `UserController` (auth), `AdminController` (ADMIN), `ScheduleController` (auth), `EduProxyController` (reverse proxy)
- **Services**: `UserServiceImpl`, `ScheduleServiceImpl`, `EduProxyService`, `ScheduleParser`
- **Config**: `SecurityConfig` (JWT filter chain), `JwtAuthenticationFilter`, `WebConfig` (CORS + static resources)

### Data Access
Mixed: MyBatis-Plus annotations on entities + JdbcTemplate direct SQL in services. Most queries use JdbcTemplate, not MyBatis-Plus mappers.

### Authentication
BCrypt password hashing → JWT with role claim (`admin` if username is "admin", else `USER`) → `Authorization: Bearer` header → `JwtAuthenticationFilter` validates and sets SecurityContext.

### Educational System Proxy (Core Feature)
`EduProxyController` acts as a full reverse proxy to the university's educational system:
- `EduProxyService` manages session cookies in a `ConcurrentHashMap` per user
- HTML responses are rewritten to inject JS that patches `jQuery.ajax`, `fetch`, `XMLHttpRequest.open` to route all requests through the proxy
- Handles SSL bypass, redirect following (3 hops), session cookie protection, frame-blocking header removal
- School configs in `school_config` table enable multi-school support

### Course Schedule Import Flow
1. `EduSystemFrame.vue` loads edu system login page through backend proxy
2. User logs in inside iframe; `EduProxyService` handles SHA1+salt login and stores session cookies
3. Frontend reads iframe DOM directly (same-origin via proxy) and parses course cards with regex
4. Backend alternative: `ScheduleParser` (Jsoup) parses HTML for both card-based and table-based formats
5. Preview dialog → save to DB via `ScheduleController.saveSchedule()`

### Frontend Structure
- **Views**: Login, Register, Home (schedule grid), Profile, ScheduleImport, UserManagement
- **Stores** (Pinia): `user.ts` (auth state, persisted to localStorage), `schedule.ts` (schedule CRUD + extraction)
- **Components**: `StatusBar` (header), `ScheduleGrid` (weekly table), `EduSystemFrame` (iframe wrapper)

## Conventions

- Java package: `com.educate.assistant`
- API prefix: `/api/`
- Unified response: `Result<T>` wrapper (`{code, message, data}`)
- Frontend: Vue 3 Composition API with `<script setup lang="ts">`
- UI: Element Plus components with `@element-plus/icons-vue`
- TypeScript interfaces in `src/types/`
- HTTP wrapper in `src/utils/request.ts` (Axios with JWT interceptor, 401 auto-redirect)
