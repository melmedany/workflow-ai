# Setup

Prerequisites, layout, and how to run Workflow AI and its tests. For what the application is and how it works, see
[README.md](README.md).

---

## Technologies / Tools

| Tool                        |                                                                                                      |
|-----------------------------|------------------------------------------------------------------------------------------------------|
| Java 25+                    | Main programming language                                                                            |
| Spring Boot                 | Wiring, REST, JPA, configuration properties.                                                         |
| LangChain4j                 | Per-provider chat and streaming chat models, plus the `InputGuardrail`/`OutputGuardrail` interfaces. |
| LangGraph4j                 | Compiles the stage graph and executes it, and provides it as Mermaid diagram.                        |
| JobRunr                     | Recurring and one-off scheduling for conversation tasks, behind the `TaskScheduler` port.            |
| PostgreSQL                  | Single store for agents, conversations, messages, memory, run history and tasks.                     |
| Flyway                      | Owns the schema, including JobRunr's tables. Migrations stay the only source of truth.               |
| Spring Data JPA             | Entities and repositories, used only inside `adapter.out.persistence` pacakge.                       |
| Jackson 3 (`tools.jackson`) | API serialisation and parsing the classifier's JSON output.                                          |
| JUnit 6                     | Test runtime.                                                                                        |
| Mockito                     | Mocking framework.                                                                                   |
| Testcontainers              | Integration tests run against a real PostgreSQL 16 container.                                        |
| REST Assured                | Endpoint tests over real HTTP, including reading the SSE stream.                                     |
| ArchUnit                    | The hexagonal boundaries are asserted as tests, not documented as conventions.                       |
| Gradle (Kotlin DSL)         | Version catalog in `gradle/libs.versions.toml`.                                                      |
| Frontend                    | Simple and plain HTML, CSS and JavaScript with no build step.                                        |

---

## Package Structure

```text
src/main/java/io/workflowai
├── adapter
│   ├── in
│   │   └── rest                        REST controllers, SSE emitter, exception handler
│   │       └── dto                     request/response records, SSE payloads, EventType, mappers
│   └── out
│       ├── chat
│       │   ├── guardrail               LangChain4j input/output guardrails and their config
│       │   └── provider                AbstractChatProvider, AbstractOpenAiProvider
│       │       ├── anthropic
│       │       ├── bonzai
│       │       ├── ollama
│       │       └── openai
│       ├── persistence                 storage adapters implementing the outbound ports
│       │   ├── agent
│       │   │   ├── memory              agent_memory entity and repository
│       │   │   └── run                 agent_runs entity, repository, status
│       │   ├── conversation
│       │   │   └── message             messages entity and repository
│       │   └── task                    conversation_tasks entity and repository
│       ├── scheduling                  JobRunr scheduler adapter, scheduled task runner
│       └── stream                      SSE workflow event streamer, stage labels
├── application
│   ├── agent                           AgentDefinitionService (admin use case)
│   ├── conversation                    ConversationService
│   ├── execution                       Agent, AgentRequest, AgentService, ChatProviderRegistry, ResponseValidator
│   │   ├── stage                       one class per workflow stage, plus shared response helpers
│   │   └── workflow                    WorkflowFactory, WorkflowPrompts
│   ├── port
│   │   ├── in                          inbound ports: agent, admin, conversation, task use cases
│   │   └── out                         outbound ports: chat, storage, scheduling, streaming, notification
│   └── task                            TaskService
├── bootstrap                           Spring Boot entry point, guardrail and stage properties
│   └── config                          bean wiring for the application layer
└── domain
    ├── agent                           AgentDefinition, AgentProperties, ChatProviderId, TriggerSource
    ├── conversation                    Conversation, ConversationMessage, message role
    ├── exceptions                      domain exception hierarchy
    ├── task                            ConversationTask, TaskStatus, SchedulingIntentDetector
    └── workflow                        stage contract, state, graph assembly, policy, events, executor
        └── response                    response contract, format, result
```

```text
src/main/resources
├── application.yml                     datasource, JobRunr, providers, stage models, guardrail terms
├── db/migration                        V1 agents, V2 conversations, V3 runs, V4 tasks, V5 JobRunr tables
└── static                              index.html, chat.html, admin.html, css/, js/

src/test
├── java/io/workflowai
│   ├── application/…                   agent and stage-level unit tests
│   ├── archunit                        ArchitectureTest
│   └── integration                     Testcontainers-backed endpoint and persistence tests
└── resources/io/workflowai/integration per-test SQL fixtures
```

