name: "PRP: Simplify mars-microservice-standard Template to Single Maven Module"
description: |
Migrate templates/mars-microservice-standard/ from 4 Maven modules to a single module with
package-based Onion Architecture enforced by ArchUnit. Remove sealed interface ceremony on
use cases, eliminate UseCaseConfiguration.java and SampleService.java wrapper, remove manual
DataSource/Flyway configurations, and use @Service directly. Update README.md to reflect new structure.

---

## Goal

Restructure `templates/mars-microservice-standard/` from a 4-module Maven project (sample-business, sample-data-provider, sample-app, sample-third-party) into a **single Maven module** with package-based layer separation enforced by ArchUnit tests. This is the **canonical template** that all new Mars Enterprise Kit microservices will be generated from — it must exemplify ADR-021.

## Why

- **ADR-021** (docs/adrs/021-simplify-single-module-architecture.md) documents the decision to simplify
- Current 4-module structure creates **46+ lines across 4 files** for operations with 1 line of real logic
- `sealed interface permits SingleImpl` eliminates polymorphism (the only benefit of interfaces)
- `UseCaseConfiguration.java` manually wires beans that `@Service` + component scanning handles automatically
- Manual `DataSourceConfiguration.java` and `FlywayConfiguration.java` duplicate Spring Boot auto-configuration
- Market research shows 60-70% of companies use single-module + packages; only 5-10% use multi-module per service
- **As the template**, this change propagates to every future service generated from it
- Reduces 5 pom.xml files to 1, removes ~20 structural/boilerplate files

## What

### Success Criteria
- [ ] Single `pom.xml` replaces 5 pom.xml files (packaging=jar, no `<modules>`)
- [ ] All source files reorganized into `domain/`, `infrastructure/`, `api/` packages
- [ ] Use case interface removed; use case becomes `@Service` class directly
- [ ] `UseCaseConfiguration.java` deleted
- [ ] `SampleService.java` (service wrapper) deleted — use case IS the service now
- [ ] `DataSourceConfiguration.java` deleted — Spring Boot auto-configures HikariCP
- [ ] `FlywayConfiguration.java` deleted — `spring-boot-starter-flyway` handles it via yaml config
- [ ] New comprehensive `ArchitectureTest.java` with ArchUnit rules enforcing package boundaries (all ADR-021 rules)
- [ ] `mvn clean verify` passes — **all existing test scenarios pass with zero behavioral changes**
- [ ] **@Test count MUST increase** — current total is exactly 21 tests across all modules; after migration `mvn test` must report >= 26 tests (ArchitectureTest expands from 4 to 12 rules; ApplicationContextTest replaces ThirdPartyApplicationTests; integration tests are consolidated with 1 duplicate removed)
- [ ] `README.md` updated to reflect single-module structure and usage instructions
- [ ] `samples.http` preserved unchanged
- [ ] Application starts and connects to database successfully (smoke test: `/actuator/health` returns UP)

### Git Commit Strategy
This migration should be done in a single feature branch. Recommended commits:
1. `feat(template): create single-module structure with merged pom.xml`
2. `refactor(template): move domain classes to domain/ package`
3. `refactor(template): move infrastructure classes to infrastructure/ package`
4. `refactor(template): move api classes and Application`
5. `refactor(template): transform use case from sealed interface to @Service`
6. `test(template): add comprehensive ArchitectureTest with ADR-021 rules`
7. `test(template): migrate all tests to new package structure`
8. `chore(template): delete old module directories and update README`
9. `test(template): verify full build with mvn clean verify`

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project rules and conventions (CRITICAL - read first)

- adr: docs/adrs/021-simplify-single-module-architecture.md
  why: The decision document driving this migration

- adr: docs/adrs/001-onion-architecture.md
  why: Original architecture (being superseded)

- adr: docs/adrs/003-transactional-outbox-pattern.md
  why: Outbox pattern must be preserved
```

### Known Gotchas & Critical Rules

**1. Jackson 3 in Spring Boot 4 (CRITICAL)**
```java
// Spring Boot 4 uses Jackson 3 (tools.jackson.*) NOT Jackson 2 (com.fasterxml.jackson.*)
// Auto-configured bean: tools.jackson.databind.json.JsonMapper
// Exception: tools.jackson.core.JacksonException
// SampleEventPublisherImpl already uses correct Jackson 3 imports - preserve them
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
```

**2. TestContainers + Docker Desktop 4.59**
```xml
<!-- Surefire needs -Dapi.version=1.44 for Docker Desktop compatibility -->
<argLine>-Dapi.version=1.44</argLine>
<environmentVariables>
    <DOCKER_API_VERSION>1.44</DOCKER_API_VERSION>
</environmentVariables>
```

**3. ArchUnit + Java 25**
```xml
<!-- Must use archunit-junit5 1.4.1+ for Java 25 bytecode support -->
<artifactId>archunit-junit5</artifactId>
<version>1.4.1</version>
```

**4. AbstractIntegrationTest Pattern with Outbox Starter**
```java
// When mars-outbox-spring-boot-starter is on classpath, its JPA entities are scanned
// The current template uses Flyway enabled + Kafka disabled in tests
registry.add("spring.flyway.enabled", () -> "true");
registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
```

**5. mars-outbox-spring-boot-starter API**
```java
// EventPublisher.publish() takes 4 String params: (String eventType, String aggregateId, String channel, String payload)
// OutboxEventHandler.handle(OutboxEvent event) — OutboxEvent record: (UUID idempotentId, String eventType, String aggregateId, String channel, String payload)
// The starter auto-creates its own outbox table via Flyway migration
```

**6. Component Scanning**
```java
// Application.java is at com.mars.sample — @SpringBootApplication scans com.mars.sample.**
// All @Service, @Component, @Repository, @RestController in subpackages are auto-discovered
// No UseCaseConfiguration needed — @Service on CreateSampleUseCase is enough
```

**7. Flyway Migration from Manual Config to Auto-Config**
```yaml
# BEFORE: FlywayConfiguration.java manually creates Flyway bean with baselineVersion("0")
# AFTER: spring-boot-starter-flyway auto-configures. Set in application.yaml:
spring:
  flyway:
    enabled: true
    baseline-version: "0"
    baseline-on-migrate: true
