# PRP-05: Chaos Endpoint — Phantom Event (Dual Write Failure Demonstration)

## Purpose

Implement a chaos testing endpoint that **proves** the Phantom Event problem in the Dual Write anti-pattern. When invoked, the endpoint creates an order (saving to DB + publishing to Kafka), then forces a DB rollback via an AOP interceptor. The result: the Kafka event describes an order that **does not exist** in PostgreSQL.

### Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests the AI can run and fix
3. **TDD Mandatory**: Every feature follows Red-Green-Refactor
4. **Onion Architecture**: Dependencies always point inward (app -> data-provider -> business)
5. **Global Rules**: Follow all rules in CLAUDE.md

---

## Goal

A `POST /chaos/phantom-event` endpoint (active only with Spring profile `chaos`) that:
1. Calls the existing `CreateOrderUseCase.execute()` (which saves to DB + publishes to Kafka)
2. An AOP `@Around` advice intercepts and throws AFTER execute() returns but BEFORE `@Transactional` commits
3. The DB transaction rolls back, but the Kafka event was already sent
4. Returns a JSON report proving the inconsistency: `existsInDb: false`, `eventSentToKafka: true`

## Why

- **Educational**: Demonstrates the Phantom Event scenario — the most dangerous Dual Write failure
- **Tangible proof**: Developers can see with their own eyes that Kafka has an event for a non-existent order
- **Motivates Outbox**: After experiencing this, the developer understands WHY the Transactional Outbox pattern exists
- **AI-First**: The chaos skill can orchestrate this endpoint automatically

## What

### User-Visible Behavior

```bash
# Start app with chaos profile
cd app && SPRING_PROFILES_ACTIVE=chaos mvn spring-boot:run

# Trigger phantom event
curl -s -X POST http://localhost:8082/chaos/phantom-event \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "quantity": 2, "unitPrice": 149.95}
    ]
  }'
```

**Response (200 OK):**
```json
{
  "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "existsInDb": false,
  "eventSentToKafka": true,
  "dbRolledBack": true,
  "explanation": "PHANTOM EVENT: The order.created event was published to Kafka, but the order does NOT exist in PostgreSQL. Any consumer processing this event will reference a non-existent order."
}
```

### Success Criteria

- [ ] `POST /chaos/phantom-event` returns 200 with `PhantomEventReport`
- [ ] Report shows `existsInDb: false` (DB was rolled back)
- [ ] Report shows `eventSentToKafka: true` (Kafka received the event before rollback)
- [ ] Report shows `dbRolledBack: true`
- [ ] Endpoint only active with `@Profile("chaos")` — invisible in default profile
- [ ] AOP `@Around` advice is the mechanism that forces the exception
- [ ] Normal `POST /orders` endpoint is NOT affected (chaos is isolated)
- [ ] All existing tests continue to pass (`mvn clean verify`)
- [ ] New E2E test validates the chaos endpoint behavior
- [ ] Reuses existing `CreateOrderRequest` DTO (no duplication)

---

## All Needed Context

### Documentation & References

```yaml
- file: CLAUDE.md
  why: Project conventions, code style, TDD rules, Onion Architecture, port 8082

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Full project context, Dual Write anti-pattern explanation, domain model

- url: https://docs.spring.io/spring-framework/reference/core/aop.html
  why: Spring AOP @Aspect, @Around advice, pointcut expressions

- url: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
  why: AOP proxy mechanisms — CGLIB vs JDK dynamic proxy (critical for records)

- url: https://docs.spring.io/spring-boot/reference/features/aop.html
  why: Spring Boot AOP auto-configuration, spring-boot-starter-aop dependency
```

### Current Codebase Structure (Relevant Files)

