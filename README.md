# Garment Factory Platform

Modular monolith: Spring Boot 4.1.1 (Java 21) + React/TypeScript + MySQL 8.4.

## Local development

1. `cp .env.example .env` and replace every `change-me-*` value.
2. `docker compose --env-file .env -f infra/docker-compose.yml up -d mysql redis minio mailpit`
3. Backend: `cd backend && set -a && . ../.env && set +a && mvn spring-boot:run`
4. Frontend: `cd frontend && npm install && npm run dev`

URLs: app http://localhost:5173, API docs http://localhost:8080/swagger-ui.html,
MinIO console http://localhost:9001, Mailpit http://localhost:8025.

Tests: `cd backend && mvn verify` (needs Docker for Testcontainers).