```

**8. DataSource Auto-Configuration**
```yaml
# BEFORE: DataSourceConfiguration.java manually creates HikariDataSource bean
# AFTER: spring-boot-starter-data-jpa + application.yaml is sufficient
# Spring Boot auto-configures HikariCP from spring.datasource.* properties
# Move HikariCP-specific properties under spring.datasource.hikari.*
```

---

## Current Structure → Target Structure

### Current (4 modules, 5 pom.xml)
```
templates/mars-microservice-standard/
├── pom.xml (parent POM, packaging=pom)
├── README.md
├── samples.http
├── sample-business/
│   ├── pom.xml
│   └── src/main/java/com/mars/sample/
│       ├── Sample.java, SampleRepository.java, BusinessException.java
│       ├── common/ (DomainEvent.java, DomainResult.java, Channel.java)
│       ├── events/ (SampleEvent.java, SampleCreated.java, SampleEventType.java, SampleEventPublisher.java)
│       └── usecase/ (CreateSampleUseCase.java [sealed interface], CreateSampleUseCaseImpl.java)
│   └── src/test/java/com/mars/sample/
│       ├── SampleTest.java (5 tests)
│       ├── ArchitectureTest.java (4 tests)
│       └── usecase/CreateSampleUseCaseTest.java (3 tests)
├── sample-data-provider/
│   ├── pom.xml
│   └── src/main/java/com/mars/sample/
│       ├── SampleEntity.java, SampleJpaRepository.java, SampleRepositoryImpl.java
│       └── configuration/ (DataSourceConfiguration.java, FlywayConfiguration.java, AuditingConfiguration.java)
│   └── src/main/resources/db/migration/V1__create_samples_table.sql
├── sample-app/
│   ├── pom.xml
│   └── src/main/java/com/mars/sample/
│       ├── Application.java
│       ├── api/ (SampleController.java, CreateSampleRequest.java, CreateSampleResponse.java)
│       ├── service/ (SampleService.java, KafkaProducerHandler.java)
│       ├── events/publishers/ (SampleEventPublisherImpl.java)
│       └── configuration/usecase/ (UseCaseConfiguration.java)
│   └── src/main/resources/application.yaml
│   └── src/test/java/com/mars/sample/
│       ├── AbstractIntegrationTest.java
│       ├── SampleControllerE2ETest.java (4 tests)
│       ├── CreateSampleUseCaseIntegrationTest.java (1 test)
│       └── service/SampleServiceIntegrationTest.java (3 tests)
└── sample-third-party/
    ├── pom.xml
    └── src/test/java/com/mars/sample/ThirdPartyApplicationTests.java (1 test — DELETE)
