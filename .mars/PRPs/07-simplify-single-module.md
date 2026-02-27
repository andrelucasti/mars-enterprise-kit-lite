name: "PRP: Simplify Mars Enterprise Kit Lite to Single Maven Module"
description: |
  Migrate mars-enterprise-kit-lite from 3 Maven modules (business, data-provider, app) to a single
  module with package-based Onion Architecture enforced by ArchUnit. Remove sealed interface ceremony
  on use cases, eliminate UseCaseConfiguration.java and OrderService.java wrapper, rename
  DataSourceConfiguration.java to AuditingConfiguration.java, and use @Service directly.
  Update CLAUDE.md and README.md to reflect new structure.

---

## Goal

Restructure Mars Enterprise Kit Lite from a 3-module Maven project (business, data-provider, app) into a **single Maven module** with package-based layer separation enforced by ArchUnit tests. The Dual Write anti-pattern and Chaos Testing features must be fully preserved.

## Why

- Current 3-module structure creates excessive boilerplate for an educational project
- `sealed interface permits SingleImpl` eliminates polymorphism (the only benefit of interfaces)
- `UseCaseConfiguration.java` manually wires beans that `@Service` + component scanning handles automatically
- `OrderService.java` is a pure pass-through wrapper adding no value over direct use case injection
- Market research shows 60-70% of companies use single-module + packages; only 5-10% use multi-module per service
- Reduces 4 pom.xml files to 1, removes ~10 structural/boilerplate files
- ArchUnit provides equivalent compile-time-like enforcement at the package level
- **As an educational template**, simplicity is paramount — developers should learn Onion Architecture without Maven module friction

## What

### Success Criteria
- [ ] Single `pom.xml` replaces 4 pom.xml files (packaging=jar, no `<modules>`)
- [ ] All source files reorganized into `domain/`, `infrastructure/`, `api/` packages
- [ ] Use case interfaces removed; use cases become `@Service` classes directly
- [ ] `UseCaseConfiguration.java` deleted
- [ ] `OrderService.java` (service wrapper) deleted — use cases ARE the services now
- [ ] `DataSourceConfiguration.java` renamed to `AuditingConfiguration.java` and moved
- [ ] `TestApplication.java` (data-provider) deleted — main Application.java is used
- [ ] New comprehensive `ArchitectureTest.java` with ArchUnit rules enforcing package boundaries
- [ ] `mvn clean verify` passes — **all existing test scenarios pass with zero behavioral changes**
- [ ] **@Test count MUST increase** — current total is exactly 38 tests; after migration `mvn test` must report >= 50 tests
- [ ] `CLAUDE.md` and `README.md` updated to reflect single-module structure
- [ ] Application starts and connects to database successfully (smoke test: `/actuator/health` returns UP)
- [ ] Chaos testing (`POST /chaos/phantom-event`) still works identically
- [ ] Dual Write behavior is completely unchanged

### Git Commit Strategy
This migration should be done in a single feature branch. Recommended commits:
1. `feat: create single-module structure with merged pom.xml`
2. `refactor: move domain classes to domain/ package`
3. `refactor: move infrastructure classes to infrastructure/ package`
4. `refactor: move api classes and Application`
5. `refactor: transform use cases from sealed interfaces to @Service classes`
6. `test: add comprehensive ArchitectureTest with ArchUnit rules`
7. `test: migrate all tests to new package structure`
8. `chore: delete old module directories and update docs`
9. `test: verify full build with mvn clean verify`

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project rules and conventions (CRITICAL - read first)

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Full project context document

- file: .mars/PRPs/06-simplify-project.md
  why: Reference PRP from mars-enterprise-kit (similar migration, different project)