```
app/src/main/java/io/mars/lite/
├── Application.java
├── configuration/
│   ├── UseCaseConfiguration.java       # Bean wiring for use cases
│   └── KafkaConfiguration.java         # Kafka producer config
└── api/
    ├── order/
    │   ├── OrderController.java        # Existing REST controller
    │   ├── OrderService.java           # @Service wrapping use cases
    │   ├── CreateOrderRequest.java     # ← REUSE THIS for chaos endpoint
    │   └── OrderResponse.java
    ├── event/
    │   ├── OrderCreatedPublisher.java  # Kafka publisher (implements port)
    │   └── OrderCancelledConsumer.java
    └── GlobalExceptionHandler.java

business/src/main/java/io/mars/lite/order/
├── Order.java                          # Aggregate Root (record)
├── OrderCreatedEvent.java              # Domain event (record)
├── DomainResult.java                   # Generic wrapper: record DomainResult<D, E>(D domain, E event)
├── OrderRepository.java               # Port (interface)
├── OrderEventPublisher.java           # Port (interface) — publish() sends to Kafka
└── usecase/
    ├── CreateOrderUseCase.java         # Interface + Input record
    └── CreateOrderUseCaseImpl.java     # Record — calls save() then publish()
```

### Key Code: The Dual Write Sequence (What We're Exploiting)

```java
// CreateOrderUseCaseImpl.java — THIS is the fragile sequence
record CreateOrderUseCaseImpl(
    OrderRepository orderRepository,
    OrderEventPublisher orderEventPublisher
) implements CreateOrderUseCase {

    @Override
    public UUID execute(Input input) {
        var result = Order.create(input.customerId(), input.items());
        orderRepository.save(result.domain());           // 1. DB INSERT (not committed yet)
        orderEventPublisher.publish(result.event());      // 2. Kafka SEND (immediate, irreversible)
        return result.domain().id();                      // 3. Returns orderId
        // ← AOP throws HERE, BEFORE @Transactional commits → DB ROLLBACK
        // But Kafka already has the event → PHANTOM EVENT!
    }
}
```

### Key Code: OrderService @Transactional Boundary

```java
// OrderService.java — @Transactional wraps the use case
@Service
public class OrderService {
    @Transactional
    public UUID createOrder(Set<OrderItem> items, UUID customerId) {
        return createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, customerId));
    }
}
```

### Key Code: CreateOrderUseCase.Input

```java
// CreateOrderUseCase.java (interface) — Input is a nested record
public interface CreateOrderUseCase {
    UUID execute(Input input);

    record Input(Set<OrderItem> items, UUID customerId) {
        // (inferred from OrderService usage)
    }

    static CreateOrderUseCase create(OrderRepository repo, OrderEventPublisher publisher) {
        return new CreateOrderUseCaseImpl(repo, publisher);
    }
}
```

### Key Code: AbstractIntegrationTest (Base Test Class)

```java
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
    private OrderEventPublisher orderEventPublisher;  // Mocked — no real Kafka in tests

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

### Key Code: Existing E2E Test Pattern (REST Assured)

```java
// OrderControllerE2ETest.java — follow this pattern
class OrderControllerE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/orders";
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /orders - should create order and return 201")
    void shouldCreateOrderAndReturn201() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "customerId": "...", "items": [...] }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .body("orderId", notNullValue());
    }
}
```

### Desired Codebase Changes

```
app/src/main/java/io/mars/lite/api/chaos/          # NEW package
├── ChaosController.java                             # REST endpoint POST /chaos/phantom-event
├── ChaosService.java                                # Orchestrates chaos scenario
├── ChaosOrderExecutor.java                          # Thin bean wrapping UseCase (AOP target)
├── PhantomEventChaosAspect.java                     # @Aspect — forces exception after execute()
├── PhantomEventSimulationException.java             # RuntimeException carrying orderId
└── PhantomEventReport.java                          # Response DTO (Java record)

app/src/test/java/io/mars/lite/api/chaos/           # NEW test package
├── ChaosControllerE2ETest.java                      # REST Assured E2E test
└── PhantomEventChaosAspectTest.java                 # Unit test for the aspect