```

### Target (1 module, 1 pom.xml)
```
templates/mars-microservice-standard/
├── pom.xml                                    # Single POM (all deps merged)
├── README.md                                  # UPDATED to reflect new structure
├── samples.http                               # UNCHANGED
├── src/
│   ├── main/
│   │   ├── java/com/mars/sample/
│   │   │   ├── Application.java               # MOVE: unchanged
│   │   │   │
│   │   │   ├── domain/                        # FROM: sample-business
│   │   │   │   ├── Sample.java                # MOVE: update package
│   │   │   │   ├── SampleRepository.java      # MOVE: update package (port interface)
│   │   │   │   ├── BusinessException.java     # MOVE: update package
│   │   │   │   ├── common/                    # MOVE: update packages
│   │   │   │   │   ├── DomainEvent.java
│   │   │   │   │   ├── DomainResult.java
│   │   │   │   │   └── Channel.java
│   │   │   │   ├── events/                    # MOVE: update packages
│   │   │   │   │   ├── SampleEvent.java
│   │   │   │   │   ├── SampleCreated.java
│   │   │   │   │   ├── SampleEventType.java
│   │   │   │   │   └── SampleEventPublisher.java  # Port interface
│   │   │   │   └── usecase/
│   │   │   │       └── CreateSampleUseCase.java   # TRANSFORM: @Service class (merge interface+impl)
│   │   │   │
│   │   │   ├── infrastructure/                # FROM: sample-data-provider + sample-app infra
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── SampleEntity.java      # MOVE: update package + imports
│   │   │   │   │   ├── SampleJpaRepository.java
│   │   │   │   │   └── SampleRepositoryImpl.java
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── SampleEventPublisherImpl.java  # MOVE: update package + imports
│   │   │   │   │   └── KafkaProducerHandler.java      # MOVE: update package
│   │   │   │   └── configuration/
│   │   │   │       └── AuditingConfiguration.java     # MOVE: keep @EnableJpaAuditing
│   │   │   │
│   │   │   └── api/                           # FROM: sample-app/api
│   │   │       ├── SampleController.java      # MODIFY: inject CreateSampleUseCase directly
│   │   │       ├── CreateSampleRequest.java   # MOVE: update package
│   │   │       └── CreateSampleResponse.java  # MOVE: update package
│   │   │
│   │   └── resources/
│   │       ├── application.yaml               # MODIFY: enable Flyway, add baseline config
│   │       └── db/migration/
│   │           └── V1__create_samples_table.sql  # MOVE: from sample-data-provider
│   │
│   └── test/
│       └── java/com/mars/sample/
│           ├── ArchitectureTest.java          # REWRITE: comprehensive ArchUnit rules for package boundaries
│           ├── AbstractIntegrationTest.java   # MOVE: from sample-app, update imports
│           ├── ApplicationContextTest.java    # NEW: Spring context smoke test
│           │
│           ├── domain/                        # FROM: sample-business tests
│           │   ├── SampleTest.java
│           │   └── usecase/
│           │       ├── CreateSampleUseCaseTest.java           # MODIFY: test @Service class directly
│           │       └── CreateSampleUseCaseIntegrationTest.java # MERGE: outbox + persistence tests
│           │
│           └── api/                           # FROM: sample-app tests
│               └── SampleControllerE2ETest.java  # MODIFY: inject use case directly
```

---

## Implementation Blueprint

### Phase 0: Preparation (Read & Understand)

**MANDATORY READS before any code changes:**
1. `CLAUDE.md` - Project conventions
2. `docs/adrs/021-simplify-single-module-architecture.md` - The decision driving this work
3. ALL current source files listed in "Current Structure" above

### Phase 1: Create New Single-Module POM

**File: `templates/mars-microservice-standard/pom.xml`**

Merge all 5 pom.xml into one. Key requirements:
- Parent: `spring-boot-starter-parent:4.0.0`
- Packaging: `jar` (NOT `pom`)
- NO `<modules>` section
- ALL dependencies from all 4 child modules merged (deduplicated)
- `spring-boot-maven-plugin` with `mainClass=com.mars.sample.Application`
- Surefire with `-Dapi.version=1.44` for TestContainers
- Replace `flyway-core` with `spring-boot-starter-flyway`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.0</version>
        <relativePath/>
    </parent>
    <groupId>com.mars</groupId>
    <artifactId>mars-sample-service</artifactId>
    <version>1.0.0</version>
    <name>mars-sample-service</name>
    <description>Mars Sample Service - Template for new microservices (Single Module with Package-Based Onion Architecture)</description>

    <properties>
        <java.version>25</java.version>
        <testcontainers.version>1.21.3</testcontainers.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Persistence -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.8</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-kafka</artifactId>
        </dependency>

        <!-- Outbox Starter -->
        <dependency>
            <groupId>com.mars</groupId>
            <artifactId>mars-outbox-spring-boot-starter</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Dev Tools -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <version>5.5.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>1.4.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.mars.sample.Application</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
                <configuration>
                    <source>25</source>
                    <target>25</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <environmentVariables>
                        <DOCKER_API_VERSION>1.44</DOCKER_API_VERSION>
                    </environmentVariables>
                    <argLine>-Dapi.version=1.44</argLine>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Phase 2: Domain Package (from sample-business)

Move files from `sample-business/src/main/java/com/mars/sample/` to `src/main/java/com/mars/sample/domain/`.

**For each file, update the package declaration** from `package com.mars.sample;` to `package com.mars.sample.domain;` (or appropriate subpackage).

**Files to move (unchanged except package):**
- `Sample.java` → `domain/Sample.java` — update package + imports for events/common
- `SampleRepository.java` → `domain/SampleRepository.java` — update package
- `BusinessException.java` → `domain/BusinessException.java` — update package

**common/ subpackage (unchanged except package):**
- `DomainEvent.java` → `domain/common/DomainEvent.java` — update package
- `DomainResult.java` → `domain/common/DomainResult.java` — update package
- `Channel.java` → `domain/common/Channel.java` — update package

**events/ subpackage (unchanged except package):**
- `SampleEvent.java` → `domain/events/SampleEvent.java` — update package + imports
- `SampleCreated.java` → `domain/events/SampleCreated.java` — update package + imports
- `SampleEventType.java` → `domain/events/SampleEventType.java` — update package
- `SampleEventPublisher.java` → `domain/events/SampleEventPublisher.java` — update package + imports

**Use Case - TRANSFORM (merge sealed interface + impl into @Service class):**

**CreateSampleUseCase.java** — merge `CreateSampleUseCase` (sealed interface) + `CreateSampleUseCaseImpl` (final class):
```java
package com.mars.sample.domain.usecase;

