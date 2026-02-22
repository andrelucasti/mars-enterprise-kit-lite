# PRP 01 — Project Bootstrap & Infrastructure

## Purpose
Set up the Maven multi-module project structure, Docker Compose infrastructure, and Spring Boot application skeleton for Mars Enterprise Kit Lite.

### Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests the AI can run and fix
3. **TDD Mandatory**: Every feature follows Red-Green-Refactor
4. **Onion Architecture**: Dependencies always point inward (app -> data-provider -> business)
5. **Global Rules**: Follow all rules in CLAUDE.md

---

## Goal
A working Maven multi-module project with 3 modules (business, data-provider, app), Docker Compose for local infrastructure (PostgreSQL + Redpanda), and a Spring Boot application that starts and responds to health checks.

## Why
- Foundation for all subsequent PRPs — nothing can be built without project structure
- Docker Compose provides the local dev environment (PostgreSQL + Redpanda)
- Maven multi-module enforces Onion Architecture layer boundaries at build level
- The application skeleton validates that Spring Boot boots correctly

## What
A developer can:
1. Run `docker-compose up -d` to start PostgreSQL + Redpanda
2. Run `mvn clean compile` to compile all 3 modules
3. Run `cd app && mvn spring-boot:run` to start the application
4. Hit `GET /actuator/health` and receive `200 OK`

### Success Criteria
- [ ] Parent POM with 3 modules compiles: `mvn clean compile`
- [ ] Docker Compose starts PostgreSQL (port 5432) and Redpanda (port 9092)
- [ ] `cd app && mvn spring-boot:run` boots successfully
- [ ] `GET /actuator/health` returns 200
- [ ] business/ module has ZERO Spring/JPA dependencies (only spring-boot-starter for basic DI + test)

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project conventions, code style, TDD rules

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Full project context, architecture, domain model, Docker Compose specs
```

### Current Codebase Structure
```
mars-enterprise-kit-lite/
├── .mars/
│   ├── docs/mars-enterprise-kit-context-lite.md
│   └── PRPs/templates/prp_base.md
├── CLAUDE.md
└── (no source code yet — this PRP creates everything from scratch)
```

### Desired Codebase Changes
```
mars-enterprise-kit-lite/
├── pom.xml                                    # NEW: Parent POM (multi-module)
├── docker-compose.yml                         # NEW: PostgreSQL + Redpanda
│
├── business/                                  # NEW: Domain module
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/mars/lite/order/
│       │   └── .gitkeep                       # Placeholder
│       └── test/java/io/mars/lite/order/
│           └── .gitkeep                       # Placeholder
│
├── data-provider/                             # NEW: Infrastructure module
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/mars/lite/
│       │   └── .gitkeep
│       ├── main/resources/
│       │   └── db/migration/
│       │       └── .gitkeep
│       └── test/java/io/mars/lite/
│           └── .gitkeep
│
└── app/                                       # NEW: API entry point
    ├── pom.xml
    └── src/
        ├── main/java/io/mars/lite/
        │   ├── Application.java               # Spring Boot main class
        │   └── configuration/
        │       └── .gitkeep
        ├── main/resources/
        │   └── application.yaml               # Application configuration
```

### Known Gotchas
```java
// CRITICAL: business/ module must have ZERO Spring/JPA dependencies
// CRITICAL: This is the Lite version — uses Dual Write, NOT Outbox pattern
// CRITICAL: Events use JSON (Jackson), NOT Avro — no Schema Registry
// CRITICAL: Package is io.mars.lite (NOT com.mars.order)
// CRITICAL: Java 25 + Spring Boot 4.0.3
// CRITICAL: Module names are business, data-provider, app (NOT order-business, etc.)
// CRITICAL: No third-party module — Lite only has 3 modules
// CRITICAL: Port 8081 for the app (NOT 8989 like the Enterprise Kit)
// CRITICAL: Database name is orders_db, user/password is mars/mars
```

---

## Implementation Blueprint

### Data Models
```
No domain models in this PRP — just project structure.
```

### Tasks (in execution order)

```yaml
Task 1: Parent POM
  module: root
  files:
    - pom.xml
  tests: none (compilation is the test)
  TDD: Compile -> Verify structure