app/pom.xml                                          # MODIFIED — add spring-boot-starter-aop
```

### Known Gotchas

```
CRITICAL: CreateOrderUseCaseImpl is a Java RECORD (final class).
  CGLIB cannot proxy records. The AOP aspect MUST NOT target CreateOrderUseCase
  directly. Instead, target ChaosOrderExecutor (regular class, proxyable).
  This is why ChaosOrderExecutor exists as a thin wrapper.

CRITICAL: @Transactional only rolls back on unchecked exceptions by default.
  PhantomEventSimulationException MUST extend RuntimeException.

CRITICAL: Self-invocation bypasses Spring proxies (@Transactional and AOP).
  ChaosService.simulatePhantomEvent() calls chaosOrderExecutor.execute()
  (a DIFFERENT bean), not a method on itself. This ensures both the
  @Transactional proxy and the AOP proxy are triggered.

CRITICAL: AOP ordering — the aspect MUST run INSIDE the @Transactional boundary.
  Solution: @Transactional is on ChaosService (outer bean), AOP is on
  ChaosOrderExecutor (inner bean). Since they're on different beans,
  the call chain guarantees correct ordering:
    ChaosService[@Transactional] → ChaosOrderExecutor[AOP] → UseCase

CRITICAL: spring-boot-starter-aop dependency is REQUIRED for @Aspect support.
  spring-boot-starter-web does NOT include aspectjweaver.
  Add to app/pom.xml: spring-boot-starter-aop

CRITICAL: @Profile("chaos") on ALL chaos beans ensures they don't exist in
  default/test profiles. For chaos E2E tests, use @ActiveProfiles("chaos").

CRITICAL: In tests, OrderEventPublisher is @MockitoBean (no real Kafka).
  publish() is a no-op. But the AOP still throws after execute() returns,
  so the rollback behavior is testable. We verify DB rollback, not Kafka send.
  For real Kafka verification, use the chaos-testing skill against running infra.

NOTE: Port is 8082 (NOT 8081). Redpanda schema registry uses 8081.
NOTE: Reuse CreateOrderRequest from io.mars.lite.api.order — do NOT duplicate.
NOTE: Package is io.mars.lite (not com.mars).
NOTE: Java 25 + Spring Boot 4.0.3.
```

---

## Architecture: Call Chain

```
POST /chaos/phantom-event
    │
    ▼
ChaosController
    │ (parses CreateOrderRequest, converts to UseCase.Input)
    │
    ▼
ChaosService.simulatePhantomEvent()           ← NOT @Transactional (orchestrator)
    │
    ├──► ChaosService.attemptPhantomOrder()   ← @Transactional (BEGINS TX)
    │        │
    │        ▼
    │    ChaosOrderExecutorProxy.execute()    ← AOP intercepts via proxy
    │        │
    │        ▼
    │    PhantomEventChaosAspect.forceRollbackAfterPublish()
    │        │
    │        ├── joinPoint.proceed()          ← runs actual execute()
    │        │       │
    │        │       ▼
    │        │   ChaosOrderExecutor.execute()
    │        │       │
    │        │       ▼
    │        │   CreateOrderUseCase.execute()
    │        │       ├── orderRepository.save()        ← DB INSERT (not committed)
    │        │       └── orderEventPublisher.publish()  ← KAFKA SEND (immediate!)
    │        │       └── return orderId
    │        │
    │        ├── proceed() returns orderId
    │        │
    │        ╳── THROWS PhantomEventSimulationException(orderId)
    │
    │    ← exception propagates through @Transactional → DB ROLLBACK
    │
    ├── catch PhantomEventSimulationException → extract orderId
    │
    ├──► ChaosService.orderExists(orderId)    ← @Transactional(readOnly=true)
    │        │                                   (new TX after rollback)
    │        └── orderRepository.findById()   → returns empty (rolled back!)
    │
    ▼