import com.mars.sample.domain.Sample;
import com.mars.sample.domain.SampleRepository;
import com.mars.sample.domain.events.SampleEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class CreateSampleUseCase {

    private final SampleRepository sampleRepository;
    private final SampleEventPublisher sampleEventPublisher;

    public CreateSampleUseCase(SampleRepository sampleRepository,
                                SampleEventPublisher sampleEventPublisher) {
        this.sampleRepository = Objects.requireNonNull(sampleRepository, "sampleRepository cannot be null");
        this.sampleEventPublisher = Objects.requireNonNull(sampleEventPublisher, "sampleEventPublisher cannot be null");
    }

    @Transactional
    public UUID execute(final Input input) {
        final var domainResult = Sample.create(input.customerId(), input.productId());
        final var sample = domainResult.domain();
        final var sampleCreated = domainResult.event();

        sampleRepository.save(sample);
        sampleEventPublisher.publish(sampleCreated);
        return sample.sampleId();
    }

    public record Input(UUID customerId, UUID productId) {}
}
```

### Phase 3: Infrastructure Package

**persistence/ subpackage** (from sample-data-provider):
- `SampleEntity.java` → `infrastructure/persistence/SampleEntity.java`
- `SampleJpaRepository.java` → `infrastructure/persistence/SampleJpaRepository.java`
- `SampleRepositoryImpl.java` → `infrastructure/persistence/SampleRepositoryImpl.java`

Update all imports from `com.mars.sample.*` domain types to `com.mars.sample.domain.*`.

**messaging/ subpackage** (from sample-app):
- `SampleEventPublisherImpl.java` → `infrastructure/messaging/SampleEventPublisherImpl.java`
    - Update imports: `com.mars.sample.common.*` → `com.mars.sample.domain.common.*`
    - Update imports: `com.mars.sample.events.*` → `com.mars.sample.domain.events.*`
- `KafkaProducerHandler.java` → `infrastructure/messaging/KafkaProducerHandler.java`
    - Update package only (no domain imports to change)

**configuration/ subpackage:**
- `AuditingConfiguration.java` → `infrastructure/configuration/AuditingConfiguration.java`
    - Update package only

**DELETE these configuration files (Spring Boot auto-config replaces them):**
- `DataSourceConfiguration.java` — Spring Boot auto-configures HikariCP from yaml properties
- `FlywayConfiguration.java` — `spring-boot-starter-flyway` auto-configures from yaml properties

### Phase 4: API Package (from sample-app)

Move files from `sample-app/src/main/java/com/mars/sample/api/` to `src/main/java/com/mars/sample/api/`.

**SampleController.java** — MODIFY to inject `CreateSampleUseCase` directly (replaces `SampleService`):
```java
package com.mars.sample.api;

import com.mars.sample.domain.usecase.CreateSampleUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/samples")
public class SampleController {

    private final CreateSampleUseCase createSampleUseCase;

    public SampleController(final CreateSampleUseCase createSampleUseCase) {
        this.createSampleUseCase = createSampleUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateSampleResponse> createSample(@Valid @RequestBody final CreateSampleRequest request) {
        final var sampleId = createSampleUseCase.execute(
                new CreateSampleUseCase.Input(request.customerId(), request.productId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateSampleResponse(sampleId));
    }
}
```

- `CreateSampleRequest.java` → `api/CreateSampleRequest.java` — update package only
- `CreateSampleResponse.java` → `api/CreateSampleResponse.java` — update package only

### Phase 5: Application & Resources

**Application.java** — stays at `com.mars.sample.Application` (root package, unchanged)

**application.yaml** — MODIFY to enable Flyway auto-config and update datasource config:
```yaml
spring:
  application:
    name: mars-sample-service
  threads:
    virtual:
      enabled: true
  main:
    banner-mode: off
  flyway:
    enabled: true
    baseline-version: "0"
    baseline-on-migrate: true
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: sample-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.ByteArraySerializer
    listener:
      concurrency: 3
  datasource:
    url: jdbc:postgresql://localhost:5432/sample
    username: sample
    password: sample
    hikari:
      pool-name: mars-sample-service
      minimum-idle: 2
      maximum-pool-size: 5
      connection-timeout: 5000
server:
  port: 8989
mars:
  outbox:
    enabled: true
    poll-interval: 5000
    batch-size: 100
    retry:
      max-retries: 5
      initial-delay: 2000
      max-delay: 60000
      multiplier: 2.0
```

**IMPORTANT datasource config change:** The current template uses `spring.datasource.hikari.jdbcUrl` (HikariCP-specific). With Spring Boot auto-config, use `spring.datasource.url` (standard Spring Boot property). Spring Boot will pass it to HikariCP automatically. This also simplifies `AbstractIntegrationTest` (no need for dual `url` + `hikari.jdbcUrl` properties).

**V1__create_samples_table.sql** — MOVE from `sample-data-provider/src/main/resources/db/migration/` to `src/main/resources/db/migration/` (unchanged content)

**DELETE these files (no longer needed):**
- `sample-app/src/main/java/com/mars/sample/configuration/usecase/UseCaseConfiguration.java`
- `sample-app/src/main/java/com/mars/sample/service/SampleService.java`
- `sample-data-provider/src/main/java/com/mars/sample/configuration/DataSourceConfiguration.java`
- `sample-data-provider/src/main/java/com/mars/sample/configuration/FlywayConfiguration.java`
- All 4 child module `pom.xml` files
- Parent `pom.xml` (replaced by new single pom.xml)
- Old use case interface (`CreateSampleUseCase.java` sealed interface)
- Old use case impl (`CreateSampleUseCaseImpl.java`)
- `sample-third-party/src/test/java/com/mars/sample/ThirdPartyApplicationTests.java`
- All `.gitignore`, `.gitattributes`, `mvnw`, `mvnw.cmd` files from child modules

### Phase 6: ArchitectureTest (REWRITE)

Replace the simple 4-rule test from sample-business with a comprehensive test covering all ADR-021 rules:

```java
package com.mars.sample;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.mars.sample");
    }

    // === LAYER BOUNDARY RULES (ADR-021) ===
    //
    // NOTE: Domain is intentionally ALLOWED to use org.springframework.stereotype.Service
    // and org.springframework.transaction.annotation.Transactional per ADR-021 trade-off.
    // Only infrastructure-specific Spring imports (JPA, Kafka, Web, Servlet) are blocked.
    // See ADR-021 "What About the @Service Annotation in Domain?" section.