```

### Known Gotchas & Critical Rules

**1. Jackson 3 in Spring Boot 4 (CRITICAL)**
```java
// Spring Boot 4 uses Jackson 3 (tools.jackson.*) NOT Jackson 2 (com.fasterxml.jackson.*)
// Auto-configured bean: tools.jackson.databind.json.JsonMapper
// Exception: tools.jackson.core.JacksonException
// OrderCreatedPublisher and OrderCancelledConsumer already use correct Jackson 3 imports — preserve them
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
```

**2. TestContainers BOM Version**
```xml
<!-- Current version is 2.0.3 — NOT the same as the reference PRP's 1.21.3 -->
<testcontainers.version>2.0.3</testcontainers.version>
<!-- TestContainers artifact IDs use different naming than older versions -->
<artifactId>testcontainers-junit-jupiter</artifactId>
<artifactId>testcontainers-postgresql</artifactId>
<artifactId>testcontainers-kafka</artifactId>
```

**3. ArchUnit + Java 25**
```xml
<!-- Must use archunit-junit5 1.4.1+ for Java 25 bytecode support -->
<artifactId>archunit-junit5</artifactId>
<version>1.4.1</version>
```

**4. AbstractIntegrationTest Pattern**
```java
// Uses @MockitoBean for OrderEventPublisher to avoid needing real Kafka
// Kafka bootstrap-servers set to localhost:0 (no real Kafka)
// Kafka listener auto-startup set to false
// Static PostgreSQL container shared across all integration tests
@MockitoBean
private OrderEventPublisher orderEventPublisher;
```

**5. AOP Pointcut for Chaos Testing (CRITICAL)**
```java
// PhantomEventChaosAspect uses a HARDCODED pointcut expression:
@Around("execution(* io.mars.lite.api.chaos.ChaosOrderExecutor.execute(..))")
// ChaosOrderExecutor MUST remain in io.mars.lite.api.chaos package
// or this pointcut MUST be updated to match the new package
```

**6. @Transactional Propagation with Chaos Tests**
```java
// ChaosService (@Transactional) → ChaosOrderExecutor → CreateOrderUseCase (@Transactional)
// With default propagation REQUIRED, the inner @Transactional joins the outer one
// The AOP throws AFTER proceed() → outer TX rolls back (DB rolled back, Kafka event already sent)
// This preserves identical Phantom Event behavior
```

**7. Component Scanning**
```java
// Application.java is at io.mars.lite — @SpringBootApplication scans io.mars.lite.**
// All @Service, @Component, @Repository, @RestController in subpackages are auto-discovered
// No UseCaseConfiguration needed — @Service on use case classes is enough
```

**8. REST Assured Version**
```xml
<!-- Current project uses REST Assured 6.0.0, NOT 5.5.0 -->
<version>6.0.0</version>
```

**9. OrderCancelledConsumer Dependency Change**
```java
// BEFORE: depends on OrderService (deleted)
// AFTER: depends on CancelOrderUseCase directly
// CancelOrderUseCase is now @Service with @Transactional, so the consumer
// gets transactional behavior from the use case (same as before via OrderService)
```

**10. OrderController GET Endpoint**
```java
// BEFORE: controller calls OrderService.findById() for GET /orders/{id}
// AFTER: controller injects OrderRepository (domain port) directly for reads
// This is clean — api layer is allowed to depend on domain layer (ports)
// No need for a wrapper service just for a simple read
```

**11. data-provider Spring Boot Test Dependency**
```xml
<!-- data-provider uses spring-boot-starter-data-jpa-test (NOT spring-boot-starter-test) -->
<!-- In the merged POM, both spring-boot-starter-test AND spring-boot-starter-data-jpa-test are needed -->
<artifactId>spring-boot-starter-data-jpa-test</artifactId>
```

---

## Current Structure → Target Structure

### Current (3 modules, 4 pom.xml)
```
mars-enterprise-kit-lite/
├── pom.xml (parent POM, packaging=pom)
├── CLAUDE.md
├── README.md
├── docker-compose.yml
├── business/                                    # Pure Java domain (ZERO frameworks)
│   ├── pom.xml
│   └── src/main/java/io/mars/lite/order/
│       ├── Order.java                           # Aggregate Root (record)
│       ├── OrderStatus.java                     # Value Object (enum)
│       ├── OrderItem.java                       # Value Object (record)
│       ├── OrderCreatedEvent.java               # Domain Event (record)
│       ├── DomainResult.java                    # Generic wrapper (record)
│       ├── OrderRepository.java                 # Port (interface)
│       ├── OrderEventPublisher.java             # Port (interface)
│       ├── BusinessException.java               # Domain Exception
│       └── usecase/
│           ├── CreateOrderUseCase.java          # Sealed interface
│           ├── CreateOrderUseCaseImpl.java       # Record implementation
│           ├── CancelOrderUseCase.java          # Sealed interface
│           └── CancelOrderUseCaseImpl.java      # Record implementation
│   └── src/test/java/io/mars/lite/order/
│       ├── OrderTest.java (9 tests)
│       ├── OrderItemTest.java (6 tests)
│       └── usecase/
│           ├── CreateOrderUseCaseTest.java (2 tests)
│           └── CancelOrderUseCaseTest.java (2 tests)
├── data-provider/                               # Infrastructure (JPA, Flyway)
│   ├── pom.xml
│   └── src/main/java/io/mars/lite/
│       ├── configuration/
│       │   └── DataSourceConfiguration.java     # Just @EnableJpaAuditing
│       └── order/
│           ├── OrderEntity.java                 # JPA Entity
│           ├── OrderItemEntity.java             # JPA Entity
│           ├── OrderJpaRepository.java          # Spring Data JPA
│           └── OrderRepositoryImpl.java         # Adapter (implements port)
│   └── src/main/resources/db/migration/
│       └── V1__create_orders_table.sql
│   └── src/test/java/io/mars/lite/
│       ├── TestApplication.java (test context)
│       └── order/OrderRepositoryImplIntegrationTest.java (4 tests)
├── app/                                         # API entry point
│   ├── pom.xml
│   └── src/main/java/io/mars/lite/
│       ├── Application.java
│       ├── configuration/
│       │   ├── UseCaseConfiguration.java        # Manual bean wiring (DELETE)
│       │   └── KafkaConfiguration.java          # Kafka producer config
│       └── api/
│           ├── GlobalExceptionHandler.java
│           ├── order/
│           │   ├── OrderController.java
│           │   ├── OrderService.java            # @Service wrapper (DELETE)
│           │   ├── CreateOrderRequest.java
│           │   └── OrderResponse.java
│           ├── event/
│           │   ├── OrderCreatedPublisher.java   # Kafka publisher (implements port)
│           │   └── OrderCancelledConsumer.java   # Kafka consumer
│           └── chaos/
│               ├── ChaosController.java
│               ├── ChaosService.java
│               ├── ChaosOrderExecutor.java
│               ├── PhantomEventChaosAspect.java
│               ├── PhantomEventSimulationException.java
│               └── PhantomEventReport.java
│   └── src/main/resources/application.yaml
│   └── src/test/java/io/mars/lite/
│       ├── AbstractIntegrationTest.java
│       ├── api/order/
│       │   ├── OrderServiceIntegrationTest.java (4 tests)
│       │   └── OrderControllerE2ETest.java (5 tests)
│       └── api/chaos/
│           ├── PhantomEventChaosAspectTest.java (3 tests)
│           └── ChaosControllerE2ETest.java (3 tests)
```

### Target (1 module, 1 pom.xml)
```
mars-enterprise-kit-lite/
├── pom.xml                                      # Single POM (all deps merged)
├── CLAUDE.md                                    # UPDATED to reflect new structure
├── README.md                                    # UPDATED to reflect new structure
├── docker-compose.yml                           # UNCHANGED
├── src/
│   ├── main/
│   │   ├── java/io/mars/lite/
│   │   │   ├── Application.java                 # MOVE: unchanged
│   │   │   │
│   │   │   ├── domain/                          # FROM: business/
│   │   │   │   ├── Order.java                   # MOVE: update package
│   │   │   │   ├── OrderStatus.java             # MOVE: update package
│   │   │   │   ├── OrderItem.java               # MOVE: update package
│   │   │   │   ├── OrderCreatedEvent.java       # MOVE: update package
│   │   │   │   ├── DomainResult.java            # MOVE: update package
│   │   │   │   ├── OrderRepository.java         # MOVE: update package (port interface)
│   │   │   │   ├── OrderEventPublisher.java     # MOVE: update package (port interface)
│   │   │   │   ├── BusinessException.java       # MOVE: update package
│   │   │   │   └── usecase/
│   │   │   │       ├── CreateOrderUseCase.java  # TRANSFORM: @Service class (merge interface+impl)
│   │   │   │       └── CancelOrderUseCase.java  # TRANSFORM: @Service class (merge interface+impl)
│   │   │   │
│   │   │   ├── infrastructure/                  # FROM: data-provider/ + app/ infra
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── OrderEntity.java         # MOVE: update package + imports
│   │   │   │   │   ├── OrderItemEntity.java     # MOVE: update package
│   │   │   │   │   ├── OrderJpaRepository.java  # MOVE: update package
│   │   │   │   │   └── OrderRepositoryImpl.java # MOVE: update package + imports
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── OrderCreatedPublisher.java   # MOVE: update package + imports
│   │   │   │   │   └── OrderCancelledConsumer.java  # MOVE: update package + imports + dependency change
│   │   │   │   └── configuration/
│   │   │   │       ├── AuditingConfiguration.java   # RENAME from DataSourceConfiguration
│   │   │   │       └── KafkaConfiguration.java      # MOVE: update package
│   │   │   │
│   │   │   └── api/                             # FROM: app/api/
│   │   │       ├── OrderController.java         # MODIFY: inject CreateOrderUseCase + OrderRepository
│   │   │       ├── CreateOrderRequest.java      # MOVE: update package
│   │   │       ├── OrderResponse.java           # MOVE: update package
│   │   │       ├── GlobalExceptionHandler.java  # MOVE: update package + imports
│   │   │       └── chaos/                       # MOVE: update packages + imports
│   │   │           ├── ChaosController.java
│   │   │           ├── ChaosService.java
│   │   │           ├── ChaosOrderExecutor.java
│   │   │           ├── PhantomEventChaosAspect.java
│   │   │           ├── PhantomEventSimulationException.java
│   │   │           └── PhantomEventReport.java
│   │   │
│   │   └── resources/
│   │       ├── application.yaml                 # MOVE: unchanged
│   │       └── db/migration/
│   │           └── V1__create_orders_table.sql  # MOVE: from data-provider
│   │
│   └── test/
│       └── java/io/mars/lite/
│           ├── ArchitectureTest.java            # NEW: comprehensive ArchUnit rules (12 tests)
│           ├── AbstractIntegrationTest.java     # MOVE: update imports
│           ├── ApplicationContextTest.java      # NEW: Spring context smoke test (1 test)
│           │
│           ├── domain/                          # FROM: business/ tests
│           │   ├── OrderTest.java (9 tests)
│           │   ├── OrderItemTest.java (6 tests)
│           │   └── usecase/
│           │       ├── CreateOrderUseCaseTest.java (2 tests)       # MODIFY: test @Service class
│           │       ├── CancelOrderUseCaseTest.java (2 tests)       # MODIFY: test @Service class
│           │       ├── CreateOrderUseCaseIntegrationTest.java       # FROM: OrderServiceIntegrationTest (2 tests)
│           │       └── CancelOrderUseCaseIntegrationTest.java       # FROM: OrderServiceIntegrationTest (2 tests)
│           │
│           ├── infrastructure/
│           │   └── persistence/
│           │       └── OrderRepositoryImplIntegrationTest.java (4 tests)  # MOVE: extend AbstractIntegrationTest
│           │
│           └── api/
│               ├── OrderControllerE2ETest.java (5 tests)           # MOVE: update imports
│               └── chaos/
│                   ├── PhantomEventChaosAspectTest.java (3 tests)  # MOVE: update imports
│                   └── ChaosControllerE2ETest.java (3 tests)       # MOVE: update imports
```

---

## Implementation Blueprint

### Phase 1: Create New Single-Module POM

**File: `pom.xml`**

Merge all 4 pom.xml into one. Key requirements:
- Parent: `spring-boot-starter-parent:4.0.3`
- Packaging: `jar` (NOT `pom`)
- NO `<modules>` section
- ALL dependencies from all 3 child modules merged (deduplicated)
- `spring-boot-maven-plugin` with `mainClass=io.mars.lite.Application`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.3</version>
        <relativePath/>
    </parent>
    <groupId>io.mars.lite</groupId>
    <artifactId>mars-enterprise-kit-lite</artifactId>
    <version>1.0.0</version>
    <name>Mars Enterprise Kit Lite</name>
    <description>Mars Enterprise Kit Lite - Educational microservice with Onion Architecture and Dual Write pattern</description>

    <properties>
        <java.version>25</java.version>
        <testcontainers.version>2.0.3</testcontainers.version>
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

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-kafka</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <version>6.0.0</version>
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
                    <mainClass>io.mars.lite.Application</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Phase 2: Domain Package (from business/)

Move files from `business/src/main/java/io/mars/lite/order/` to `src/main/java/io/mars/lite/domain/`.

**For each file, update the package declaration** from `package io.mars.lite.order;` to `package io.mars.lite.domain;` (or appropriate subpackage).

**Files to move (unchanged except package):**
- `Order.java` → `domain/Order.java` — update package + internal imports
- `OrderStatus.java` → `domain/OrderStatus.java` — update package
- `OrderItem.java` → `domain/OrderItem.java` — update package
- `OrderCreatedEvent.java` → `domain/OrderCreatedEvent.java` — update package + imports
- `DomainResult.java` → `domain/DomainResult.java` — update package
- `OrderRepository.java` → `domain/OrderRepository.java` — update package
- `OrderEventPublisher.java` → `domain/OrderEventPublisher.java` — update package
- `BusinessException.java` → `domain/BusinessException.java` — update package

**Use Cases — TRANSFORM (merge sealed interface + record impl into @Service class):**

**CreateOrderUseCase.java** — merge `CreateOrderUseCase` (sealed interface) + `CreateOrderUseCaseImpl` (record):
```java
package io.mars.lite.domain.usecase;