PhantomEventReport {
    orderId: "xxx",
    existsInDb: false,         ← PROVEN: DB rolled back
    eventSentToKafka: true,    ← PROVEN: Kafka has the event
    dbRolledBack: true,
    explanation: "PHANTOM EVENT: ..."
}
```

**Why this ordering is guaranteed:**
- `@Transactional` is on `ChaosService.attemptPhantomOrder()` (outer bean)
- AOP `@Around` is on `ChaosOrderExecutor.execute()` (inner bean, called WITHIN the TX)
- Since they are on DIFFERENT beans, Spring proxy ordering is irrelevant — the TX starts first, the AOP runs inside it

---

## Implementation Blueprint

### Data Models

```java
// PhantomEventSimulationException.java — RuntimeException (unchecked → triggers rollback)
public class PhantomEventSimulationException extends RuntimeException {
    private final UUID orderId;
    // constructor, getter
}

// PhantomEventReport.java — Response DTO (Java record, separate file)
public record PhantomEventReport(
    UUID orderId,
    boolean existsInDb,
    boolean eventSentToKafka,
    boolean dbRolledBack,
    String explanation
) {}
```

### Tasks (in execution order)

```yaml
Task 1: Add spring-boot-starter-aop dependency
  module: app/
  file: app/pom.xml
  action: Add spring-boot-starter-aop dependency

