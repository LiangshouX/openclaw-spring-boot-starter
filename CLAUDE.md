# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenClaw Spring Boot Starter — a Spring Boot SDK that bridges Spring Boot applications with the OpenClaw Runtime (an AI-powered platform for chat, task execution, and skill orchestration). The SDK auto-configures HTTP/WebSocket clients, session lifecycle, event dispatch, skill registration, and callback handling.

## Build Commands

```bash
mvn clean install                           # Full build with tests
mvn clean install -DskipTests               # Build without tests
mvn test                                    # Run all tests
mvn test -pl openclaw-runtime-api           # Tests for a single module
mvn test -Dtest=ClassName -pl <module>      # Single test class in a module
mvn test -Dtest=ClassName#method -pl <module>  # Single test method
```

Run sample apps:
```bash
mvn spring-boot:run -pl samples/sample-runtime
mvn spring-boot:run -pl samples/sample-skill-provider
```

## Tech Stack

- **Java 17**, Spring Boot 3.3.6, Maven multi-module
- **HTTP:** Spring WebFlux `WebClient` (reactive, non-blocking)
- **WebSocket:** Spring WebSocket (via webflux)
- **JSON:** Jackson (with `jackson-datatype-jsr310` for Java time types)
- **Lombok** 1.18.34 — used throughout for `@Data`, `@Builder`, `@Slf4j`, etc.
- **Reactive:** Project Reactor `Flux` (provided scope in api module)
- **Tracing (optional):** Micrometer Tracing + OpenTelemetry bridge
- **Testing:** JUnit 5 via `spring-boot-starter-test` (no tests written yet)

## Architecture

### Module Dependency Graph

```
openclaw-runtime-api                    ← Foundation: interfaces, DTOs, events, exceptions (zero internal deps)
    ↑
    ├── openclaw-runtime-client         ← HTTP/WS protocol layer (depends on: api)
    ├── openclaw-runtime-event          ← Event publishing & callbacks (depends on: api)
    ├── openclaw-runtime-session        ← Session lifecycle (depends on: api, client)
    ├── openclaw-runtime-skill          ← Skill annotation framework (depends on: api, client)
    ├── openclaw-runtime-converter      ← DTO conversion (depends on: api, client)
    │
    └── openclaw-runtime-autoconfigure  ← Spring Boot auto-config glue (depends on: ALL above)
            ↑
        openclaw-runtime-starter        ← Pure dependency aggregator (no source code)
            ↑
        samples/*
```

### Key Entry Point

`OpenClawRuntime` (interface in `api` module) is the sole public API. Its implementation `DefaultOpenClawRuntime` (in `autoconfigure`) wires together `SessionManager`, `OpenClawClient`, `SkillRegistry`, and `EventPublisher`.

### Auto-Configuration Flow

1. **Activation gate:** Entire SDK activates only when `openclaw.endpoint` is set
2. **`OpenClawAutoConfiguration`** — creates WebClient, domain clients, session manager, event publisher, converters
3. **`OpenClawSkillAutoConfiguration`** — if `openclaw.auto-register-skill=true` (default), creates `JsonSchemaGenerator`, `SkillRegistrar`, `OpenClawLifecycleInitializer`
4. **`OpenClawLifecycleInitializer`** (`ApplicationRunner`) — scans `@OpenClawSkill` beans → generates JSON Schema → registers with gateway → publishes `RuntimeStartedEvent`
5. **`OpenClawShutdownHandler`** (`DisposableBean`) — closes sessions → unregisters skills → stops heartbeat → publishes `RuntimeStoppedEvent`
6. **`OpenClawCallbackAutoConfiguration`** — if `openclaw.callback.enabled=true` (default), registers REST controller at configured path for async callbacks
7. **`TraceAutoConfiguration`** — optional OpenTelemetry integration

All beans use `@ConditionalOnMissingBean` / `@ConditionalOnProperty` — everything is overridable.

### Skill Framework

Annotation-driven: `@OpenClawSkill` (meta-annotated with `@Component`) → `SkillScanner` → `SkillMetadataBuilder` → `SkillRegistry` → `SkillRegistrar` (registers with gateway at startup). `SkillDispatcher` routes incoming skill invocations. `JsonSchemaGenerator` derives JSON Schema from the class structure.

### Event Model

13 typed subtypes of `RuntimeEvent`: `RuntimeStarted`, `RuntimeStopped`, `SessionCreated`, `SessionClosed`, `TaskStarted`, `TaskFinished`, `TaskFailed`, `ToolCalling`, `ToolFinished`, `ArtifactCreated`, `Reasoning`, `Streaming`, `Error`. `EventPublisher` dispatches to `RuntimeListener` implementations via `instanceof` pattern matching. Thread-safe via `CopyOnWriteArrayList`.

### Exception Hierarchy

All extend `OpenClawRuntimeException` with typed `ErrorCode`: `ClientException`, `AuthenticationException`, `SessionException`, `SkillException`, `CallbackException`, `RegisterException`, `TimeoutException`, `ConverterException`.

## Important Notes

- **Under active development** — several methods are stubs (e.g., `ChatClient.sendMessage()` throws `UnsupportedOperationException`, `ChatClient.streamMessage()` returns `Flux.empty()`)
- **Session storage is in-memory only** (`ConcurrentHashMap`) — no persistence layer
- **The `openclaw-runtime-starter` module has no source code** — it's a pure dependency aggregator (standard Spring Boot Starter pattern)
- **Auto-config classes** are registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **Configuration metadata** for IDE autocomplete is in `META-INF/additional-spring-configuration-metadata.json`
- **Documentation** (README, architecture doc in `docs/`) is in Chinese