import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderEventPublisher;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               OrderEventPublisher orderEventPublisher) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository cannot be null");
        this.orderEventPublisher = Objects.requireNonNull(orderEventPublisher, "orderEventPublisher cannot be null");
    }

    @Transactional
    public UUID execute(final Input input) {
        var result = Order.create(input.customerId(), input.items());
        orderRepository.save(result.domain());
        orderEventPublisher.publish(result.event());
        return result.domain().id();
    }

    public record Input(Set<OrderItem> items, UUID customerId) {}
}
```

**CancelOrderUseCase.java** — merge `CancelOrderUseCase` (sealed interface) + `CancelOrderUseCaseImpl` (record):
```java
package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;

    public CancelOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository cannot be null");
    }

    @Transactional
    public void execute(UUID orderId) {
        var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found: " + orderId));
        var cancelled = order.cancel();
        orderRepository.update(cancelled);
    }
}
```

### Phase 3: Infrastructure Package

**persistence/ subpackage** (from data-provider/):
- `OrderEntity.java` → `infrastructure/persistence/OrderEntity.java`
  - Update package to `io.mars.lite.infrastructure.persistence`
  - Update imports: `io.mars.lite.order.*` → `io.mars.lite.domain.*`
- `OrderItemEntity.java` → `infrastructure/persistence/OrderItemEntity.java`
  - Update package only
- `OrderJpaRepository.java` → `infrastructure/persistence/OrderJpaRepository.java`
  - Update package only (OrderEntity is in same package)
- `OrderRepositoryImpl.java` → `infrastructure/persistence/OrderRepositoryImpl.java`
  - Update package to `io.mars.lite.infrastructure.persistence`
  - Update imports: `io.mars.lite.order.*` → `io.mars.lite.domain.*`

**messaging/ subpackage** (from app/api/event/):
- `OrderCreatedPublisher.java` → `infrastructure/messaging/OrderCreatedPublisher.java`
  - Update package to `io.mars.lite.infrastructure.messaging`
  - Update imports: `io.mars.lite.order.*` → `io.mars.lite.domain.*`
  - **Preserve Jackson 3 imports**: `tools.jackson.core.JacksonException`, `tools.jackson.databind.ObjectMapper`
- `OrderCancelledConsumer.java` → `infrastructure/messaging/OrderCancelledConsumer.java`
  - Update package to `io.mars.lite.infrastructure.messaging`
  - **CRITICAL CHANGE**: Replace `OrderService` dependency with `CancelOrderUseCase`
  - Update imports: `io.mars.lite.order.BusinessException` → `io.mars.lite.domain.BusinessException`
  - Update imports: `io.mars.lite.api.order.OrderService` → REMOVED
  - Add import: `io.mars.lite.domain.usecase.CancelOrderUseCase`
  - Change call: `orderService.cancelOrder(payload.orderId())` → `cancelOrderUseCase.execute(payload.orderId())`

```java
package io.mars.lite.infrastructure.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.usecase.CancelOrderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);
    private final CancelOrderUseCase cancelOrderUseCase;
    private final ObjectMapper objectMapper;

    public OrderCancelledConsumer(CancelOrderUseCase cancelOrderUseCase, ObjectMapper objectMapper) {
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.cancelled", groupId = "order-service")
    public void onOrderCancelled(String message) {
        try {
            var payload = objectMapper.readValue(message, OrderCancelledPayload.class);
            log.info("Received order.cancelled event for orderId={}", payload.orderId());
            cancelOrderUseCase.execute(payload.orderId());
        } catch (JacksonException e) {
            log.error("Failed to deserialize order.cancelled event", e);
        } catch (BusinessException e) {
            log.warn("Failed to cancel order: {}", e.getMessage());
        }
    }

    record OrderCancelledPayload(UUID eventId, UUID orderId, String reason, Instant occurredAt) {}
}
```

**configuration/ subpackage:**
- `AuditingConfiguration.java` → `infrastructure/configuration/AuditingConfiguration.java`
  - **RENAME** from `DataSourceConfiguration` to `AuditingConfiguration` (reflects actual purpose)
  - Update package to `io.mars.lite.infrastructure.configuration`
  - Content unchanged (just `@Configuration @EnableJpaAuditing`)
- `KafkaConfiguration.java` → `infrastructure/configuration/KafkaConfiguration.java`
  - Update package to `io.mars.lite.infrastructure.configuration`
  - Content unchanged (preserve Dual Write timeout values)

**DELETE these files (no longer needed):**
- `app/src/main/java/io/mars/lite/configuration/UseCaseConfiguration.java` — @Service replaces manual wiring
- `app/src/main/java/io/mars/lite/api/order/OrderService.java` — use cases are injected directly
- `data-provider/src/test/java/io/mars/lite/TestApplication.java` — main Application.java used in tests
- All 3 child module `pom.xml` files
- Old use case interfaces (`CreateOrderUseCase.java` sealed interface, `CancelOrderUseCase.java` sealed interface)
- Old use case impls (`CreateOrderUseCaseImpl.java`, `CancelOrderUseCaseImpl.java`)

### Phase 4: API Package (from app/api/)

**OrderController.java** — MODIFY to inject `CreateOrderUseCase` + `OrderRepository` directly (replaces `OrderService`):
```java
package io.mars.lite.api;

