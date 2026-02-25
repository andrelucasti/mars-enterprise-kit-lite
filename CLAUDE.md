# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# Mars Enterprise Kit Lite

## Project Overview

Mars Enterprise Kit Lite is a free, open-source Order microservice with correct Onion Architecture, Kafka communication via Redpanda, and PostgreSQL persistence — **but without the Transactional Outbox pattern**. The Dual Write anti-pattern is intentional: it demonstrates the consistency problem so developers can experience it firsthand.

This project is also an **AI-First** lab: all infrastructure operations are orchestrated by Claude Code.

**What This Project IS:**
- A single Order microservice with Onion Architecture (single-module, package-based layers)
- Two use cases: Create Order (publishes to Kafka) and Cancel Order (consumes from Kafka)
- Dual Write pattern (intentional — no atomic guarantee between DB and Kafka)
- Educational template for learning Onion Architecture with a real, functional example

**What This Project is NOT:**
- Not production-ready (Dual Write has no consistency guarantee)
- No Transactional Outbox (out of scope for Lite)
- No Event Sourcing, no SAGA, no CQRS
- No authentication, no observability beyond actuator
- No Schema Registry, no Avro (JSON events only)
- No Helm charts, no CI/CD, no Kubernetes


## Quick Reference

### Build & Run Commands

```bash
# Start infrastructure (PostgreSQL + Redpanda)
docker-compose up -d

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Run tests
mvn test                              # All tests

# Run single test class
mvn test -Dtest=CreateOrderUseCaseTest

# Run single test method
mvn test -Dtest=CreateOrderUseCaseTest#shouldCreateOrderSuccessfully

# Full build verification
mvn clean verify
```

### Service Access
- **API**: http://localhost:8082
- **Health**: http://localhost:8082/actuator/health
- **PostgreSQL**: localhost:5432 (orders_db)
- **Redpanda (Kafka)**: localhost:9092
- **Redpanda Admin**: localhost:9644
- **Redpanda Schema Registry**: localhost:8081 ⚠️ conflicts with default Spring Boot port — app uses **8082**

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/orders` | Create a new order |
| `GET` | `/orders/{id}` | Get order by ID |
| `GET` | `/actuator/health` | Health check |
| `POST` | `/chaos/phantom-event` | Simulate phantom event (Dual Write failure) |

No authentication. Endpoints are open by design (educational project).

> **Chaos endpoint** requires `SPRING_PROFILES_ACTIVE=chaos`. See [Chaos Testing](#chaos-testing-phantom-event) below.

## Technology Stack

| Category | Technology |
|----------|------------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Build | Maven 3.9+ (multi-module) |
| Database | PostgreSQL 16 (alpine) |
| Messaging | Redpanda (Kafka-compatible) |
| Event Format | JSON (Jackson) |
| Containers | Docker Compose |
| Testing | JUnit 5, TestContainers, REST Assured |

## Architecture

### Onion Architecture (Single Module, Package-Based Layers)

```
src/main/java/io/mars/lite/
├── Application.java                          # Spring Boot entry point
│
├── domain/                                   # Domain Layer — NO JPA, NO Kafka, NO Web
│   ├── Order.java                            # Aggregate Root (record)
│   ├── OrderStatus.java                      # Value Object (enum)
│   ├── OrderItem.java                        # Value Object (record)
│   ├── OrderCreatedEvent.java                # Domain Event (record)
│   ├── DomainResult.java                     # Generic wrapper: record DomainResult<D, E>
│   ├── OrderRepository.java                  # Port (interface)
│   ├── OrderEventPublisher.java              # Port (interface) — decouples domain from Kafka
│   ├── BusinessException.java                # Domain Exception
│   └── usecase/
│       ├── CreateOrderUseCase.java           # @Service class (was: sealed interface + record impl)
│       └── CancelOrderUseCase.java           # @Service class (was: sealed interface + record impl)
│
├── infrastructure/                           # Infrastructure Layer — implements domain ports
│   ├── persistence/
│   │   ├── OrderEntity.java                  # JPA Entity
│   │   ├── OrderItemEntity.java              # JPA Entity (items)
│   │   ├── OrderJpaRepository.java           # Spring Data JPA
│   │   └── OrderRepositoryImpl.java          # Adapter (implements OrderRepository)
│   ├── messaging/
│   │   ├── OrderCreatedPublisher.java        # Kafka Producer — implements OrderEventPublisher
│   │   └── OrderCancelledConsumer.java       # Kafka Consumer
│   └── configuration/
│       ├── AuditingConfiguration.java        # @EnableJpaAuditing (was: DataSourceConfiguration)
│       └── KafkaConfiguration.java           # Kafka producer config
│
└── api/                                      # API Layer — HTTP entry points
    ├── OrderController.java                  # REST controller (POST /orders, GET /orders/{id})
    ├── CreateOrderRequest.java               # Request DTO (record)
    ├── OrderResponse.java                    # Response DTO (record)
    ├── GlobalExceptionHandler.java           # @RestControllerAdvice
    └── chaos/                                # Chaos Testing (@Profile("chaos") only)
        ├── ChaosController.java              # POST /chaos/phantom-event
        ├── ChaosService.java                 # @Transactional orchestrator
        ├── ChaosOrderExecutor.java           # AOP target (wraps CreateOrderUseCase)
        ├── PhantomEventChaosAspect.java      # @Aspect — forces rollback after publish
        ├── PhantomEventSimulationException.java  # RuntimeException with orderId
        └── PhantomEventReport.java           # Response DTO (record)