Task 2: Create PhantomEventSimulationException
  module: app/
  file: app/src/main/java/io/mars/lite/api/chaos/PhantomEventSimulationException.java
  TDD: Simple enough — no test needed (it's just a RuntimeException with a UUID field)

Task 3: Create PhantomEventReport DTO
  module: app/
  file: app/src/main/java/io/mars/lite/api/chaos/PhantomEventReport.java
  TDD: Simple record — no test needed

Task 4: Create ChaosOrderExecutor (AOP target)
  module: app/
  file: app/src/main/java/io/mars/lite/api/chaos/ChaosOrderExecutor.java
  note: Thin wrapper around CreateOrderUseCase.execute()
        Exists as a separate bean so AOP can proxy it (records can't be proxied)
        @Component @Profile("chaos")

Task 5: Create PhantomEventChaosAspect (AOP interceptor)
  module: app/
  file: app/src/main/java/io/mars/lite/api/chaos/PhantomEventChaosAspect.java
  TDD: Write unit test FIRST (PhantomEventChaosAspectTest)
  test: app/src/test/java/io/mars/lite/api/chaos/PhantomEventChaosAspectTest.java

Task 6: Create ChaosService (transactional orchestrator)
  module: app/
  file: app/src/main/java/io/mars/lite/api/chaos/ChaosService.java
  note: @Service @Profile("chaos")
        attemptPhantomOrder() is @Transactional
        simulatePhantomEvent() orchestrates: try/catch + DB check
        orderExists() is @Transactional(readOnly = true)

Task 7: Create ChaosController (REST endpoint)
  module: app/
  file: app/src/main/java/io/mars/lite/api/chaos/ChaosController.java
  TDD: Write E2E test FIRST (ChaosControllerE2ETest)
  test: app/src/test/java/io/mars/lite/api/chaos/ChaosControllerE2ETest.java

Task 8: Run full validation
  command: mvn clean verify
  note: ALL tests must pass — both existing and new
```

### Per-Task Pseudocode

```java
// === Task 1: app/pom.xml ===
// Add BEFORE spring-boot-starter-test:
// <dependency>
//     <groupId>org.springframework.boot</groupId>
//     <artifactId>spring-boot-starter-aop</artifactId>
// </dependency>

// === Task 2: PhantomEventSimulationException.java ===
// Package: io.mars.lite.api.chaos
// Extends RuntimeException (unchecked → triggers @Transactional rollback)
// Field: UUID orderId
// Constructor: public PhantomEventSimulationException(UUID orderId)
// Getter: public UUID getOrderId()

// === Task 3: PhantomEventReport.java ===
// Package: io.mars.lite.api.chaos
// Java record with 5 fields: orderId, existsInDb, eventSentToKafka, dbRolledBack, explanation

// === Task 4: ChaosOrderExecutor.java ===
@Component
@Profile("chaos")
public class ChaosOrderExecutor {
    private final CreateOrderUseCase createOrderUseCase;

    // Constructor injection

    public UUID execute(CreateOrderUseCase.Input input) {
        return createOrderUseCase.execute(input);
    }
}

// === Task 5: PhantomEventChaosAspect.java ===
@Aspect
@Component
@Profile("chaos")
public class PhantomEventChaosAspect {

    private static final Logger log = LoggerFactory.getLogger(PhantomEventChaosAspect.class);

    @Around("execution(* io.mars.lite.api.chaos.ChaosOrderExecutor.execute(..))")
    public Object forceRollbackAfterPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID orderId = (UUID) joinPoint.proceed();
        // At this point: DB INSERT happened (not committed), Kafka event SENT
        log.warn("CHAOS: Forcing rollback after UseCase.execute(). "
                + "Order {} will be rolled back, but Kafka event is already sent.", orderId);
        throw new PhantomEventSimulationException(orderId);
    }
}

// === Task 6: ChaosService.java ===
@Service
@Profile("chaos")
public class ChaosService {
    private final ChaosOrderExecutor chaosOrderExecutor;
    private final OrderRepository orderRepository;

    // Constructor injection

    @Transactional
    public UUID attemptPhantomOrder(CreateOrderUseCase.Input input) {
        return chaosOrderExecutor.execute(input);
        // AOP throws → exception propagates → @Transactional rolls back
    }

    @Transactional(readOnly = true)
    public boolean orderExists(UUID orderId) {
        return orderRepository.findById(orderId).isPresent();
    }

    public PhantomEventReport simulatePhantomEvent(CreateOrderUseCase.Input input) {
        UUID orderId = null;
        boolean dbRolledBack = false;

        try {
            attemptPhantomOrder(input);
        } catch (PhantomEventSimulationException e) {
            orderId = e.getOrderId();
            dbRolledBack = true;
        }

        boolean existsInDb = orderExists(orderId);

        return new PhantomEventReport(
            orderId,
            existsInDb,
            true,  // Kafka event was sent (publish() ran before the throw)
            dbRolledBack,
            "PHANTOM EVENT: The order.created event was published to Kafka, "
            + "but the order does NOT exist in PostgreSQL. "
            + "Any consumer processing this event will reference a non-existent order."
        );
    }
}
// IMPORTANT: simulatePhantomEvent() calls attemptPhantomOrder() — this is a
// self-invocation. In Spring, self-invocation bypasses the proxy, so
// @Transactional on attemptPhantomOrder() will NOT be triggered.
//
// FIX: Inject self-reference or extract attemptPhantomOrder to a separate bean.
//
// SIMPLEST FIX: Make ChaosService itself not have @Transactional on
// simulatePhantomEvent(), and let the controller call attemptPhantomOrder()
// directly. OR better: inject ChaosService into itself via ObjectProvider.
//
// CLEANEST FIX: Split into two beans — ChaosTransactionalExecutor and ChaosService.
// But that adds yet another class. Instead, use this approach:

// REVISED ChaosService.java — NO self-invocation
@Service
@Profile("chaos")
public class ChaosService {
    private final ChaosOrderExecutor chaosOrderExecutor;
    private final OrderRepository orderRepository;

    // Only two methods, both called from ChaosController (external invocation)

    @Transactional
    public UUID attemptPhantomOrder(CreateOrderUseCase.Input input) {
        return chaosOrderExecutor.execute(input);
    }

    @Transactional(readOnly = true)
    public boolean orderExists(UUID orderId) {
        return orderRepository.findById(orderId).isPresent();
    }
}

// === Task 7: ChaosController.java ===
@RestController
@RequestMapping("/chaos")
@Profile("chaos")
public class ChaosController {
    private final ChaosService chaosService;

    // Constructor injection

    @PostMapping("/phantom-event")
    public ResponseEntity<PhantomEventReport> simulatePhantomEvent(
            @RequestBody CreateOrderRequest request) {
        // Reuse CreateOrderRequest from io.mars.lite.api.order
        var items = request.items().stream()
            .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());
        var input = new CreateOrderUseCase.Input(items, request.customerId());

        UUID orderId = null;
        boolean dbRolledBack = false;

        try {
            chaosService.attemptPhantomOrder(input);
        } catch (PhantomEventSimulationException e) {
            orderId = e.getOrderId();
            dbRolledBack = true;
        }

        boolean existsInDb = chaosService.orderExists(orderId);

        var report = new PhantomEventReport(
            orderId,
            existsInDb,
            true,
            dbRolledBack,
            "PHANTOM EVENT: The order.created event was published to Kafka, "
            + "but the order does NOT exist in PostgreSQL. "
            + "Any consumer processing this event will reference a non-existent order."
        );

        return ResponseEntity.ok(report);
    }
}
```

### Integration Points

```yaml
DATABASE:
  - No new tables or migrations needed
  - Uses existing orders + order_items tables
  - DB INSERT is rolled back (phantom scenario)