    @Test
    @DisplayName("Domain should not depend on infrastructure, api, or configuration")
    void domainShouldNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..api..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain should not use JPA annotations")
    void domainShouldNotUseJpa() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain should not use Kafka annotations")
    void domainShouldNotUseKafka() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.kafka..", "org.apache.kafka..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain should not use Spring Web or Servlet annotations")
    void domainShouldNotUseWebAnnotations() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .check(allClasses);
    }

    @Test
    @DisplayName("API should not access infrastructure persistence directly (ADR-021)")
    void apiShouldNotAccessRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure.persistence..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Infrastructure should not depend on API layer")
    void infrastructureShouldNotDependOnApi() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat()
                .resideInAPackage("..api..")
                .check(allClasses);
    }

    // === STRUCTURAL RULES ===

    @Test
    @DisplayName("Repository interfaces in domain should be interfaces (ports)")
    void repositoryInterfacesInDomainShouldBeInterfaces() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().resideInAPackage("..domain..")
                .should().beInterfaces()
                .check(allClasses);
    }

    @Test
    @DisplayName("Event publisher interfaces in domain should be interfaces (ports)")
    void eventPublisherInterfacesInDomainShouldBeInterfaces() {
        classes()
                .that().haveSimpleNameEndingWith("EventPublisher")
                .and().resideInAPackage("..domain..")
                .should().beInterfaces()
                .check(allClasses);
    }

    @Test
    @DisplayName("Repository implementations should reside in infrastructure.persistence")
    void repositoryImplsShouldResideInInfrastructurePersistence() {
        classes()
                .that().haveSimpleNameEndingWith("RepositoryImpl")
                .should().resideInAPackage("..infrastructure.persistence..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Use cases should reside in domain.usecase package")
    void useCasesShouldBeInDomainUsecasePackage() {
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..domain.usecase..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Use cases should be annotated with @Service")
    void useCasesShouldBeAnnotatedWithService() {
        classes()
                .that().resideInAPackage("..domain.usecase..")
                .and().areNotInterfaces()
                .and().haveSimpleNameEndingWith("UseCase")
                .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .check(allClasses);
    }

    @Test
    @DisplayName("API controllers should depend on domain use cases, not infrastructure directly")
    void apiShouldNotAccessInfrastructureDirectly() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .check(allClasses);
    }
}
```

### Phase 7: Migrate Tests

**CRITICAL RULE: @Test count MUST increase. Current count: exactly 21 tests. Expected post-migration count: >= 26 tests (ArchitectureTest expands from 4 to 12 rules; ApplicationContextTest replaces ThirdPartyApplicationTests; SampleServiceIntegrationTest 3 tests deduplicated with CreateSampleUseCaseIntegrationTest 1 test → 3 unique tests). Run `mvn test` before AND after migration to compare.**

**Test count mapping:**
| Source (Before) | Tests | Target (After) | Tests | Notes |
|---|---|---|---|---|
| SampleTest | 5 | domain/SampleTest | 5 | Package move only |
| CreateSampleUseCaseTest | 3 | domain/usecase/CreateSampleUseCaseTest | 3 | Change @InjectMocks target |
| ArchitectureTest (business) | 4 | ArchitectureTest (root) | 12 | Expanded: 6 layer + 6 structural rules |
| SampleControllerE2ETest | 4 | api/SampleControllerE2ETest | 4 | Package move only |
| CreateSampleUseCaseIntegrationTest | 1 | domain/usecase/CreateSampleUseCaseIntegrationTest | (merged) | Deduplicated with below |
| SampleServiceIntegrationTest | 3 | domain/usecase/CreateSampleUseCaseIntegrationTest | 3 | 1 duplicate removed |
| ThirdPartyApplicationTests | 1 | ApplicationContextTest | 1 | Upgraded: now boots Spring context |
| **TOTAL** | **21** | | **28** | **Net +7** |

**AbstractIntegrationTest.java** — MOVE to `src/test/java/com/mars/sample/AbstractIntegrationTest.java`:
- Update imports for `SampleJpaRepository` to `com.mars.sample.infrastructure.persistence.SampleJpaRepository`
- **SIMPLIFY datasource properties**: Since we now use `spring.datasource.url` (not `hikari.jdbcUrl`), remove the dual property registration:

```java
package com.mars.sample;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("sample")
        .withUsername("sample")
        .withPassword("sample")
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
    }
}
```

**ApplicationContextTest.java** — NEW (replaces deleted ThirdPartyApplicationTests).
Note: `ThirdPartyApplicationTests` was a no-op module test. `ApplicationContextTest` is fundamentally different and stronger — it boots the full Spring context with TestContainers, verifying all `@Service` beans wire correctly. This is a net improvement in quality.
```java
package com.mars.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationContextTest extends AbstractIntegrationTest {
    @Test
    @DisplayName("Application context should load successfully with all beans wired")
    void contextLoads() {
        // No assertions needed — Spring will fail to start if @Service wiring is broken
    }
}
```

**Domain unit tests** (from sample-business/src/test/) → `src/test/java/com/mars/sample/domain/`

**SampleTest.java** (5 tests):
- Update package to `com.mars.sample.domain`
- Update imports: `com.mars.sample.Sample` → `com.mars.sample.domain.Sample`
- Update imports: `com.mars.sample.BusinessException` → `com.mars.sample.domain.BusinessException`
- Update imports: `com.mars.sample.events.SampleCreated` → `com.mars.sample.domain.events.SampleCreated`

**CreateSampleUseCaseTest.java** (3 tests):
- Move to `src/test/java/com/mars/sample/domain/usecase/CreateSampleUseCaseTest.java`
- Update package to `com.mars.sample.domain.usecase`
- **IMPORTANT**: Change from testing `CreateSampleUseCaseImpl` to testing `CreateSampleUseCase` directly
- `@InjectMocks` works on `CreateSampleUseCase` because it is now a concrete class (not a sealed interface). Mockito constructs it with the `@Mock` dependencies. `@Service` and `@Transactional` annotations are ignored by Mockito (no Spring context in unit tests).

```java
package com.mars.sample.domain.usecase;