```

### Dependency Rule (The Onion Law)

Dependencies always point **inward** (enforced by ArchUnit `ArchitectureTest`):
- **domain/** → depends on NOTHING (no JPA, no Kafka, no Web — only @Service and @Transactional allowed)
- **infrastructure/** → depends on domain (implements its ports)
- **api/** → depends on domain + infrastructure configuration (wires everything via DI)

### The Intentional Anti-Pattern: Dual Write

`CreateOrderUseCase` calls two ports in sequence — `OrderRepository.save()` then `OrderEventPublisher.publish()` — with **no atomic guarantee** between them. If the Kafka publish fails after the DB commit, the event is silently lost. This is by design.

```
POST /orders → CreateOrderUseCase (@Transactional @Service)
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
  OrderRepository        OrderEventPublisher
  (port → JPA adapter)   (port → Kafka adapter)
  INSERT orders          send("order.created")
                    ⚠️  No atomicity guarantee
```

`CreateOrderUseCase` is a **@Service class** with constructor injection:
```java
@Service
public class CreateOrderUseCase {
    public CreateOrderUseCase(OrderRepository orderRepository,
                               OrderEventPublisher orderEventPublisher) { ... }

    @Transactional
    public UUID execute(Input input) { ... }

    public record Input(Set<OrderItem> items, UUID customerId) {}
}
```

The Transactional Outbox pattern solves this, but is intentionally omitted from Lite.

### Chaos Testing (Phantom Event)

The project includes a chaos testing endpoint that **proves** the Dual Write problem by deliberately causing a Phantom Event — an event published to Kafka for an order that doesn't exist in PostgreSQL.

**How it works:**
1. `ChaosController` receives `POST /chaos/phantom-event` (same body as `POST /orders`)
2. `ChaosService.attemptPhantomOrder()` calls `CreateOrderUseCase.execute()` inside `@Transactional`
3. `PhantomEventChaosAspect` (AOP `@Around`) intercepts `ChaosOrderExecutor.execute()` and throws **after** the use case completes (DB INSERT + Kafka publish both happened)
4. The `@Transactional` catches the exception and **rolls back** the DB INSERT
5. Result: Kafka has the event, PostgreSQL does NOT have the order

**Requires Spring profile `chaos`:**
```bash
cd app && SPRING_PROFILES_ACTIVE=chaos mvn spring-boot:run
curl -X POST http://localhost:8082/chaos/phantom-event \
  -H "Content-Type: application/json" \
  -d '{"customerId":"550e8400-e29b-41d4-a716-446655440000","items":[{"productId":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","quantity":2,"unitPrice":149.95}]}'
