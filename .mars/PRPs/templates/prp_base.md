# PRP Template — Mars Enterprise Kit Lite

## Purpose
Template for AI agents to implement features in Mars Enterprise Kit Lite with sufficient context for one-pass success.

### Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests the AI can run and fix
3. **TDD Mandatory**: Every feature follows Red-Green-Refactor
4. **Onion Architecture**: Dependencies always point inward (app -> data-provider -> business)
5. **Global Rules**: Follow all rules in CLAUDE.md

---

## Goal
[What needs to be built — specific end state]

## Why
- [Business value and user impact]
- [Integration with existing Order domain]
- [Problems this solves]

## What
[User-visible behavior and technical requirements]

### Success Criteria
- [ ] [Specific measurable outcomes]

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project conventions, code style, TDD rules

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Full project context, architecture, domain model

- url: [Spring Boot 4.0.3 / Spring Framework 7 docs URL if relevant]
  why: [Specific sections needed]
```

### Current Codebase Structure
```
mars-enterprise-kit-lite/
├── business/                        # Domain (pure Java, no frameworks)
│   └── src/main/java/io/mars/lite/order/
│       ├── Order.java               # Aggregate Root
│       ├── OrderStatus.java         # Value Object (enum)
│       ├── OrderItem.java           # Value Object
│       ├── OrderRepository.java     # Port (interface)
│       └── usecase/                 # Use case interfaces + impls
├── data-provider/                   # Infrastructure (implements business)
│   └── src/main/java/io/mars/lite/
│       ├── configuration/           # DataSource, Kafka config
│       └── order/                   # JPA entities, repository adapters
├── app/                             # API entry point (wires everything)
│   └── src/main/java/io/mars/lite/
│       ├── Application.java
│       ├── configuration/           # UseCaseConfiguration
│       └── api/                     # Controllers, DTOs, event handlers
└── docker-compose.yml               # PostgreSQL + Redpanda
```

### Desired Codebase Changes
```
[Show new/modified files with their responsibility]
```

### Known Gotchas
```java
// CRITICAL: business/ module must have ZERO Spring/JPA dependencies
// CRITICAL: This is the Lite version — uses Dual Write, NOT Outbox pattern
// CRITICAL: Events use JSON (Jackson), NOT Avro — no Schema Registry
// CRITICAL: Use Java records for all immutable objects (domain objects, DTOs)
// CRITICAL: Request/Response DTOs must be separate files, not inner classes
// Package: io.mars.lite (not com.mars.order)
// Java 25 + Spring Boot 4.0.3
```

---

## Implementation Blueprint

### Data Models
```java
// Domain objects (business/ module) — use records for value objects
// JPA entities (data-provider/ module) — separate from domain
// Request/Response DTOs (app/ module) — Java records, separate files
```

### Tasks (in execution order)
```yaml
Task 1: [Domain Layer]
  module: business/
  files: [list files to create/modify]
  tests: [list test files — written FIRST]
  TDD: Red -> Green -> Refactor

Task 2: [Infrastructure Layer]
  module: data-provider/
  files: [JPA entities, adapters, Flyway migrations]
  tests: [integration tests with TestContainers — written FIRST]
  TDD: Red -> Green -> Refactor

Task 3: [API Layer]
  module: app/
  files: [services, controllers, DTOs, configuration]
  tests: [service integration tests + REST Assured E2E — written FIRST]
  TDD: Red -> Green -> Refactor
```

### Per-Task Pseudocode
```java
// Task 1 — Domain Layer pseudocode
// PATTERN: Follow existing Order.java aggregate structure
// PATTERN: Use records for value objects (see OrderItem.java)
// PATTERN: Repository interface in business/, impl in data-provider/

// Task 2 — Infrastructure Layer pseudocode
// PATTERN: JPA entity maps to domain (see OrderEntity.java)
// PATTERN: Flyway migration naming: V{N}__{description}.sql

// Task 3 — API Layer pseudocode
// PATTERN: Controller delegates to use case (see OrderController.java)
// PATTERN: Wire beans in UseCaseConfiguration.java
```

### Integration Points
```yaml
DATABASE:
  - migration: "V{N}__{description}.sql"
  - tables: [list new tables/columns]

KAFKA (Dual Write):
  - topic: [topic name if applicable]
  - payload: [JSON structure]

CONFIGURATION:
  - app/src/main/java/.../configuration/UseCaseConfiguration.java
  - app/src/main/resources/application.yaml (if needed)
```

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile
# Expected: BUILD SUCCESS. If errors, fix imports and dependencies.
```

### Level 2: Unit Tests (business/)
```bash
mvn test -pl business
# Fast, no Spring context. Tests domain logic in isolation.
# Test naming: should{Behavior}When{Condition}
```

### Level 3: Integration Tests (data-provider/)
```bash
mvn test -pl data-provider
# Uses TestContainers for PostgreSQL. Tests JPA mappings and migrations.
```

### Level 4: App Tests (service + E2E)
```bash
mvn test -pl app
# Service integration tests: verify @Service + repository with real DB
# E2E tests: REST Assured given/when/then with real HTTP server
```

### Level 5: Full Build
```bash
mvn clean verify
# Must pass completely before considering work done
```

---

## Final Checklist
- [ ] All tests pass: `mvn clean verify`
- [ ] TDD followed: tests written before production code
- [ ] Java records used for immutable objects
- [ ] Request/Response DTOs in separate files
- [ ] Flyway migration follows naming convention
- [ ] Dual Write used for events (NOT Outbox)
- [ ] Acceptance criteria met

---

## Anti-Patterns to Avoid
- Don't add Spring/JPA annotations in business/ module
- Don't write production code before a failing test
- Don't use setters in domain objects — use records or immutable classes
- Don't define DTOs as inner classes in controllers
- Don't implement Outbox pattern — this is the Lite version (Dual Write by design)
- Don't skip validation gates because "it should work"
- Don't catch generic exceptions — be specific

---

## Confidence Score: [1-10]
- Context completeness: [score]
- Pattern availability in codebase: [score]
- Validation gate coverage: [score]
- One-pass implementation likelihood: [score]
