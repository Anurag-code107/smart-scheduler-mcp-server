# SmartScheduler MCP Server

A proof-of-concept **Model Context Protocol (MCP) server** built with Spring Boot and Spring AI that exposes smart day scheduling capabilities as MCP tools — ready to be consumed by Claude, Cursor, or any MCP-compatible AI client.

---

## Table of Contents

- [What is MCP?](#what-is-mcp)
- [What This POC Does](#what-this-poc-does)
- [Architecture](#architecture)
- [MCP Tools Reference](#mcp-tools-reference)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Connecting an AI Client](#connecting-an-ai-client)
- [Running Tests](#running-tests)
- [Future Scope](#future-scope)
- [Known Limitations](#known-limitations)

---

## What is MCP?

**Model Context Protocol (MCP)** is an open standard (published by Anthropic, March 2025) that defines how AI models communicate with external tools and data sources over a structured JSON-RPC interface. Think of it as a USB-C port for AI: instead of every LLM integration needing a custom plugin or function-calling schema, MCP provides one universal protocol.

An **MCP server** is a lightweight process that:
1. Declares a set of **tools** (functions the AI can call).
2. Accepts tool-call requests from a **client** (e.g. Claude Desktop, Cursor, a custom agent).
3. Executes the tool logic and streams results back.

Transport options include **stdio** (local process), **HTTP with SSE**, and the newer **Streamable HTTP** (stateless POST) — this project uses **Streamable HTTP** over `POST /mcp`.

---

## What This POC Does

This server demonstrates how to wrap a real business domain — **calendar & meeting scheduling** — behind an MCP interface so an AI assistant can manage it via natural language.

A single "calendar owner" account is seeded on startup. An AI client connected to this server can:

- **Create events** with automatic double-booking prevention
- **Check availability** for any time slot
- **Reschedule** meetings (conflict-checked against existing events)
- **Cancel** meetings
- **Add participants** (external attendees) to existing events
- **Search events** by title with pagination
- **Register users** and list them

Example interaction via Claude Desktop:
> *"Schedule a 1-hour design review for tomorrow at 3 PM and invite sara@design.com"*  
> → Claude calls `check_availability`, then `create_event`, then `add_participant` autonomously.

---


<img width="975" height="580" alt="image" src="https://github.com/user-attachments/assets/9837cf19-0ff8-4c79-a537-b6c04a578961" />

All tools:
<img width="975" height="577" alt="image" src="https://github.com/user-attachments/assets/253bc7ac-3919-4bb7-b7bd-4b07073981c6" />


<img width="975" height="614" alt="image" src="https://github.com/user-attachments/assets/6229b3b5-cf9f-4b8d-8c8f-2c8112be3018" />

<img width="975" height="605" alt="image" src="https://github.com/user-attachments/assets/f9345a30-d11e-4480-bb83-e51092096778" />

<img width="975" height="594" alt="image" src="https://github.com/user-attachments/assets/46a378a5-beb8-4f2b-ac7f-dd4762ae9288" />

<img width="1918" height="955" alt="Screenshot 2026-04-06 152051" src="https://github.com/user-attachments/assets/52cb8f8f-41b1-4378-a3c5-5b510e90cd5f" />


## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  AI Client                          │
│  (Claude Desktop / Cursor / custom agent)           │
└────────────────────┬────────────────────────────────┘
                     │  POST /mcp  (Streamable HTTP)
                     │  Authorization: Bearer <api-key>
┌────────────────────▼────────────────────────────────┐
│              Spring Boot MCP Server                 │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │         Spring AI MCP Layer                  │   │
│  │  (tool discovery, JSON-RPC dispatch,         │   │
│  │   schema generation from @Tool annotations)  │   │
│  └──────────────┬───────────────────────────────┘   │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐   │
│  │  SchedulingTools  (MCP tool entrypoints)     │   │
│  │  UserTools                                   │   │
│  └──────────────┬───────────────────────────────┘   │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐   │
│  │  SchedulingService  (business logic)         │   │
│  └──────────────┬───────────────────────────────┘   │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐   │
│  │  JPA Repositories → H2 (dev) / PostgreSQL    │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

**Request flow:**
1. AI client sends a JSON-RPC 2.0 message to `POST /mcp`.
2. `ApiKeyFilter` validates the `Authorization: Bearer <key>` header.
3. Spring AI's MCP layer routes the call to the appropriate `@Tool`-annotated method.
4. `SchedulingTools` / `UserTools` parse and validate parameters, then delegate to `SchedulingService`.
5. `SchedulingService` enforces business rules (conflict detection, ownership) and persists via JPA.
6. The result is serialized to JSON and streamed back to the client.

---

## MCP Tools Reference

All tools are exposed at `POST /mcp`. Parameters are passed as a JSON object in the `arguments` field of the JSON-RPC request. ISO-8601 datetimes use the format `yyyy-MM-ddTHH:mm:ss` (e.g. `2025-06-01T10:00:00`).

### Scheduling Tools

| Tool | Description | Required Params | Optional Params |
|------|-------------|-----------------|-----------------|
| `create_event` | Creates a calendar event. Prevents double-booking automatically. | `title`, `startTime`, `endTime` | `description`, `timezone` (IANA, default UTC), `participantEmails` |
| `get_events` | Returns all events ordered by start time within a date range. | `startDate`, `endDate` | — |
| `check_availability` | Returns `available: true/false` plus any conflicting events for the owner. | `startTime`, `endTime` | — |
| `reschedule_meeting` | Moves an event to a new time slot. Checks for conflicts at the destination. | `eventId`, `newStartTime`, `newEndTime` | — |
| `cancel_meeting` | Deletes an event and removes all its participants. | `eventId` | — |
| `add_participant` | Adds an external attendee to an existing event. Idempotent (returns `ALREADY_PRESENT` status if duplicate). | `eventId`, `name`, `email` | — |
| `search_events` | Paginated case-insensitive title search. | `title` | `page` (0-based, default 0), `size` (1–100, default 10) |

### User Tools

| Tool | Description | Required Params |
|------|-------------|-----------------|
| `create_user` | Registers a new user. Email must be unique. | `name`, `email` |
| `get_users` | Returns all registered users. | — |

### Example: check_availability response

```json
{
  "available": false,
  "startTime": "2025-06-01T10:00:00",
  "endTime": "2025-06-01T11:00:00",
  "conflictingEvents": [
    {
      "id": 3,
      "title": "Client Call — Acme Corp",
      "startTime": "2025-06-01T10:30:00",
      "endTime": "2025-06-01T11:30:00",
      "timezone": "UTC",
      "participants": []
    }
  ]
}
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.3 |
| MCP | Spring AI 1.1.3 (`spring-ai-starter-mcp-server-webmvc`) |
| MCP Transport | Streamable HTTP (`POST /mcp`) |
| Persistence | Spring Data JPA + Hibernate |
| Dev / Test DB | H2 in-memory |
| Production DB | PostgreSQL |
| Validation | Jakarta Bean Validation |
| Boilerplate reduction | Lombok |
| Build | Maven (Maven Wrapper included) |
| Tests | JUnit 5 + Spring Boot Test (integration, H2) |

---

## Project Structure

```
src/
├── main/java/com/mcp/smartScheduler/
│   ├── SmartSchedulerMcpServer.java       # Spring Boot entry point
│   ├── config/
│   │   ├── ApiKeyFilter.java              # Bearer-token auth filter
│   │   ├── DataInitializer.java           # Seeds owner + sample events on startup
│   │   ├── McpToolConfig.java             # Registers tool beans with Spring AI
│   │   └── OwnerProperties.java           # Binds app.owner.* config
│   ├── dto/                               # Request / response DTOs
│   │   ├── EventRequest.java
│   │   ├── EventResponse.java
│   │   ├── UserRequest.java
│   │   ├── AddParticipantsResponse.java
│   │   ├── JsonRpcRequest.java
│   │   └── JsonRpcResponse.java
│   ├── entity/
│   │   ├── User.java                      # Calendar user / owner
│   │   ├── Event.java                     # Calendar event
│   │   └── Participant.java               # External attendee on an event
│   ├── exception/
│   │   ├── ConflictException.java         # 409 – double-booking / duplicate
│   │   ├── ResourceNotFoundException.java # 404 – event/user not found
│   │   ├── ValidationException.java       # 400 – bad input
│   │   └── GlobalExceptionHandler.java    # Maps exceptions → JSON errors
│   ├── repository/
│   │   ├── EventRepository.java           # Overlap-detection JPQL queries
│   │   ├── UserRepository.java
│   │   └── ParticipantRepository.java
│   ├── service/
│   │   └── SchedulingService.java         # All business logic lives here
│   └── tools/
│       ├── SchedulingTools.java           # @Tool methods for scheduling
│       └── UserTools.java                 # @Tool methods for user management
├── main/resources/
│   └── application.yaml
└── test/
    ├── java/com/mcp/smartScheduler/service/
    │   └── SchedulingServiceTest.java     # Integration tests (H2, transactional rollback)
    └── resources/
        └── application-test.yml
```

---

## Getting Started

### Prerequisites

- **Java 21+** (`java -version`)
- **Maven 3.9+** or use the included `./mvnw`
- No database setup needed for local dev — H2 runs in-memory

### 1. Clone and build

```bash
git clone https://github.com/your-username/smart-scheduler-mcp-server.git
cd smart-scheduler-mcp-server
./mvnw clean package -DskipTests
```

### 2. Set environment variables (optional for dev)

```bash
export OWNER_EMAIL=you@example.com        # defaults to owner@example.com
export MCP_API_KEY=your-secret-key        # defaults to negi-secret-key-mcp-server
```

### 3. Run

```bash
./mvnw spring-boot:run
```

Or:

```bash
java -jar target/smart-scheduler-*.jar
```

The server starts on `http://localhost:8080`.

On startup, `DataInitializer` seeds:
- 1 owner account (email from `OWNER_EMAIL`)
- 5 sample events (Morning Standup, Product Review, Client Call, Design Review, Sprint Planning)
- 5 sample participants

### 4. Verify

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer negi-secret-key-mcp-server" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

You should receive a JSON-RPC response listing all 9 registered tools.

### H2 Console (dev only)

Browse to `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:mem:calendardb`  
Username: `sa` | Password: *(empty)*

---

## Configuration

All configuration is in `src/main/resources/application.yaml`. The two environment-variable-driven properties you will need to set in production:

| Env Var | yaml key | Default | Purpose |
|---------|----------|---------|---------|
| `OWNER_EMAIL` | `app.owner.email` | `owner@example.com` | Email of the calendar owner account |
| `MCP_API_KEY` | `app.api-key` | `negi-secret-key-mcp-server` | Bearer token required on every request |

### Switching to PostgreSQL

Replace the `datasource` and `jpa` sections in `application.yaml` (or use a `application-prod.yaml` profile):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smartscheduler
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # use Flyway / Liquibase for migrations in prod
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

---

## Connecting an AI Client

### Claude Desktop

Add the following to your Claude Desktop MCP configuration (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "smart-scheduler": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer negi-secret-key-mcp-server"
      }
    }
  }
}
```

Restart Claude Desktop. The scheduling tools will appear in the tool list and Claude will call them automatically in response to scheduling-related prompts.

### Claude Code / any HTTP MCP client

```bash
claude mcp add smart-scheduler \
  --transport http \
  --url http://localhost:8080/mcp \
  --header "Authorization: Bearer negi-secret-key-mcp-server"
```

### Direct JSON-RPC call (curl)

```bash
# Create an event
curl -s -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer negi-secret-key-mcp-server" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "create_event",
      "arguments": {
        "title": "Q3 Planning",
        "startTime": "2025-09-01T10:00:00",
        "endTime": "2025-09-01T11:30:00",
        "timezone": "Asia/Kolkata",
        "participantEmails": ["alice@team.com", "bob@team.com"]
      }
    }
  }'
```

---

## Running Tests

```bash
./mvnw test
```

Tests use the `test` Spring profile which targets H2 (`application-test.yml`). Each test class runs in a transaction that is rolled back after the test, so tests are fully isolated.

Coverage includes:
- Duplicate email rejection on user creation
- Happy-path event creation with participants
- Invalid time range (end before start)
- Double-booking conflict detection
- Date-range filtering for `get_events`
- Free/busy `check_availability`
- Successful `reschedule_meeting`

---

## Future Scope

The current implementation is intentionally scoped as a POC. Below is a prioritized list of what a production version would need:

### Core Functionality
- **Recurring events** — daily / weekly / monthly recurrence rules (RFC 5545 RRULE)
- **Multi-owner support** — right now a single owner is seeded; extend to per-user calendars with auth
- **Attendee availability** — `check_availability` currently checks only the owner; extend to check all invitees
- **Timezone-aware conflict detection** — conflicts are currently compared in raw `LocalDateTime`; convert to UTC before overlap checks
- **Event reminders / notifications** — email or webhook notifications before events

### MCP / AI Enhancements
- **MCP Resources** — expose the calendar as a readable resource (`calendar://events/today`) so the AI can subscribe to it
- **MCP Prompts** — pre-built prompt templates (e.g. "find the next available 1-hour slot this week") for guided interactions
- **Streaming tool responses** — for long-running queries, stream partial results using SSE
- **Tool result caching** — cache `get_events` and `check_availability` results with a short TTL

### Integrations
- **Google Calendar / Outlook sync** — bidirectional sync via Google Calendar API or Microsoft Graph
- **Slack / Teams notifications** — notify participants when an event is created, updated, or cancelled
- **iCal export** — expose events as `.ics` files for import into standard calendar apps

### Infrastructure & Production-Readiness
- **Database migrations** — replace `ddl-auto: create-drop` with Flyway versioned migrations
- **Authentication** — replace the single static API key with JWT / OAuth 2.0 (Spring Security)
- **Rate limiting** — per-client request throttling on the `/mcp` endpoint
- **Observability** — Micrometer metrics, distributed tracing (OpenTelemetry), structured JSON logging
- **Docker / Kubernetes** — `Dockerfile`, `docker-compose.yml` for local dev, Helm chart for K8s
- **CI/CD** — GitHub Actions pipeline (build → test → Docker image → push)
- **API versioning** — versioned MCP tool schemas to avoid breaking clients on updates

---

## Known Limitations

| Limitation | Impact |
|-----------|--------|
| Single calendar owner | All events belong to one seeded owner; no multi-user scheduling |
| H2 in-memory DB | All data is lost on restart; PostgreSQL config is provided but not default |
| No timezone normalization | `startTime`/`endTime` are stored as-is; two events in different timezones can silently overlap |
| Static API key | No key rotation; any request with the key has full access |
| No pagination on `get_events` | Large date ranges return all events in a single response |
| `UserTools` not registered | `UserTools` class exists but is not wired into `McpToolConfig`; only `SchedulingTools` is active |

---

## License

This project is licensed under the [Apache License 2.0](https://github.com/Anurag-code107/RecipeHub-Backend/blob/main/LICENSE).