import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import io.mars.lite.domain.usecase.CreateOrderUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderRepository orderRepository;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           OrderRepository orderRepository) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> createOrder(
            @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
            .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());

        var input = new CreateOrderUseCase.Input(items, request.customerId());
        var orderId = createOrderUseCase.execute(input);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("orderId", orderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
            .map(OrderResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

- `CreateOrderRequest.java` → `api/CreateOrderRequest.java` — update package only
- `OrderResponse.java` → `api/OrderResponse.java` — update package + imports (`io.mars.lite.order.Order` → `io.mars.lite.domain.Order`)
- `GlobalExceptionHandler.java` → `api/GlobalExceptionHandler.java` — update package + imports (`io.mars.lite.order.BusinessException` → `io.mars.lite.domain.BusinessException`)

**Chaos package** — update packages and imports:
- `ChaosController.java` → `api/chaos/ChaosController.java`
  - Update import: `io.mars.lite.api.order.CreateOrderRequest` → `io.mars.lite.api.CreateOrderRequest`
  - Update imports: `io.mars.lite.order.*` → `io.mars.lite.domain.*`
- `ChaosService.java` → `api/chaos/ChaosService.java`
  - Update imports: `io.mars.lite.order.*` → `io.mars.lite.domain.*`
- `ChaosOrderExecutor.java` → `api/chaos/ChaosOrderExecutor.java`
  - Update imports: `io.mars.lite.order.usecase.*` → `io.mars.lite.domain.usecase.*`
- `PhantomEventChaosAspect.java` → `api/chaos/PhantomEventChaosAspect.java`
  - **CRITICAL**: Pointcut expression references `io.mars.lite.api.chaos.ChaosOrderExecutor` — this package stays the same, so NO CHANGE NEEDED to the pointcut
- `PhantomEventSimulationException.java` → `api/chaos/PhantomEventSimulationException.java` — package stays same
- `PhantomEventReport.java` → `api/chaos/PhantomEventReport.java` — package stays same

### Phase 5: Application & Resources

**Application.java** — stays at `io.mars.lite.Application` (root package, unchanged content)

**application.yaml** — MOVE from `app/src/main/resources/` to `src/main/resources/` (unchanged content)

**V1__create_orders_table.sql** — MOVE from `data-provider/src/main/resources/db/migration/` to `src/main/resources/db/migration/` (unchanged content)

### Phase 6: ArchitectureTest (NEW)

Create a comprehensive ArchUnit test covering Onion Architecture rules:

```java
package io.mars.lite;

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
                .importPackages("io.mars.lite");
    }

    // === LAYER BOUNDARY RULES ===
    //
    // NOTE: Domain is intentionally ALLOWED to use org.springframework.stereotype.Service
    // and org.springframework.transaction.annotation.Transactional.
    // Only infrastructure-specific Spring imports (JPA, Kafka, Web, Servlet) are blocked.

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
    @DisplayName("API should not access infrastructure persistence directly")
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
    @DisplayName("API controllers should not access infrastructure directly")
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

**CRITICAL RULE: @Test count MUST increase. Current count: exactly 38 tests. Expected post-migration count: >= 50 tests.**

**Test count mapping:**
| Source (Before) | Tests | Target (After) | Tests | Notes |
|---|---|---|---|---|
| OrderTest | 9 | domain/OrderTest | 9 | Package move only |
| OrderItemTest | 6 | domain/OrderItemTest | 6 | Package move only |
| CreateOrderUseCaseTest | 2 | domain/usecase/CreateOrderUseCaseTest | 2 | Change to test @Service class directly |
| CancelOrderUseCaseTest | 2 | domain/usecase/CancelOrderUseCaseTest | 2 | Change to test @Service class directly |
| OrderRepositoryImplIntegrationTest | 4 | infrastructure/persistence/OrderRepositoryImplIntegrationTest | 4 | Extend AbstractIntegrationTest |
| OrderServiceIntegrationTest | 4 | SPLIT into 2 use case integration tests | 4 | shouldFindOrderById dropped (covered by E2E + repo test) |
| OrderControllerE2ETest | 5 | api/OrderControllerE2ETest | 5 | Update imports only |
| PhantomEventChaosAspectTest | 3 | api/chaos/PhantomEventChaosAspectTest | 3 | Package stays same |
| ChaosControllerE2ETest | 3 | api/chaos/ChaosControllerE2ETest | 3 | Update imports only |
| (none) | 0 | ArchitectureTest (root) | 12 | NEW: 6 layer + 6 structural rules |
| (none) | 0 | ApplicationContextTest | 1 | NEW: Spring context smoke test |
| **TOTAL** | **38** | | **51** | **Net +13** |

**NOTE on OrderServiceIntegrationTest split**: The 4 tests are split as follows:
- `shouldCreateOrderAndPersistInDatabase` → `CreateOrderUseCaseIntegrationTest` (1 test)
- `shouldCancelExistingOrder` → `CancelOrderUseCaseIntegrationTest` (1 test)
- `shouldFindOrderById` → DROPPED (covered by OrderRepositoryImplIntegrationTest.shouldSaveOrderAndFindById + OrderControllerE2ETest.shouldReturnOrderDetails)
- `shouldThrowWhenCancellingNonExistentOrder` → `CancelOrderUseCaseIntegrationTest` (1 test)

Wait — that's only 3 tests from the split, not 4. Let me add one more: a test that verifies the create-then-cancel flow end-to-end at the use case level.

Actually, re-counting more carefully: after dropping `shouldFindOrderById` (1 test), we have 3 from the split. 38 - 1 + 12 + 1 = 50 tests. That's still a net increase of +12. The revised table:

| Source (Before) | Tests | Target (After) | Tests | Notes |
|---|---|---|---|---|
| OrderTest | 9 | domain/OrderTest | 9 | Package move only |
| OrderItemTest | 6 | domain/OrderItemTest | 6 | Package move only |
| CreateOrderUseCaseTest | 2 | domain/usecase/CreateOrderUseCaseTest | 2 | Change to test @Service class directly |
| CancelOrderUseCaseTest | 2 | domain/usecase/CancelOrderUseCaseTest | 2 | Change to test @Service class directly |
| OrderRepositoryImplIntegrationTest | 4 | infrastructure/persistence/OrderRepositoryImplIntegrationTest | 4 | Extend AbstractIntegrationTest |
| OrderServiceIntegrationTest (create) | 1 | domain/usecase/CreateOrderUseCaseIntegrationTest | 1 | Inject use case directly |
| OrderServiceIntegrationTest (cancel) | 1 | domain/usecase/CancelOrderUseCaseIntegrationTest | 1 | Inject use case directly |
| OrderServiceIntegrationTest (cancel-404) | 1 | domain/usecase/CancelOrderUseCaseIntegrationTest | 1 | Inject use case directly |
| OrderServiceIntegrationTest (findById) | 1 | DROPPED | 0 | Covered by repo + E2E tests |
| OrderControllerE2ETest | 5 | api/OrderControllerE2ETest | 5 | Update imports only |
| PhantomEventChaosAspectTest | 3 | api/chaos/PhantomEventChaosAspectTest | 3 | Package stays same |
| ChaosControllerE2ETest | 3 | api/chaos/ChaosControllerE2ETest | 3 | Update imports only |
| (none) | 0 | ArchitectureTest (root) | 12 | NEW |
| (none) | 0 | ApplicationContextTest | 1 | NEW |
| **TOTAL** | **38** | | **50** | **Net +12** |

**AbstractIntegrationTest.java** — MOVE to `src/test/java/io/mars/lite/AbstractIntegrationTest.java`:
- Update import: `io.mars.lite.order.OrderEventPublisher` → `io.mars.lite.domain.OrderEventPublisher`
- Content otherwise unchanged

```java
package io.mars.lite;

import io.mars.lite.domain.OrderEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders_db")
            .withUsername("mars")
            .withPassword("mars");
        postgres.start();
    }

    @MockitoBean
    private OrderEventPublisher orderEventPublisher;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }
}
```

**ApplicationContextTest.java** — NEW (Spring context smoke test):
```java
package io.mars.lite;

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