KAFKA:
  - No new topics — uses existing order.created
  - Phantom event published to order.created via existing OrderCreatedPublisher
  - In tests: OrderEventPublisher is mocked (no real Kafka)

CONFIGURATION:
  - app/pom.xml: Add spring-boot-starter-aop dependency
  - No changes to application.yaml needed
  - @Profile("chaos") activates with: SPRING_PROFILES_ACTIVE=chaos
  - No changes to UseCaseConfiguration.java (chaos beans auto-discovered)

AOP:
  - spring-boot-starter-aop provides aspectjweaver + auto-proxy configuration
  - @EnableAspectJAutoProxy is auto-configured by Spring Boot
  - No manual @EnableAspectJAutoProxy needed
```

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile
# Expected: BUILD SUCCESS
# Verify: no import errors, spring-boot-starter-aop resolves
```

### Level 2: Unit Tests (business/)
```bash
mvn test -pl business
# Expected: All existing tests PASS
# No changes to business module — chaos is purely app/ infrastructure
```

### Level 3: Integration Tests (data-provider/)
```bash
mvn test -pl data-provider
# Expected: All existing tests PASS
# No changes to data-provider module
```

### Level 4: App Tests (service + E2E)
```bash
mvn test -pl app
# Expected: All existing tests PASS + new chaos tests PASS
# New tests:
#   - ChaosControllerE2ETest (REST Assured, @ActiveProfiles("chaos"))
#   - PhantomEventChaosAspectTest (unit test for aspect logic)
```

### Level 5: Full Build
```bash
mvn clean verify
# Must pass completely before considering work done
```

---

## Test Specifications

### PhantomEventChaosAspectTest (Unit Test)

```java
// app/src/test/java/io/mars/lite/api/chaos/PhantomEventChaosAspectTest.java
// Tests the AOP aspect logic in isolation (no Spring context needed)

@Test
@DisplayName("should throw PhantomEventSimulationException after proceed returns")
void shouldThrowAfterProceedReturns() {
    // Given: a ProceedingJoinPoint that returns a UUID
    // When: forceRollbackAfterPublish is called
    // Then: PhantomEventSimulationException is thrown with the orderId
}

@Test
@DisplayName("should carry the correct orderId in the exception")
void shouldCarryCorrectOrderIdInException() {
    // Given: proceed() returns a specific UUID
    // When: aspect intercepts
    // Then: exception.getOrderId() matches the UUID
}
```

### ChaosControllerE2ETest (REST Assured E2E)