The boundaries this layout implies are enforced by `ArchitectureTest`: the domain depends on nothing outward and on
neither Spring nor LangChain4j, and outside `domain.workflow` not on LangGraph4j either. The application layer depends
on ports rather than adapters and on neither AI framework; LangChain4j is allowed only under
`adapter.out.chat`; and `adapter.in` and `adapter.out` never reference each other. LangGraph4j is only ever imported
from `domain.workflow` today, though the test does not forbid other packages from using it directly.

---

## Persistence

Flyway owns every table. `spring.flyway.schemas` is `public, tasks`, so the `tasks` schema is created for JobRunr.

| Table                | Contents                                                                                                        |
|----------------------|-----------------------------------------------------------------------------------------------------------------|
| `agents`             | Agent definitions, with `details`, `chat_properties` and `workflow_policy` stored as JSONB. V1 seeds one agent. |
| `conversations`      | One row per conversation, owned by an agent. Cascades on agent delete.                                          |
| `messages`           | User, system, and agent messages.                                                                               |
| `agent_memory`       | One compact memory blob per `(conversation_id, agent_id)`.                                                      |
| `agent_runs`         | One row per workflow execution.                                                                                 |
| `conversation_tasks` | Scheduled tasks.                                                                                                |
| `tasks.jobrunr_*`    | JobRunr's own job.                                                                                              |

---

## Prerequisites

- **JDK 25+.**
- **Docker.** Used for the PostgreSQL container in development and by Testcontainers during tests.
- **A model provider.** At least one of:
    - **Ollama** running locally, with the models you configure already pulled. This is the default: the seeded agent
      uses `gemma4:26b` and every workflow stage uses `llama3.2:3b`. `OllamaProvider` checks `/api/tags` on the first
      call and fails with the exact `ollama pull …` command when a model is missing.
    - **OpenAI**, **Anthropic** or **Bonzai**, configured by environment variable.

Environment variables, all optional and all with defaults in `application.yml`:

```text
OLLAMA_BASE_URL       default http://localhost:11434
OPENAI_BASE_URL       default https://api.openai.com/v1
OPENAI_API_KEY        default not-configured
ANTHROPIC_BASE_URL    default not-configured
ANTHROPIC_API_KEY     default not-configured
BONZAI_BASE_URL       default not-configured
BONZAI_API_KEY        default not-configured
```

---

## Installation and running

Start the database, update the platform [docker-compose.yml](docker/docker-compose.yml) if you are not using M-series
chips:

```bash
docker compose -f docker/docker-compose.yml up -d workflow-ai-database
```

Run the application:

```bash
./gradlew bootRun
```

Or for windows:

```cmd
gradlew.bat bootRun
```

Then open:

```text
http://localhost:8080/          agent picker
http://localhost:8080/chat.html chat, with a Tasks tab per conversation
http://localhost:8080/admin.html agent administration
```

Per-stage models and guardrail blocklists are configuration, not admin fields: edit `workflow-ai.stages` and
`workflow-ai.guardrail` in `application.yml` and restart.

---

## Setup / Testing

```bash
./gradlew test
```

Or for windows:

```cmd
gradlew.bat test
```

- **Docker must be running.** `IntegrationBase` starts a `postgres:16` Testcontainer with container reuse enabled and
  hands its connection details to Spring through `@ServiceConnection`.
- **`ChatEndpointTest` must pass without a locally running Ollama instance.** It replaces `ChatProviderRegistry`
  with a `@MockitoBean` returning a stub provider, so classification and generation are answered in-process and no
  request leaves the JVM. `ScheduleTaskTest` does the same with a stub that returns schedule-extraction JSON. If either
  test ever starts requiring a real Ollama, treat that as a regression rather than a setup problem.
- The remaining integration tests `AgentDefinitionEndpointTest`, `ConversationPersistenceTest`,
  `MemoryPersistenceTest`, `DatabaseAgentRunTrackerAdapterTest` (in `RunHistoryPersistenceTest.java`) exercise the admin
  API and the storage adapters directly and never reach a model.
- Unit tests under `application/execution/stage` (`WorkflowStagesTest`, `ClassificationStageSchedulingTest`,
  `CreateTaskStageTest`) cover classification's scheduling behaviour, `CREATE_TASK`'s clarify and refuse branches, and
  the remaining stages without Spring or a database. `AgentDefinitionServiceTest` under `application/agent` covers
  validation errors on save/update the same way.
- `ArchitectureTest` runs in the same suite; a boundary violation fails the build like any other test.
- Behavioural specs for the pieces these tests cover live under [`/spec`](spec) in the project root; see the
  README's [Specs section](README.md#specs) for the index. Tests are written to enforce those specs.
- Test fixtures are per-test SQL files in `src/test/resources/io/workflowai/integration`, applied by `@Sql` and named
  after their test class.