**Domain unit tests** (from business/src/test/) → `src/test/java/io/mars/lite/domain/`

**OrderTest.java** (9 tests):
- Update package to `io.mars.lite.domain`
- All imports from `io.mars.lite.order.*` → `io.mars.lite.domain.*`

**OrderItemTest.java** (6 tests):
- Update package to `io.mars.lite.domain`
- All imports from `io.mars.lite.order.*` → `io.mars.lite.domain.*`

**CreateOrderUseCaseTest.java** (2 tests):
- Move to `src/test/java/io/mars/lite/domain/usecase/CreateOrderUseCaseTest.java`
- Update package to `io.mars.lite.domain.usecase`
- **IMPORTANT**: Change from using `CreateOrderUseCase.create()` factory to direct constructor
- The @Service class constructor takes `OrderRepository` and `OrderEventPublisher`

```java
package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderCreatedEvent;
import io.mars.lite.domain.OrderEventPublisher;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CreateOrderUseCaseTest {

    private OrderRepository orderRepository;
    private OrderEventPublisher eventPublisher;
    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        eventPublisher = mock(OrderEventPublisher.class);
        useCase = new CreateOrderUseCase(orderRepository, eventPublisher);
    }

    @Test
    void shouldSaveOrderAndPublishEvent() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("99.99")));
        var input = new CreateOrderUseCase.Input(items, UUID.randomUUID());

        var orderId = useCase.execute(input);

        assertThat(orderId).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publish(any(OrderCreatedEvent.class));
    }

    @Test
    void shouldThrowWhenItemsEmpty() {
        var input = new CreateOrderUseCase.Input(Set.of(), UUID.randomUUID());

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class);

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
```