import com.mars.sample.domain.Sample;
import com.mars.sample.domain.SampleRepository;
import com.mars.sample.domain.events.SampleCreated;
import com.mars.sample.domain.events.SampleEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateSampleUseCaseTest {

    @Mock
    private SampleRepository sampleRepository;

    @Mock
    private SampleEventPublisher sampleEventPublisher;

    @InjectMocks
    private CreateSampleUseCase useCase;

    @Test
    @DisplayName("should create sample and return sampleId")
    void shouldCreateSampleAndReturnId() {
        var input = new CreateSampleUseCase.Input(UUID.randomUUID(), UUID.randomUUID());

        var result = useCase.execute(input);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should save sample to repository")
    void shouldSaveSampleToRepository() {
        var customerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var input = new CreateSampleUseCase.Input(customerId, productId);

        useCase.execute(input);

        ArgumentCaptor<Sample> captor = ArgumentCaptor.forClass(Sample.class);
        verify(sampleRepository).save(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
        assertThat(captor.getValue().productId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("should publish SampleCreated event")
    void shouldPublishSampleCreatedEvent() {
        var input = new CreateSampleUseCase.Input(UUID.randomUUID(), UUID.randomUUID());

        useCase.execute(input);

        verify(sampleEventPublisher).publish(any(SampleCreated.class));
    }
}
```

**CreateSampleUseCaseIntegrationTest.java** (1 test) + **SampleServiceIntegrationTest.java** (3 tests) → MERGE into `src/test/java/com/mars/sample/domain/usecase/CreateSampleUseCaseIntegrationTest.java`:

The `SampleServiceIntegrationTest` currently tests `SampleService` which is deleted. The `CreateSampleUseCaseIntegrationTest` has 1 test (`shouldCreateSampleAndPersist`). The merge results in 3 tests, not 4, because `shouldCreateSampleAndPersist` from the old integration test is **semantically identical** to `shouldCreateSampleAndPersistInDatabase` from the service test (both verify persistence with field-level assertions). This deduplication is intentional — no unique behavior is lost.

```java
package com.mars.sample.domain.usecase;

import com.mars.sample.AbstractIntegrationTest;
import com.mars.sample.infrastructure.persistence.SampleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateSampleUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateSampleUseCase createSampleUseCase;

    @Autowired
    private SampleJpaRepository sampleJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        sampleJpaRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM outbox_events");
    }

    @Test
    @DisplayName("should create sample and persist via repository")
    void shouldCreateSampleAndPersist() {
        var customerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var input = new CreateSampleUseCase.Input(customerId, productId);

        var sampleId = createSampleUseCase.execute(input);

        var entity = sampleJpaRepository.findById(sampleId).orElseThrow();
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getProductId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("should create outbox event when sample is created")
    void shouldCreateOutboxEventWhenSampleIsCreated() {
        var customerId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var input = new CreateSampleUseCase.Input(customerId, productId);

        var sampleId = createSampleUseCase.execute(input);

        var outboxEvents = jdbcTemplate.queryForList(
            "SELECT event_type, aggregate_id, channel FROM outbox_events WHERE aggregate_id = ?",
            sampleId.toString()
        );
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().get("event_type")).isEqualTo("SampleCreated");
        assertThat(outboxEvents.getFirst().get("channel")).isEqualTo("sample.sample.created");
    }

    @Test
    @DisplayName("should create multiple samples independently")
    void shouldCreateMultipleSamplesIndependently() {
        var sampleId1 = createSampleUseCase.execute(
                new CreateSampleUseCase.Input(UUID.randomUUID(), UUID.randomUUID()));
        var sampleId2 = createSampleUseCase.execute(
                new CreateSampleUseCase.Input(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(sampleId1).isNotEqualTo(sampleId2);
        assertThat(sampleJpaRepository.findById(sampleId1)).isPresent();
        assertThat(sampleJpaRepository.findById(sampleId2)).isPresent();
    }
}
```

**SampleControllerE2ETest.java** (4 tests) → `src/test/java/com/mars/sample/api/SampleControllerE2ETest.java`:
- Update package to `com.mars.sample.api`
- Update import: `com.mars.sample.AbstractIntegrationTest` (stays the same — root test package)
- Keep all REST Assured assertions unchanged — controller behavior is identical

### Phase 8: Delete Old Module Directories

After all files are moved and verified:
1. Delete `sample-business/` directory entirely
2. Delete `sample-data-provider/` directory entirely
3. Delete `sample-app/` directory entirely
4. Delete `sample-third-party/` directory entirely
5. Delete old parent `pom.xml` (already replaced)

### Phase 9: Update README.md

Rewrite `README.md` to reflect the single-module structure. The new README must include:

**Architecture diagram** — show packages instead of modules:
```
mars-sample-service/
├── src/main/java/com/mars/sample/
│   ├── Application.java
│   ├── domain/           # Pure business logic (NO JPA, NO Kafka, NO Web)
│   │   ├── Sample.java, SampleRepository.java, BusinessException.java
│   │   ├── common/       # DomainEvent, DomainResult, Channel
│   │   ├── events/       # SampleEvent, SampleCreated, SampleEventPublisher
│   │   └── usecase/      # CreateSampleUseCase (@Service)
│   ├── infrastructure/   # Adapters (JPA, Outbox, Kafka, Configuration)
│   │   ├── persistence/  # SampleEntity, SampleJpaRepository, SampleRepositoryImpl
│   │   ├── messaging/    # SampleEventPublisherImpl, KafkaProducerHandler
│   │   └── configuration/ # AuditingConfiguration
│   └── api/              # REST Controllers + DTOs
│       ├── SampleController.java
│       ├── CreateSampleRequest.java
│       └── CreateSampleResponse.java
```

**Updated "How to Use This Template"** section (no more module renaming):
```markdown
### Step 1: Copy the Template
Copy this directory as your new service:
\`\`\`bash
cp -r templates/mars-microservice-standard reference-system/mars-{your-domain}-service
\`\`\`

### Step 2: Rename the Domain
Replace all occurrences of `Sample`/`sample` with your domain name:
\`\`\`bash
cd reference-system/mars-{your-domain}-service
# Rename Java files
find src -name "Sample*" -type f | while read f; do mv "$f" "$(echo $f | sed 's/Sample/{YourDomain}/g')"; done
# Update package contents, imports, and references
find src -name "*.java" | xargs sed -i 's/Sample/{YourDomain}/g; s/sample/{yourdomain}/g'
# Update pom.xml
sed -i 's/sample/{yourdomain}/g' pom.xml
# Update application.yaml
sed -i 's/sample/{yourdomain}/g' src/main/resources/application.yaml
# Update SQL migration
mv src/main/resources/db/migration/V1__create_samples_table.sql src/main/resources/db/migration/V1__create_{yourdomain}s_table.sql
sed -i 's/samples/{yourdomain}s/g' src/main/resources/db/migration/V1__create_{yourdomain}s_table.sql
\`\`\`

### Step 3: Add Your Domain Fields
Edit `domain/{YourDomain}.java` to replace `customerId` and `productId` with your domain's fields.

### Step 4: Update the Migration
Edit `V1__create_{yourdomain}s_table.sql` to match your domain's table structure.

### Step 5: Update the Port
Configure database credentials and Kafka settings in `application.yaml`.
```

**Updated build commands** (no `-pl` flags):
```bash
# Compile
mvn clean compile

# Run unit tests (fast, no Docker needed)
mvn test -Dtest="com.mars.sample.domain.*,com.mars.sample.ArchitectureTest"

# Run all tests (requires Docker for TestContainers)
mvn test

# Full build
mvn clean verify
```

**Key patterns section** — update to explain:
- ADR-021: Package-based Onion Architecture with ArchUnit enforcement
- Use cases are `@Service` classes directly (no sealed interfaces)
- No `UseCaseConfiguration.java` needed
- ArchUnit replaces multi-module compile-time enforcement

---

## Task List

```yaml
Task 1: Create single pom.xml
- WRITE: templates/mars-microservice-standard/pom.xml (merged)
- VALIDATE: mvn validate (pom structure correct)

Task 2: Create domain package structure
- CREATE: src/main/java/com/mars/sample/domain/
- MOVE+MODIFY: All business domain files (update packages)
- MOVE+MODIFY: common/ and events/ subpackages
- TRANSFORM: CreateSampleUseCase (sealed interface+impl → @Service class)
- VALIDATE: No compilation errors in domain package

Task 3: Create infrastructure package
- CREATE: src/main/java/com/mars/sample/infrastructure/persistence/
- CREATE: src/main/java/com/mars/sample/infrastructure/messaging/
- CREATE: src/main/java/com/mars/sample/infrastructure/configuration/
- MOVE+MODIFY: All infrastructure files (update packages + imports)
- VALIDATE: No compilation errors

Task 4: Create api package
- MOVE+MODIFY: SampleController to inject CreateSampleUseCase directly
- MOVE+MODIFY: CreateSampleRequest, CreateSampleResponse (update packages)
- VALIDATE: No compilation errors

Task 5: Application and resources
- MOVE: Application.java (stays at root package, unchanged)
- MODIFY: application.yaml (enable Flyway auto-config, update datasource)
- MOVE: V1__create_samples_table.sql to src/main/resources/db/migration/
- VALIDATE: mvn clean compile passes

Task 6: Write ArchitectureTest
- WRITE: src/test/java/com/mars/sample/ArchitectureTest.java
- VALIDATE: mvn test -Dtest=ArchitectureTest passes

Task 7: Migrate tests
- MOVE+MODIFY: AbstractIntegrationTest (simplify datasource properties)
- MOVE+MODIFY: SampleTest to domain/ test package
- MOVE+MODIFY+MERGE: CreateSampleUseCaseTest + integration tests
- MOVE+MODIFY: SampleControllerE2ETest to api/ test package
- WRITE: ApplicationContextTest (replaces ThirdPartyApplicationTests)
- VALIDATE: mvn test passes (all tests)

Task 8: Delete old module directories
- DELETE: sample-business/, sample-data-provider/, sample-app/, sample-third-party/
- VALIDATE: mvn clean verify passes (full build)

Task 9: Update README.md
- REWRITE: README.md to reflect single-module structure
- UPDATE: Build commands, architecture diagram, usage instructions
- VALIDATE: Content accuracy

Task 10: Final verification
- RUN: mvn clean verify
- VERIFY: All tests pass
- VERIFY: @Test count >= 21
```

---

## Validation Loop

### Level 1: Compilation
```bash
cd templates/mars-microservice-standard
mvn clean compile
# Expected: BUILD SUCCESS
```

### Level 2: Architecture Tests
```bash
mvn test -Dtest=ArchitectureTest
# Expected: All architecture rules pass
# Key: domain package has NO dependency on infrastructure or api
```

### Level 3: Unit Tests (domain)
```bash
mvn test -Dtest="com.mars.sample.domain.**"
# Expected: SampleTest (5), CreateSampleUseCaseTest (3) all pass
# These run WITHOUT Spring context, WITHOUT database
```

### Level 4: Integration Tests
```bash
# Ensure Docker is running for TestContainers
docker info
mvn test
# Expected: ALL tests pass (unit + integration + E2E + architecture)
```

### Level 5: Full Build
```bash
mvn clean verify
# Expected: BUILD SUCCESS
```

---

## Final Validation Checklist

- [ ] Single pom.xml (no `<modules>`, packaging=jar)
- [ ] All domain classes in `com.mars.sample.domain.*`
- [ ] All infrastructure classes in `com.mars.sample.infrastructure.*`
- [ ] All API classes in `com.mars.sample.api.*`
- [ ] No sealed interface on use cases
- [ ] No UseCaseConfiguration.java
- [ ] No SampleService.java wrapper
- [ ] No DataSourceConfiguration.java (Spring Boot auto-config)
- [ ] No FlywayConfiguration.java (Spring Boot auto-config)
- [ ] Use case is @Service class with @Transactional
- [ ] ArchitectureTest enforces: domain cannot depend on infrastructure/api
- [ ] Domain has NO JPA, NO Kafka, NO Spring Web imports
- [ ] All existing test scenarios preserved and passing
- [ ] application.yaml at correct location with Flyway enabled
- [ ] Flyway migration at correct location
- [ ] README.md updated
- [ ] `samples.http` preserved byte-for-byte identical (verify with `git diff`)
- [ ] `mvn clean verify` passes

---

## Out of Scope

- **Dockerfile**: No Dockerfile exists in the template currently; Docker/Jib configuration is a separate PRP
- **Kubernetes manifests**: Covered by separate infrastructure PRP
- **mars-cli template registration**: Separate PRP when CLI is implemented
- **docker-compose.yml**: Local dev environment setup is not part of this structural migration

---

## Anti-Patterns to Avoid

- **DO NOT** put `@Entity` or JPA annotations in domain package
- **DO NOT** import `jakarta.persistence.*` in domain package
- **DO NOT** import `org.springframework.kafka.*` in domain package
- **DO NOT** create new interfaces for use cases (use concrete @Service classes)
- **DO NOT** create UseCaseConfiguration.java (component scanning handles it)
- **DO NOT** create service wrapper classes around use cases
- **DO NOT** change any business logic or test assertions (this is a structural migration only)
- **DO NOT** modify application.yaml beyond what's needed for the migration
- **DO NOT** skip the ArchitectureTest — it replaces multi-module enforcement
- **DO NOT** forget to add `@Service` and `@Transactional` on the use case class
- **DO NOT** add `@Repository` or `@Component` annotations inside the domain package — only `@Service` and `@Transactional` are permitted per ADR-021 trade-off
- **DO NOT** reduce the @Test count — every test scenario must be preserved (target: >= 26 tests)
- **DO NOT** keep the manual DataSource/Flyway configurations — use Spring Boot auto-config

---

## Confidence Score: 9/10

**Rationale:**
- [x] Every current source file has been read and mapped to target location
- [x] All pom.xml dependencies identified and merged
- [x] Use case transformation pattern is clear with concrete code examples
- [x] All gotchas documented (Jackson 3, TestContainers, ArchUnit, Outbox starter)
- [x] DataSource/Flyway simplification documented (removing manual configs)
- [x] Validation gates are executable at each phase
- [x] No ambiguous requirements — this is a pure structural migration
- [x] Test migration plan includes exact count mapping (21 → 28 tests)
- [x] ArchitectureTest includes all ADR-021 rules + @Service enforcement + generic port rules
- [x] Spring context smoke test ensures wiring correctness
- [x] README update included with skeleton "How to Use" instructions
- [x] Multi-perspective review: 3 MUST FIX (PO) + 2 MUST FIX (QA) all addressed
- [x] Template renameability verified (no hard-coded class references in ArchUnit rules)
- [x] ADR-021 trade-off on @Service in domain explicitly documented

---

## Approval Record
- **Architect**: APPROVED — Architecture compliant. Onion Architecture preserved via packages + ArchUnit. All ADR-021 rules enforced. `@Service`/`@Transactional` in domain explicitly documented as intentional trade-off. Port/Adapter pattern maintained. DataSource/Flyway simplification to Spring Boot auto-config is correct.
- **Product Owner**: APPROVED WITH NOTES — Requirements complete. All MUST FIX items addressed: post-migration test count floor specified (>= 28), hard-coded class reference replaced with pattern-based ArchUnit rules for template renameability, README "How to Use" skeleton provided for single-module workflow. SHOULD FIX items addressed: Out of Scope section added, samples.http verification in checklist, integration test deduplication explicitly documented, Anti-Patterns section expanded.
- **Quality Analyst**: APPROVED WITH NOTES — Test strategy sound. All MUST FIX items addressed: ADR-021 Spring annotation trade-off documented in ArchitectureTest comments, full CreateSampleUseCaseTest code provided with @InjectMocks explanation. SHOULD FIX items addressed: integration test merge deduplication stated, ApplicationContextTest upgrade noted, Repository and EventPublisher generic ArchUnit rules added, RepositoryImpl location rule added. Pre-existing gaps noted (no @Transactional rollback test, no SampleEventPublisherImpl unit test) — these are not regressions and can be addressed in a follow-up PRP.