```

**All chaos beans use `@Profile("chaos")`** — they don't exist in the default profile and don't affect normal operation.

### Kafka Topics

| Topic | Role | Direction |
|-------|------|-----------|
| `order.created` | Publisher | order-service → external consumers |
| `order.cancelled` | Consumer | external systems → order-service |

Events use plain JSON (Jackson). No Avro, no Schema Registry.

## Database

### Migrations
- Location: `data-provider/src/main/resources/db/migration/`
- Naming: `V{VERSION}__{description}.sql` (e.g., `V1__create_orders_table.sql`)
- Auto-runs on startup via Flyway

### Tables

```sql
orders (
    id          UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total       NUMERIC(10,2) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
)

order_items (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders(id),
    product_id  UUID NOT NULL,
    quantity    INT NOT NULL,
    unit_price  NUMERIC(10,2) NOT NULL
)
```

No `event_store`, no `outbox`, no `orders_summary` — those are out of scope for Lite.

## Project Structure

```
mars-enterprise-kit-lite/
├── .mars/
│   ├── docs/
│   │   └── mars-enterprise-kit-context-lite.md   # Full project context
│   └── PRPs/
│       └── templates/
│           └── prp_base.md                       # PRP template
├── .claude/
│   └── commands/
│       ├── generate-prp.md                       # PRP generation command
│       └── execute-prp.md                        # PRP execution command
├── src/
│   ├── main/
│   │   ├── java/io/mars/lite/
│   │   │   ├── Application.java                  # Spring Boot entry point
│   │   │   ├── domain/                           # Domain layer (no JPA/Kafka/Web)
│   │   │   │   ├── Order.java, OrderItem.java, OrderStatus.java
│   │   │   │   ├── OrderCreatedEvent.java, DomainResult.java
│   │   │   │   ├── OrderRepository.java, OrderEventPublisher.java (ports)
│   │   │   │   ├── BusinessException.java
│   │   │   │   └── usecase/
│   │   │   │       ├── CreateOrderUseCase.java   # @Service
│   │   │   │       └── CancelOrderUseCase.java   # @Service
│   │   │   ├── infrastructure/                   # Infrastructure layer
│   │   │   │   ├── persistence/                  # JPA adapters
│   │   │   │   ├── messaging/                    # Kafka producer/consumer
│   │   │   │   └── configuration/                # AuditingConfiguration, KafkaConfiguration
│   │   │   └── api/                              # HTTP layer
│   │   │       ├── OrderController.java
│   │   │       ├── CreateOrderRequest.java, OrderResponse.java
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       └── chaos/                        # @Profile("chaos") only
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/V1__create_orders_table.sql
│   └── test/
│       └── java/io/mars/lite/
│           ├── ArchitectureTest.java             # ArchUnit rules (12 tests)
│           ├── AbstractIntegrationTest.java      # Shared TestContainers setup
│           ├── ApplicationContextTest.java       # Spring context smoke test
│           ├── domain/                           # Unit tests (no Spring)
│           ├── infrastructure/                   # Repository integration tests
│           └── api/                              # E2E tests (REST Assured)
├── docker-compose.yml                            # PostgreSQL + Redpanda
├── pom.xml                                       # Single POM (packaging=jar)
├── CLAUDE.md                                     # This file
└── README.md
```

## Code Conventions

### Package Structure
- Base package: `io.mars.lite`
- Domain: `io.mars.lite.domain`
- Use cases: `io.mars.lite.domain.usecase`
- Infrastructure persistence: `io.mars.lite.infrastructure.persistence`
- Infrastructure messaging: `io.mars.lite.infrastructure.messaging`
- Infrastructure configuration: `io.mars.lite.infrastructure.configuration`
- API: `io.mars.lite.api`

### Domain Objects
- Use Java **records** for immutable domain objects and value objects
- Null safety via `Objects.requireNonNull()` in constructors
- Business logic lives in domain objects, not services

### Naming
- Interfaces as ports: `OrderRepository`, `OrderEventPublisher` (in domain/)
- Implementations as adapters: `OrderRepositoryImpl`, `OrderCreatedPublisher` (in infrastructure/)
- Use cases: `CreateOrderUseCase` (concrete `@Service` class — NO separate interface)
- No service wrapper: `OrderController` injects `CreateOrderUseCase` directly
- Test naming: `should{ExpectedBehavior}When{Condition}`

### Code Style

**Immutability First**
- Use Java **records** for all immutable data structures
- Avoid setters and mutable state
- Validate in compact constructors

**Request/Response Objects**
- **Always create separate files** (not inner classes in controllers)
- Use Java records
- Location: `app/src/main/java/io/mars/lite/api/order/`
- Naming: `{Operation}{Request|Response}.java`

```java
// ✅ CORRECT: Separate file, Java record
// File: CreateOrderRequest.java
public record CreateOrderRequest(
    UUID customerId,
    Set<ItemRequest> items
) {
    public CreateOrderRequest {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(items, "items cannot be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items cannot be empty");
        }
    }

    public record ItemRequest(UUID productId, int quantity, BigDecimal unitPrice) {
        public ItemRequest {
            Objects.requireNonNull(productId, "productId cannot be null");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("unitPrice must be positive");
        }
    }
}
```

## Testing

### Test-Driven Development (TDD) — MANDATORY

All implementations MUST follow TDD. This is not optional.

**The TDD Cycle:**
```
1. RED:      Write a failing test FIRST
2. GREEN:    Write MINIMUM code to make the test pass
3. REFACTOR: Clean up code while keeping tests green
4. REPEAT
```

### Testing Strategy by Package

| Package | Test Location | Test Type | Speed | Framework | What to Test |
|---------|--------------|-----------|-------|-----------|--------------|
| **domain/** | `src/test/java/.../domain/` | Unit tests | < 1ms | JUnit 5 + Mockito | Domain logic, use cases, value objects |
| **infrastructure/persistence/** | `src/test/java/.../infrastructure/` | Integration tests | ~100ms | Spring Boot Test + TestContainers | JPA mappings, migrations, repository adapters |
| **domain/usecase/** (integration) | `src/test/java/.../domain/usecase/` | Use case integration tests | ~200ms | Spring Boot Test + TestContainers | @Service use cases with ACID transactions |
| **api/** | `src/test/java/.../api/` | E2E tests | ~500ms | REST Assured (BDD-style) | Full HTTP request/response cycle |

**Implementation order (inside out):**
1. Domain logic tests first (domain/) — Unit tests with mocks
2. Repository tests (infrastructure/persistence/) — Integration tests with TestContainers
3. Use case integration tests (domain/usecase/) — Verify ACID transactions
4. Controller E2E tests (api/) — REST Assured `given().when().then()`

**Rules:**
- Every `@Service` use case MUST have a corresponding integration test
- Every `@RestController` MUST have E2E tests with REST Assured
- Never write production code before a failing test

### TDD Example

```java
// Step 1: RED — Write the failing test FIRST
@Test
void shouldCalculateOrderTotalWhenMultipleItems() {
    Order order = Order.create(new CustomerId("cust-1"));
    order.addItem(new ProductId("prod-1"), 2, new Money(new BigDecimal("10.00")));

    Money total = order.calculateTotal();

    assertThat(total).isEqualTo(new Money(new BigDecimal("20.00")));
}
// Run test → FAILS (calculateTotal() doesn't exist yet)