**CancelOrderUseCaseTest.java** (2 tests):
- Move to `src/test/java/io/mars/lite/domain/usecase/CancelOrderUseCaseTest.java`
- Update package to `io.mars.lite.domain.usecase`
- Change from using `CancelOrderUseCase.create()` factory to direct constructor

```java
package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import io.mars.lite.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancelOrderUseCaseTest {

    private OrderRepository orderRepository;
    private CancelOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        useCase = new CancelOrderUseCase(orderRepository);
    }

    @Test
    void shouldCancelExistingOrder() {
        var result = Order.create(UUID.randomUUID(),
            Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
        var order = result.domain();
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));

        useCase.execute(order.id());

        verify(orderRepository).update(argThat(o -> o.status() == OrderStatus.CANCELLED));
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        var orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId))
            .isInstanceOf(BusinessException.class);

        verify(orderRepository, never()).update(any());
    }
}
```

**CreateOrderUseCaseIntegrationTest.java** (1 test — from OrderServiceIntegrationTest):
```java
package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderStatus;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void cleanUp() {
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should create order and persist in database")
    void shouldCreateOrderAndPersistInDatabase() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")));
        var customerId = UUID.randomUUID();

        var orderId = createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, customerId));

        assertThat(orderId).isNotNull();
        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    }
}
```

**CancelOrderUseCaseIntegrationTest.java** (2 tests — from OrderServiceIntegrationTest):
```java
package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderStatus;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelOrderUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void cleanUp() {
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should cancel existing order")
    void shouldCancelExistingOrder() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var orderId = createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, UUID.randomUUID()));

        cancelOrderUseCase.execute(orderId);

        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should throw when cancelling non-existent order")
    void shouldThrowWhenCancellingNonExistentOrder() {
        assertThatThrownBy(() -> cancelOrderUseCase.execute(UUID.randomUUID()))
            .isInstanceOf(BusinessException.class);
    }
}
```

**OrderRepositoryImplIntegrationTest.java** (4 tests) → `src/test/java/io/mars/lite/infrastructure/persistence/OrderRepositoryImplIntegrationTest.java`:
- Update package to `io.mars.lite.infrastructure.persistence`
- **IMPORTANT**: Change to extend `AbstractIntegrationTest` instead of having its own TestContainers setup
- Update imports: `io.mars.lite.order.*` → `io.mars.lite.domain.*`
- Remove `@Testcontainers`, `@Container`, `@DynamicPropertySource` (provided by AbstractIntegrationTest)