Task 2: Business Module POM
  module: business/
  files:
    - business/pom.xml
    - business/src/main/java/io/mars/lite/order/.gitkeep
    - business/src/test/java/io/mars/lite/order/.gitkeep
  tests: none
  TDD: Compile -> Verify no Spring/JPA deps

Task 3: Data Provider Module POM
  module: data-provider/
  files:
    - data-provider/pom.xml
    - data-provider/src/main/java/io/mars/lite/.gitkeep
    - data-provider/src/main/resources/db/migration/.gitkeep
    - data-provider/src/test/java/io/mars/lite/.gitkeep
  tests: none
  TDD: Compile -> Verify deps on business

Task 4: App Module POM + Application
  module: app/
  files:
    - app/pom.xml
    - app/src/main/java/io/mars/lite/Application.java
    - app/src/main/resources/application.yaml
  tests: none
  TDD: Compile -> Verify

Task 5: Docker Compose
  module: root
  files:
    - docker-compose.yml
  tests: docker-compose up -d && health check
```

### Per-Task Pseudocode

```xml
<!-- Task 1 — Parent POM -->
<!-- PATTERN: Follow reference mars-order-service/pom.xml structure -->
<!-- CHANGES: io.mars.lite groupId, 3 modules (no third-party), Spring Boot 4.0.3 -->

<parent>spring-boot-starter-parent 4.0.3</parent>
<groupId>io.mars.lite</groupId>
<artifactId>mars-enterprise-kit-lite</artifactId>
<version>1.0.0</version>
<modules>business, data-provider, app</modules>
<properties>java.version=25, testcontainers.version=1.21.3</properties>
<dependencyManagement>testcontainers-bom</dependencyManagement>
<build>maven-compiler-plugin 3.14.1, source/target=25</build>
```

```xml
<!-- Task 2 — Business POM -->
<!-- CRITICAL: Only spring-boot-starter (for minimal deps) + test deps -->
<!-- NO JPA, NO Web, NO Kafka — pure Java domain -->

<parent>io.mars.lite:mars-enterprise-kit-lite:1.0.0</parent>
<artifactId>business</artifactId>
<dependencies>
  spring-boot-starter-test (test scope)
</dependencies>
```

```xml
<!-- Task 3 — Data Provider POM -->
<!-- Depends on business module + Spring Data JPA + PostgreSQL + Flyway -->

<parent>io.mars.lite:mars-enterprise-kit-lite:1.0.0</parent>
<artifactId>data-provider</artifactId>
<dependencies>
  business (1.0.0)
  spring-boot-starter-data-jpa
  postgresql (42.7.8)
  flyway-core
  flyway-database-postgresql
  spring-boot-starter-test (test scope)
  testcontainers (test scope)
  testcontainers/junit-jupiter (test scope)
  testcontainers/postgresql (test scope)
</dependencies>
```

```xml
<!-- Task 4 — App POM + Application.java + application.yaml -->
<!-- Depends on business + data-provider + Web + Kafka + Actuator -->

<parent>io.mars.lite:mars-enterprise-kit-lite:1.0.0</parent>
<artifactId>app</artifactId>
<dependencies>
  business (1.0.0)
  data-provider (1.0.0)
  spring-boot-starter-web
  spring-boot-starter-validation
  spring-boot-starter-kafka
  spring-boot-starter-actuator
  spring-boot-starter-test (test scope)
  testcontainers (test scope)
  testcontainers/junit-jupiter (test scope)
  testcontainers/postgresql (test scope)
  testcontainers/kafka (test scope)
  rest-assured 5.5.0 (test scope)