```java
// app/src/test/java/io/mars/lite/api/chaos/ChaosControllerE2ETest.java
// IMPORTANT: Must use @ActiveProfiles("chaos") to activate chaos beans
// IMPORTANT: Extends AbstractIntegrationTest for TestContainers PostgreSQL

@ActiveProfiles("chaos")
class ChaosControllerE2ETest extends AbstractIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /chaos/phantom-event - should return report with existsInDb false")
    void shouldReturnPhantomEventReport() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                         "quantity": 2, "unitPrice": 149.95}
                    ]
                }
                """)
        .when()
            .post("/chaos/phantom-event")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("orderId", notNullValue())
            .body("existsInDb", equalTo(false))
            .body("eventSentToKafka", equalTo(true))
            .body("dbRolledBack", equalTo(true))
            .body("explanation", containsString("PHANTOM EVENT"));
    }

    @Test
    @DisplayName("POST /chaos/phantom-event - order should NOT exist in database after call")
    void shouldNotPersistOrderInDatabase() {
        var orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                         "quantity": 1, "unitPrice": 50.00}
                    ]
                }
                """)
        .when()
            .post("/chaos/phantom-event")
        .then()
            .statusCode(200)
            .extract().jsonPath().getString("orderId");

        // Verify directly in DB: order should NOT exist
        assertThat(orderJpaRepository.findById(UUID.fromString(orderId))).isEmpty();
    }

    @Test
    @DisplayName("POST /orders - normal endpoint should still work (chaos is isolated)")
    void shouldNotAffectNormalOrderCreation() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                         "quantity": 1, "unitPrice": 25.00}
                    ]
                }
                """)
        .when()
            .post("/orders")
        .then()
            .statusCode(201)
            .body("orderId", notNullValue());

        // Normal order SHOULD exist in DB
        assertThat(orderJpaRepository.count()).isEqualTo(1);
    }
}
```

---

## Final Checklist

- [ ] All tests pass: `mvn clean verify`
- [ ] TDD followed: tests written before production code
- [ ] Java records used for PhantomEventReport
- [ ] Request DTO reused from existing CreateOrderRequest (no duplication)
- [ ] No changes to business/ module (chaos is infrastructure)
- [ ] No changes to data-provider/ module
- [ ] AOP aspect is the mechanism forcing the failure
- [ ] @Profile("chaos") on all chaos beans
- [ ] spring-boot-starter-aop added to app/pom.xml
- [ ] PhantomEventSimulationException extends RuntimeException
- [ ] No self-invocation issues (controller → service → executor, all different beans)
- [ ] Existing tests unaffected
- [ ] E2E test with REST Assured validates full flow
- [ ] Normal POST /orders endpoint unaffected by chaos

---

## Anti-Patterns to Avoid

- Don't put AOP on CreateOrderUseCaseImpl directly — it's a record (final), CGLIB can't proxy it
- Don't put @Transactional and AOP on the same method of the same bean — ordering issues
- Don't use self-invocation for @Transactional methods — bypasses proxy
- Don't add chaos classes to business/ or data-provider/ — chaos is app-layer infrastructure
- Don't create a new Request DTO — reuse CreateOrderRequest
- Don't skip @Profile("chaos") — chaos beans must not load in default profile
- Don't catch PhantomEventSimulationException in GlobalExceptionHandler — the controller handles it
- Don't make PhantomEventSimulationException checked — @Transactional won't roll back

---

## Confidence Score: 9/10

- Context completeness: 10/10 — all relevant code, patterns, and gotchas documented
- Pattern availability in codebase: 9/10 — follows existing controller/service/test patterns
- Validation gate coverage: 9/10 — E2E + unit test + full build verification
- One-pass implementation likelihood: 8/10 — AOP proxy for ChaosOrderExecutor should work with CGLIB (regular class), but Spring Boot 4 AOP auto-config behavior may have edge cases to handle at runtime