```java
package io.mars.lite.infrastructure.persistence;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderRepositoryImpl orderRepository;

    @BeforeEach
    void setUp() {
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should save order with items and find by id")
    void shouldSaveOrderAndFindById() {
        var items = Set.of(
            new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")),
            new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))
        );
        var result = Order.create(UUID.randomUUID(), items);
        var order = result.domain();

        orderRepository.save(order);
        var found = orderRepository.findById(order.id());

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(order.id());
        assertThat(found.get().customerId()).isEqualTo(order.customerId());
        assertThat(found.get().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(found.get().total()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(found.get().items()).hasSize(2);
    }

    @Test
    @DisplayName("should return empty when order not found")
    void shouldReturnEmptyWhenOrderNotFound() {
        var found = orderRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should update order status")
    void shouldUpdateOrderStatus() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var result = Order.create(UUID.randomUUID(), items);
        var order = result.domain();
        orderRepository.save(order);

        var cancelled = order.cancel();
        orderRepository.update(cancelled);

        var found = orderRepository.findById(order.id());
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should persist audit timestamps")
    void shouldPersistAuditTimestamps() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00")));
        var result = Order.create(UUID.randomUUID(), items);
        orderRepository.save(result.domain());

        var entity = orderJpaRepository.findById(result.domain().id()).orElseThrow();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }
}
```

**OrderControllerE2ETest.java** (5 tests) → `src/test/java/io/mars/lite/api/OrderControllerE2ETest.java`:
- Update package to `io.mars.lite.api`
- Update import: `io.mars.lite.order.OrderJpaRepository` → `io.mars.lite.infrastructure.persistence.OrderJpaRepository`

**PhantomEventChaosAspectTest.java** (3 tests) → `src/test/java/io/mars/lite/api/chaos/PhantomEventChaosAspectTest.java`:
- Package stays `io.mars.lite.api.chaos` — NO CHANGES NEEDED

**ChaosControllerE2ETest.java** (3 tests) → `src/test/java/io/mars/lite/api/chaos/ChaosControllerE2ETest.java`:
- Package stays `io.mars.lite.api.chaos`
- Update import: `io.mars.lite.order.OrderJpaRepository` → `io.mars.lite.infrastructure.persistence.OrderJpaRepository`

### Phase 8: Delete Old Module Directories

After all files are moved and verified:
1. Delete `business/` directory entirely
2. Delete `data-provider/` directory entirely
3. Delete `app/` directory entirely
4. Old parent `pom.xml` already replaced

### Phase 9: Update CLAUDE.md and README.md

**CLAUDE.md** — Major sections to update:
- Architecture section: Change from 3-module to single-module with package-based layers
- Build commands: Remove `-pl business`, `-pl data-provider`, `-pl app` flags
- Package structure: `io.mars.lite.domain`, `io.mars.lite.infrastructure`, `io.mars.lite.api`
- Use case pattern: @Service classes, no sealed interfaces, no UseCaseConfiguration
- Remove all references to OrderService.java wrapper
- Update test commands and strategy section
- Update "Adding a New Domain Aggregate" workflow

**README.md** — Update:
- Architecture diagram to show packages instead of modules
- Build commands (no `-pl` flags)
- Quick start commands
- Project structure section

---

## Task List