</dependencies>
<build>spring-boot-maven-plugin, mainClass=io.mars.lite.Application</build>
```

```java
// Task 4 — Application.java
package io.mars.lite;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

```yaml
# Task 4 — application.yaml
# PATTERN: Follow reference but adapted for Lite
spring:
  application:
    name: mars-enterprise-kit-lite
  threads:
    virtual:
      enabled: true
  main:
    banner-mode: off
  datasource:
    hikari:
      driver-class-name: org.postgresql.Driver
      pool-name: mars-lite
      minimum-idle: 2
      maximum-pool-size: 5
      connection-timeout: 5000
      jdbcUrl: jdbc:postgresql://localhost:5432/orders_db
      username: mars
      password: mars
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: order-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
server:
  port: 8081
management:
  endpoints:
    web:
      exposure:
        include: health
```

```yaml
# Task 5 — docker-compose.yml
# PostgreSQL 16-alpine + Redpanda (Kafka-compatible)
# CRITICAL: orders_db database, mars/mars credentials
# CRITICAL: Redpanda creates topics on startup via command
services:
  postgres:
    image: postgres:16-alpine
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: orders_db
      POSTGRES_USER: mars
      POSTGRES_PASSWORD: mars
    healthcheck: pg_isready -U mars -d orders_db

  redpanda:
    image: redpandadata/redpanda:v24.3.1
    command: [redpanda start, --smp 1, --memory 512M, --overprovisioned,
              --kafka-addr 0.0.0.0:9092, --advertise-kafka-addr localhost:9092]
    ports: ["9092:9092", "9644:9644"]
```

### Integration Points
```yaml
DATABASE:
  - No migrations in this PRP (just empty db/migration/ directory)
  - Will be created in PRP 03

KAFKA:
  - No topics in this PRP (just Redpanda running)
  - Topics will be auto-created by Spring Kafka in PRP 04

CONFIGURATION:
  - app/src/main/resources/application.yaml (database, Kafka, server port)
```

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile
# Expected: BUILD SUCCESS for all 3 modules
```

### Level 2: Unit Tests (business/)
```bash
mvn test -pl business
# Expected: BUILD SUCCESS (no tests yet, just verifies test infrastructure)
```

### Level 3: Full Build
```bash
mvn clean verify
# Expected: BUILD SUCCESS
```

### Level 4: Docker Compose
```bash
docker-compose up -d
# Wait for healthchecks
docker-compose ps
# Expected: postgres and redpanda both healthy/running
docker-compose down
```

### Level 5: Application Start (manual verification)
```bash
# Start infra
docker-compose up -d
# Start app
cd app && mvn spring-boot:run &
# Wait for startup
sleep 10
curl -s http://localhost:8081/actuator/health
# Expected: {"status":"UP"}
# Stop app
kill %1
docker-compose down
```

---

## Final Checklist
- [ ] All modules compile: `mvn clean compile`
- [ ] Docker Compose starts PostgreSQL + Redpanda
- [ ] Application boots and responds to health check
- [ ] business/ POM has ZERO JPA/Web/Kafka dependencies
- [ ] Package structure is `io.mars.lite` throughout
- [ ] Java 25 + Spring Boot 4.0.3 configured correctly
- [ ] Virtual threads enabled

---

## Anti-Patterns to Avoid
- Don't add Spring Data JPA to business/ module
- Don't use `com.mars.order` package — use `io.mars.lite`
- Don't add Outbox dependencies — this is the Lite version
- Don't include third-party module — Lite only has 3 modules
- Don't use port 8989 — Lite uses 8081
- Don't skip Docker Compose healthchecks
- Don't hardcode database credentials (use application.yaml)

---

## Confidence Score: 9/10
- Context completeness: 9 (all POM patterns, Docker Compose, and configs are fully specified)
- Pattern availability in codebase: 8 (reference implementation provides all patterns)
- Validation gate coverage: 9 (compilation + Docker + health check)
- One-pass implementation likelihood: 9 (structural PRP with clear outputs)