// Step 2: GREEN — Write MINIMUM code to pass
public Money calculateTotal() {
    return items.stream()
        .map(item -> item.price().multiply(item.quantity()))
        .reduce(Money.ZERO, Money::add);
}
// Run test → PASSES

// Step 3: REFACTOR — Clean up while tests stay green
```

## API Examples

### Create Order
```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "quantity": 2, "unitPrice": 149.95}
    ]
  }'
```

Response (`201 Created`):
```json
{ "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" }
```

### Get Order
```bash
curl http://localhost:8082/orders/{orderId}
```

Response (`200 OK`):
```json
{
  "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "total": 299.9,
  "items": [
    { "productId": "...", "quantity": 2, "unitPrice": 149.95 }
  ],
  "createdAt": "2026-...",
  "updatedAt": "2026-..."
}
```

> Note: the response field is `"id"` (not `"orderId"`). `total` serializes without trailing zeros (e.g. `299.9` not `299.90`).

## Common Development Workflows

### Adding a New Domain Aggregate

1. **Write failing tests** in `src/test/java/io/mars/lite/domain/`
2. **Create domain class** in `src/main/java/io/mars/lite/domain/` — records for value objects, enforce invariants
3. **Create repository interface** (port) in `domain/`
4. **Write failing integration tests** in `src/test/java/io/mars/lite/infrastructure/`
5. **Create JPA entity** in `infrastructure/persistence/` — maps domain to database
6. **Implement repository** (adapter) in `infrastructure/persistence/`
7. **Create Flyway migration** in `src/main/resources/db/migration/`
8. **Write failing E2E tests** in `src/test/java/io/mars/lite/api/`
9. **Create controller + DTOs** in `api/`
10. **No manual wiring needed** — `@Service` + component scanning auto-discovers all beans

### Adding a New Kafka Event

1. **Define event record** in `business/` (immutable, JSON-serializable)
2. **Create Kafka publisher** in `app/.../api/event/` — uses Dual Write (publish after DB save)
3. **Or create Kafka consumer** in `app/.../api/event/` — calls use case on message receipt
4. **No Outbox, no Avro** — this is the Lite version

### Modifying Database Schema

1. Create new migration: `V{N}__{description}.sql`
2. Update JPA entity in `data-provider/`
3. Update domain model in `business/` if needed
4. Migration runs automatically on startup

## Code Generation Guidelines

**DO:**
- Use Java **records** for immutable objects
- Enforce null safety with `Objects.requireNonNull()`
- Place business logic in domain objects, not services
- Follow TDD strictly: test FIRST, then implement, then refactor
- Follow Onion Architecture: domain → infrastructure → api (enforced by ArchUnit)
- Use ports (interfaces) in domain/, adapters (implementations) in infrastructure/
- Create separate files for Request/Response DTOs
- Create Flyway migrations for all schema changes
- Create integration tests for every `@Service` use case
- Create REST Assured E2E tests for every `@RestController`
- Use Dual Write for Kafka events (this is the Lite version)
- Use concrete `@Service` classes for use cases (no separate interfaces)

**DON'T:**
- Add JPA, Kafka, or Spring Web annotations in domain/ package
- Let domain/ depend on infrastructure/ or api/
- Use setters in domain objects
- Write production code before a failing test
- Implement Outbox pattern (out of scope for Lite)
- Implement Event Sourcing or SAGA (out of scope for Lite)
- Use Avro or Schema Registry (Lite uses JSON)
- Define DTOs as inner classes in controllers
- Create `@Service` use cases without integration tests
- Create `@RestController` without E2E tests
- Create a service wrapper just to call a use case (OrderController injects use cases directly)
- Create a UseCaseConfiguration.java (component scanning handles all wiring)

## Context Resources

| Resource | Purpose |
|----------|---------|
| `.mars/docs/mars-enterprise-kit-context-lite.md` | Complete project context |
| `CLAUDE.md` | This file — conventions and rules |
| `.mars/PRPs/templates/prp_base.md` | PRP template for feature implementation |

## Troubleshooting

### Docker Compose Issues
```bash
docker-compose down -v && docker-compose up -d --force-recreate
docker-compose logs -f postgres
docker-compose logs -f redpanda
docker-compose ps
```

### Build Failures
```bash
mvn clean                    # Clean artifacts
mvn clean install -U         # Force update dependencies
java -version                # Should be 25
mvn -version                 # Should be 3.9+
```

### Port Already in Use
```bash
lsof -i :8082                # Find app process (app runs on 8082)
kill -9 <PID>                # Kill it
# Note: port 8081 is used by Redpanda's schema registry — do NOT kill it
```

### Database Connection Issues
```bash
docker-compose ps postgres
docker-compose logs postgres
docker-compose exec postgres psql -U mars -d orders_db
```

### Test Failures
```bash
mvn test -X                                   # Verbose output
mvn test -Dtest=FailingTest#failingMethod     # Run specific test
docker ps                                     # Ensure Docker is running (TestContainers)
```

### Flyway Migration Issues
```bash
mvn flyway:info                               # Check status
mvn flyway:repair                             # Fix checksums
```