```yaml
Task 1: Create single pom.xml
- WRITE: pom.xml (merged, packaging=jar)
- VALIDATE: mvn validate (pom structure correct)

Task 2: Create domain package structure
- CREATE: src/main/java/io/mars/lite/domain/
- MOVE+MODIFY: All business domain files (update packages)
- TRANSFORM: CreateOrderUseCase (sealed interface+impl → @Service class)
- TRANSFORM: CancelOrderUseCase (sealed interface+impl → @Service class)
- VALIDATE: No compilation errors in domain package

Task 3: Create infrastructure package
- CREATE: src/main/java/io/mars/lite/infrastructure/persistence/
- CREATE: src/main/java/io/mars/lite/infrastructure/messaging/
- CREATE: src/main/java/io/mars/lite/infrastructure/configuration/
- MOVE+MODIFY: All infrastructure files (update packages + imports)
- MODIFY: OrderCancelledConsumer (replace OrderService with CancelOrderUseCase)
- RENAME: DataSourceConfiguration → AuditingConfiguration
- VALIDATE: No compilation errors

Task 4: Create api package
- MOVE+MODIFY: OrderController to inject CreateOrderUseCase + OrderRepository directly
- MOVE+MODIFY: CreateOrderRequest, OrderResponse, GlobalExceptionHandler (update packages)
- MOVE+MODIFY: All chaos/ files (update imports)
- VALIDATE: No compilation errors

Task 5: Application and resources
- MOVE: Application.java (stays at root package, unchanged)
- MOVE: application.yaml to src/main/resources/
- MOVE: V1__create_orders_table.sql to src/main/resources/db/migration/
- VALIDATE: mvn clean compile passes

Task 6: Write ArchitectureTest
- WRITE: src/test/java/io/mars/lite/ArchitectureTest.java (12 rules)
- VALIDATE: mvn test -Dtest=ArchitectureTest passes

Task 7: Migrate tests
- MOVE+MODIFY: AbstractIntegrationTest (update imports)
- MOVE+MODIFY: OrderTest, OrderItemTest to domain/ test package
- MOVE+MODIFY: CreateOrderUseCaseTest, CancelOrderUseCaseTest (use constructor, not factory)
- SPLIT+MOVE: OrderServiceIntegrationTest → CreateOrderUseCaseIntegrationTest + CancelOrderUseCaseIntegrationTest
- MOVE+MODIFY: OrderRepositoryImplIntegrationTest (extend AbstractIntegrationTest)
- MOVE+MODIFY: OrderControllerE2ETest to api/ test package
- MOVE: PhantomEventChaosAspectTest, ChaosControllerE2ETest (update imports)
- WRITE: ApplicationContextTest (Spring context smoke test)
- VALIDATE: mvn test passes (all tests)

Task 8: Delete old module directories
- DELETE: business/, data-provider/, app/
- VALIDATE: mvn clean verify passes (full build)

Task 9: Update CLAUDE.md and README.md
- REWRITE: CLAUDE.md Architecture section, build commands, conventions
- REWRITE: README.md structure, commands, quick start
- VALIDATE: Content accuracy

Task 10: Final verification
- RUN: mvn clean verify
- VERIFY: All tests pass
- VERIFY: @Test count >= 50
```

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile
# Expected: BUILD SUCCESS
```

### Level 2: Architecture Tests
```bash
mvn test -Dtest=ArchitectureTest
# Expected: All 12 architecture rules pass
# Key: domain package has NO dependency on infrastructure or api
```

### Level 3: Unit Tests (domain)
```bash
mvn test -Dtest="io.mars.lite.domain.**"
# Expected: OrderTest (9), OrderItemTest (6), CreateOrderUseCaseTest (2), CancelOrderUseCaseTest (2) all pass
# These run WITHOUT Spring context, WITHOUT database
```

### Level 4: Integration Tests
```bash
# Ensure Docker is running for TestContainers
docker info
mvn test
# Expected: ALL tests pass (unit + integration + E2E + architecture + chaos)
```

### Level 5: Full Build
```bash
mvn clean verify
# Expected: BUILD SUCCESS with >= 50 tests
```

---

## Final Validation Checklist

- [ ] Single pom.xml (no `<modules>`, packaging=jar)
- [ ] All domain classes in `io.mars.lite.domain.*`
- [ ] All infrastructure classes in `io.mars.lite.infrastructure.*`
- [ ] All API classes in `io.mars.lite.api.*`
- [ ] No sealed interface on use cases
- [ ] No UseCaseConfiguration.java
- [ ] No OrderService.java wrapper
- [ ] DataSourceConfiguration renamed to AuditingConfiguration
- [ ] TestApplication.java (data-provider) deleted
- [ ] Use cases are @Service classes with @Transactional
- [ ] ArchitectureTest enforces: domain cannot depend on infrastructure/api
- [ ] Domain has NO JPA, NO Kafka, NO Spring Web imports
- [ ] All existing test scenarios preserved and passing
- [ ] application.yaml at correct location (unchanged content)
- [ ] Flyway migration at correct location
- [ ] Chaos testing (POST /chaos/phantom-event) works identically
- [ ] AOP pointcut matches new ChaosOrderExecutor location
- [ ] OrderCancelledConsumer calls CancelOrderUseCase directly (not OrderService)
- [ ] OrderController injects CreateOrderUseCase + OrderRepository directly
- [ ] Jackson 3 imports preserved (tools.jackson.*)
- [ ] CLAUDE.md updated
- [ ] README.md updated
- [ ] `mvn clean verify` passes
- [ ] @Test count >= 50

---

## Out of Scope

- **docker-compose.yml**: Infrastructure config is separate from this structural migration
- **Kafka topics/event format**: JSON events are unchanged
- **Flyway migrations**: No schema changes — only file relocation
- **New features**: This is a pure structural migration with zero behavioral changes
- **Transactional Outbox**: Still intentionally omitted (this is the Lite version)

---

## Anti-Patterns to Avoid

- **DO NOT** put `@Entity` or JPA annotations in domain package
- **DO NOT** import `jakarta.persistence.*` in domain package
- **DO NOT** import `org.springframework.kafka.*` in domain package
- **DO NOT** create new interfaces for use cases (use concrete @Service classes)
- **DO NOT** create UseCaseConfiguration.java (component scanning handles it)
- **DO NOT** create service wrapper classes around use cases
- **DO NOT** change any business logic or test assertions (this is a structural migration only)
- **DO NOT** modify application.yaml content (only move it)
- **DO NOT** skip the ArchitectureTest — it replaces multi-module enforcement
- **DO NOT** forget to add `@Service` and `@Transactional` on the use case classes
- **DO NOT** add `@Repository` or `@Component` annotations inside the domain package — only `@Service` and `@Transactional` are permitted
- **DO NOT** reduce the @Test count — every test scenario must be preserved (target: >= 50 tests)
- **DO NOT** change the AOP pointcut without updating to match the new package
- **DO NOT** break Jackson 3 imports by replacing with Jackson 2 (com.fasterxml.jackson)
- **DO NOT** change Kafka producer timeout values (they're intentional for Dual Write education)
- **DO NOT** remove `@MockitoBean OrderEventPublisher` from AbstractIntegrationTest

---

## Confidence Score: 9/10

**Rationale:**
- [x] Every current source file has been read and mapped to target location
- [x] All pom.xml dependencies identified and merged
- [x] Use case transformation pattern is clear with concrete code examples
- [x] All gotchas documented (Jackson 3, TestContainers 2.0.3, ArchUnit, AOP pointcut)
- [x] Chaos testing preservation verified (AOP pointcut, @Transactional propagation)
- [x] Validation gates are executable at each phase
- [x] No ambiguous requirements — this is a pure structural migration
- [x] Test migration plan includes exact count mapping (38 → 50 tests)
- [x] ArchitectureTest includes all layer + structural rules
- [x] OrderCancelledConsumer dependency change documented
- [x] OrderController GET endpoint handled (inject OrderRepository directly)
- [x] Spring context smoke test ensures wiring correctness
- [x] CLAUDE.md + README.md update included

**Risk areas (-1):**
- [ ] OrderRepositoryImplIntegrationTest — extending AbstractIntegrationTest loads the full Spring context (including Kafka config). Verify this doesn't cause test failures. Current test has its own minimal @DynamicPropertySource; after migration, AbstractIntegrationTest's config (which includes `spring.kafka.bootstrap-servers=localhost:0` and `spring.kafka.listener.auto-startup=false`) should handle this correctly.

**Verified:**
- [x] AOP dependency — `aspectjweaver` is a transitive dependency via `spring-boot-starter-data-jpa` → `spring-aspects`. No explicit `spring-boot-starter-aop` needed.
