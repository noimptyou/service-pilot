# ServicePilot

ServicePilot is a modular Spring Boot customer-service Agent project. The first release is a modular monolith; later releases can extract conversation, knowledge ingestion, Agent orchestration, and approval execution services based on measured load.

## Stack

- Java 21
- Spring Boot 4.1
- Spring AI 2.0
- Spring Modulith 2.1
- PostgreSQL 17 + pgvector
- Vue 3 + TypeScript + Vite
- pnpm

## Repository

```text
service-pilot-server/  Spring Boot backend
service-pilot-web/     Vue frontend
compose.yml            Local PostgreSQL/pgvector
.env.example           Local configuration template
```

## Local startup

1. Copy `.env.example` to `.env` and keep `.env` out of Git.
2. Start Docker Desktop.
3. Run `docker compose up -d` in this directory.
4. Open `service-pilot-server` in IntelliJ and select Microsoft OpenJDK 21.
5. Run the backend with the IntelliJ Maven runner or `mvnw.cmd spring-boot:run` under Java 21.
6. Run `pnpm.cmd dev` in `service-pilot-web`.

The default profile starts without an LLM key. When an AI provider is selected, add `AI_API_KEY`, `AI_CHAT_MODEL`, and `AI_EMBEDDING_MODEL` to the IntelliJ Run Configuration environment variables, then enable the `ai` Spring profile.

Backend: `http://localhost:8080`

Frontend: `http://localhost:5173`

Actuator health: `http://localhost:8080/actuator/health`

## Verification

- Backend tests: `service-pilot-server\mvnw.cmd test`
- Backend package: `service-pilot-server\mvnw.cmd -DskipTests package`
- Frontend build: run `pnpm.cmd build` in `service-pilot-web`
