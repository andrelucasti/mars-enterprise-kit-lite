# PRP 04 — API & Events Layer (app/ module)

## Purpose
Implement the REST API, Kafka event publishing (Dual Write), Kafka event consuming, service layer, and wire all dependencies together — completing the Mars Enterprise Kit Lite order service.

### Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests the AI can run and fix
3. **TDD Mandatory**: Every feature follows Red-Green-Refactor
4. **Onion Architecture**: Dependencies always point inward (app -> data-provider -> business)
5. **Global Rules**: Follow all rules in CLAUDE.md

---

## Goal
A fully functional Order microservice with:
- REST API (POST /orders, GET /orders/{id})
- Kafka publisher for OrderCreated events (Dual Write — no atomicity guarantee)
- Kafka consumer for order.cancelled topic
- Service layer with @Transactional coordination
- All tests passing (service integration + E2E with REST Assured)

## Why
- This is the final layer that makes the service usable by clients
- The Dual Write pattern (DB save + Kafka publish without atomicity) is the educational core of the Lite product
- REST Assured E2E tests validate the complete request/response cycle
- Service integration tests verify ACID transaction guarantees
- The Kafka consumer demonstrates the full event-driven cycle

## What
A developer can:
1. `POST /orders` — creates an order, returns 201 with orderId, publishes event to Kafka
2. `GET /orders/{id}` — retrieves an order by ID, returns 200 with full order details
3. Publish a message to `order.cancelled` topic — order gets cancelled automatically
4. All tests pass: `mvn clean verify`

### Success Criteria
- [ ] POST /orders returns 201 with orderId (E2E test)
- [ ] POST /orders with invalid payload returns 400 (E2E test)
- [ ] GET /orders/{id} returns 200 with order details (E2E test)
- [ ] GET /orders/{id} returns 404 when not found (E2E test)
- [ ] OrderCreatedPublisher publishes to Kafka after DB save (Dual Write)
- [ ] OrderCancelledConsumer consumes from Kafka and cancels order
- [ ] Service integration tests verify @Transactional behavior
- [ ] Full build passes: `mvn clean verify`

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project conventions, testing strategy, REST Assured patterns

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: REST endpoints (section 4.4), event payloads (section 5.5), Dual Write explanation (section 5.2)

- file: .mars/PRPs/02-domain-layer.md
  why: Domain objects, use cases, ports that this layer implements/uses

- file: .mars/PRPs/03-persistence-layer.md
  why: Repository adapter, entities that service layer depends on
```

### Reference Implementation Patterns

**OrderController (REST endpoints):**
```java
// Reference: com.mars.order.api.OrderController
// Delegates to OrderService, returns ResponseEntity
// Uses @RequestBody for input, maps to domain objects
```

**OrderService (@Transactional coordination):**
```java
// Reference: com.mars.order.OrderService
// @Service + @Transactional
// Wraps use case calls with transaction boundaries
// Constructor injection with Objects.requireNonNull()
```

**OrderEventPublisherImpl (Kafka producer):**
```java
// Reference: com.mars.order.api.event.OrderEventPublisherImpl
// In Lite: uses KafkaTemplate<String, String> with JSON serialization
// DUAL WRITE: publish happens AFTER db save, INSIDE @Transactional
// No Outbox, no atomicity guarantee — this IS the point
```

**E2E Test Pattern (REST Assured BDD):**
```java
// Reference: com.mars.order.api.OrderControllerE2ETest
// given().contentType(JSON).body(requestJson)
// .when().post()
// .then().statusCode(201).body("orderId", notNullValue())
```

### Current Codebase Structure
```
mars-enterprise-kit-lite/
├── business/src/main/java/io/mars/lite/order/
│   ├── Order.java, OrderStatus.java, OrderItem.java
│   ├── OrderRepository.java, OrderEventPublisher.java
│   ├── BusinessException.java, DomainResult.java, OrderCreatedEvent.java
│   └── usecase/ (CreateOrderUseCase, CancelOrderUseCase + impls)
├── data-provider/src/main/java/io/mars/lite/
│   ├── configuration/DataSourceConfiguration.java
│   └── order/ (OrderEntity, OrderItemEntity, OrderJpaRepository, OrderRepositoryImpl)
├── app/
│   ├── pom.xml (has web, kafka, actuator, validation, rest-assured deps)
│   └── src/
│       ├── main/java/io/mars/lite/
│       │   ├── Application.java
│       │   └── configuration/ (empty)
│       ├── main/resources/application.yaml
│       └── test/java/io/mars/lite/
```

### Desired Codebase Changes
```
app/src/main/java/io/mars/lite/
├── Application.java                           # EXISTS (from PRP 01)
├── configuration/
│   └── UseCaseConfiguration.java              # NEW: Wire use case beans
├── api/
│   ├── order/
│   │   ├── OrderController.java               # NEW: REST endpoints
│   │   ├── CreateOrderRequest.java            # NEW: Request DTO (record)
│   │   ├── OrderResponse.java                 # NEW: Response DTO (record)
│   │   └── OrderService.java                  # NEW: @Service + @Transactional
│   ├── event/
│   │   ├── OrderCreatedPublisher.java         # NEW: Kafka producer (Dual Write)
│   │   └── OrderCancelledConsumer.java        # NEW: Kafka consumer
│   └── GlobalExceptionHandler.java            # NEW: @RestControllerAdvice

app/src/test/java/io/mars/lite/
├── AbstractIntegrationTest.java               # NEW: TestContainers base class
├── api/
│   ├── order/
│   │   ├── OrderControllerE2ETest.java        # NEW: REST Assured E2E tests
│   │   └── OrderServiceIntegrationTest.java   # NEW: Service integration tests
│   └── event/
│       └── OrderCancelledConsumerIntegrationTest.java  # NEW: Kafka consumer test
```

### Known Gotchas
```java
// CRITICAL: DUAL WRITE PATTERN — this is the intentional anti-pattern
//   - OrderCreatedPublisher.publish() uses KafkaTemplate INSIDE the use case flow
//   - If Kafka fails after DB commit → event is lost (this IS the problem Lite demonstrates)
//   - Do NOT add any Outbox, retry, or compensation logic
//   - The @Transactional only covers the database — Kafka is outside the transaction boundary
//
// CRITICAL: OrderEventPublisher interface is in business/ (port)
//   - OrderCreatedPublisher in app/ IMPLEMENTS OrderEventPublisher from business/
//   - This follows the Onion Architecture: business defines the contract, app implements it
//
// CRITICAL: Request/Response DTOs are SEPARATE FILES (Java records)
//   - CreateOrderRequest.java — NOT an inner class in OrderController
//   - OrderResponse.java — NOT an inner class in OrderController
//
// CRITICAL: OrderService is the @Transactional boundary
//   - Controller → OrderService → UseCase → Repository + EventPublisher
//   - @Transactional is on OrderService methods, NOT on use cases
//
// CRITICAL: KafkaTemplate<String, String> for JSON events
//   - Key: orderId as String
//   - Value: JSON string (use ObjectMapper to serialize)
//   - Topic: "order.created" for publishing
//   - Consumer group: "order-service" consuming from "order.cancelled"
//
// CRITICAL: REST Assured E2E tests need TestContainers for PostgreSQL
//   - Use AbstractIntegrationTest base class (like the reference)
//   - Kafka can be disabled in tests OR use embedded/TestContainers Kafka
//   - For simplicity: mock the Kafka producer in E2E tests OR disable Kafka auto-config
//
// CRITICAL: CancelOrderUseCase is triggered by Kafka consumer
//   - OrderCancelledConsumer listens to "order.cancelled" topic
//   - Extracts orderId from JSON payload
//   - Calls CancelOrderUseCase.execute(orderId)
//
// Package structure:
//   io.mars.lite.api.order — controllers, DTOs, service
//   io.mars.lite.api.event — Kafka publisher/consumer
//   io.mars.lite.configuration — UseCaseConfiguration
```

---

## Implementation Blueprint

### Data Models

```java
// === REQUEST/RESPONSE DTOs (app/ module, separate files) ===

// CreateOrderRequest.java
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

    public record ItemRequest(
        UUID productId,
        int quantity,
        BigDecimal unitPrice
    ) {
        public ItemRequest {
            Objects.requireNonNull(productId, "productId cannot be null");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("unitPrice must be positive");
        }
    }
}

// OrderResponse.java
public record OrderResponse(
    UUID id,
    UUID customerId,
    String status,
    BigDecimal total,
    Set<ItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        var itemResponses = order.items().stream()
            .map(i -> new ItemResponse(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());
        return new OrderResponse(
            order.id(), order.customerId(), order.status().name(),
            order.total(), itemResponses, order.createdAt(), order.updatedAt());
    }

    public record ItemResponse(UUID productId, int quantity, BigDecimal unitPrice) {}
}

// Kafka consumer payload record (for deserializing order.cancelled messages)
// This can be a private record inside OrderCancelledConsumer or a separate file
public record OrderCancelledPayload(
    UUID eventId,
    UUID orderId,
    String reason,
    Instant occurredAt
) {}
```

### Tasks (in execution order)

```yaml
Task 1: AbstractIntegrationTest base class
  module: app/ (test sources)
  files:
    - src/test/java/io/mars/lite/AbstractIntegrationTest.java
  tests: Base class for all integration tests

Task 2: UseCaseConfiguration + Service Layer
  module: app/
  files:
    - src/main/java/io/mars/lite/configuration/UseCaseConfiguration.java
    - src/main/java/io/mars/lite/api/order/OrderService.java
  tests:
    - src/test/java/io/mars/lite/api/order/OrderServiceIntegrationTest.java (FIRST)
  TDD: Red -> Green -> Refactor

Task 3: REST Controller + DTOs
  module: app/
  files:
    - src/main/java/io/mars/lite/api/order/OrderController.java
    - src/main/java/io/mars/lite/api/order/CreateOrderRequest.java
    - src/main/java/io/mars/lite/api/order/OrderResponse.java
    - src/main/java/io/mars/lite/api/GlobalExceptionHandler.java
  tests:
    - src/test/java/io/mars/lite/api/order/OrderControllerE2ETest.java (FIRST)
  TDD: Red -> Green -> Refactor

Task 4: Kafka Producer (Dual Write)
  module: app/
  files:
    - src/main/java/io/mars/lite/api/event/OrderCreatedPublisher.java
  tests: Verified by E2E tests (order creation publishes event)
  Note: The publisher implements OrderEventPublisher from business/

Task 5: Kafka Consumer
  module: app/
  files:
    - src/main/java/io/mars/lite/api/event/OrderCancelledConsumer.java
  tests:
    - src/test/java/io/mars/lite/api/event/OrderCancelledConsumerIntegrationTest.java
  TDD: Red -> Green -> Refactor (optional — Kafka consumer tests are complex)
```

### Per-Task Pseudocode

```java
// Task 1 — AbstractIntegrationTest
// Base class with TestContainers PostgreSQL
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("orders_db")
        .withUsername("mars")
        .withPassword("mars");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.hikari.jdbcUrl", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.hikari.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // Disable Kafka auto-connect in tests (prevents connection errors)
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
    }
}

// Task 2 — UseCaseConfiguration
@Configuration
public class UseCaseConfiguration {

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orderRepository,
                                                  OrderEventPublisher orderEventPublisher) {
        return CreateOrderUseCase.create(orderRepository, orderEventPublisher);
    }

    @Bean
    public CancelOrderUseCase cancelOrderUseCase(OrderRepository orderRepository) {
        return CancelOrderUseCase.create(orderRepository);
    }
}

// Task 2 — OrderService
@Service
public class OrderService {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderRepository orderRepository;

    public OrderService(CreateOrderUseCase createOrderUseCase,
                        CancelOrderUseCase cancelOrderUseCase,
                        OrderRepository orderRepository) {
        this.createOrderUseCase = Objects.requireNonNull(createOrderUseCase);
        this.cancelOrderUseCase = Objects.requireNonNull(cancelOrderUseCase);
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @Transactional
    public UUID createOrder(Set<OrderItem> items, UUID customerId) {
        return createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, customerId));
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        cancelOrderUseCase.execute(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return orderRepository.findById(orderId);
    }
}

// Task 3 — OrderController
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> createOrder(
            @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
            .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());

        var orderId = orderService.createOrder(items, request.customerId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("orderId", orderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
            .map(OrderResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}

// Task 3 — GlobalExceptionHandler
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }
}

// Task 4 — OrderCreatedPublisher (Dual Write — the intentional anti-pattern)
@Service
public class OrderCreatedPublisher implements OrderEventPublisher {

    private static final String TOPIC = "order.created";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderCreatedPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.orderId().toString(), payload);
            // ⚠️ DUAL WRITE: No atomicity with DB transaction.
            // If this fails after DB commit, the event is LOST.
            // This is intentional — the Outbox pattern solves this, but is out of scope for Lite.
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
        }
    }
}

// Task 5 — OrderCancelledConsumer
@Service
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderCancelledConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.cancelled", groupId = "order-service")
    public void onOrderCancelled(String message) {
        try {
            var payload = objectMapper.readValue(message, OrderCancelledPayload.class);
            log.info("Received order.cancelled event for orderId={}", payload.orderId());
            orderService.cancelOrder(payload.orderId());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize order.cancelled event", e);
        } catch (BusinessException e) {
            log.warn("Failed to cancel order: {}", e.getMessage());
        }
    }

    // Payload record for deserialization
    record OrderCancelledPayload(UUID eventId, UUID orderId, String reason, Instant occurredAt) {}
}
```

### Test Patterns

```java
// === SERVICE INTEGRATION TEST ===
class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

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

        var orderId = orderService.createOrder(items, customerId);

        assertThat(orderId).isNotNull();
        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("should cancel existing order")
    void shouldCancelExistingOrder() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var orderId = orderService.createOrder(items, UUID.randomUUID());

        orderService.cancelOrder(orderId);

        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should find order by id")
    void shouldFindOrderById() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("99.99")));
        var orderId = orderService.createOrder(items, UUID.randomUUID());

        var found = orderService.findById(orderId);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("should throw when cancelling non-existent order")
    void shouldThrowWhenCancellingNonExistentOrder() {
        assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID()))
            .isInstanceOf(BusinessException.class);
    }
}

// === E2E CONTROLLER TEST (REST Assured BDD) ===
class OrderControllerE2ETest extends AbstractIntegrationTest {

    private static final String UUID_REGEX =
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {
                            "productId": "123e4567-e89b-12d3-a456-426614174000",
                            "quantity": 2,
                            "unitPrice": 149.95
                        }
                    ]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("orderId", notNullValue())
            .body("orderId", matchesPattern(UUID_REGEX));
    }

    @Test
    @DisplayName("POST /orders - should return 400 when customerId is null")
    void shouldReturn400WhenCustomerIdIsNull() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": null,
                    "items": [{"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 2, "unitPrice": 10.00}]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("POST /orders - should return 400 when items are empty")
    void shouldReturn400WhenItemsAreEmpty() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": []
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("GET /orders/{id} - should return order details")
    void shouldReturnOrderDetails() {
        // Create order first
        var orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": "550e8400-e29b-41d4-a716-446655440000",
                    "items": [
                        {"productId": "123e4567-e89b-12d3-a456-426614174000", "quantity": 1, "unitPrice": 99.99}
                    ]
                }
                """)
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("orderId");

        // Get order
        given()
        .when()
            .get("/{id}", orderId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(orderId))
            .body("status", equalTo("CREATED"))
            .body("items", hasSize(1));
    }

    @Test
    @DisplayName("GET /orders/{id} - should return 404 when order not found")
    void shouldReturn404WhenOrderNotFound() {
        given()
        .when()
            .get("/{id}", UUID.randomUUID().toString())
        .then()
            .statusCode(404);
    }
}
```

### Integration Points
```yaml
DATABASE:
  - No new migrations (tables from PRP 03)
  - Service layer uses OrderRepository via use cases

KAFKA (Dual Write):
  - topic "order.created": Published by OrderCreatedPublisher after DB save
    payload: {"eventId":"uuid","orderId":"uuid","customerId":"uuid","totalAmount":99.99,
              "items":[{"productId":"uuid","quantity":2,"unitPrice":49.99}],"occurredAt":"2026-02-21T10:00:00Z"}
  - topic "order.cancelled": Consumed by OrderCancelledConsumer
    payload: {"eventId":"uuid","orderId":"uuid","reason":"Customer requested","occurredAt":"2026-02-21T10:05:00Z"}

CONFIGURATION:
  - UseCaseConfiguration.java wires CreateOrderUseCase + CancelOrderUseCase beans
  - application.yaml already has Kafka + DB config (from PRP 01)
  - OrderCreatedPublisher implements OrderEventPublisher from business/ (auto-discovered by Spring)
```

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile
# Expected: BUILD SUCCESS for all modules
```

### Level 2: Unit Tests (business/)
```bash
mvn test -pl business
# Expected: BUILD SUCCESS (domain tests from PRP 02)
```

### Level 3: Integration Tests (data-provider/)
```bash
mvn test -pl data-provider
# Expected: BUILD SUCCESS (repository tests from PRP 03)
```

### Level 4: App Tests (service + E2E)
```bash
mvn test -pl app
# Expected: BUILD SUCCESS
# Tests: OrderServiceIntegrationTest, OrderControllerE2ETest
# Docker must be running for TestContainers
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
- [ ] Java records used for CreateOrderRequest, OrderResponse (separate files)
- [ ] REST Assured E2E tests cover POST /orders and GET /orders/{id}
- [ ] Service integration tests verify create, cancel, findById
- [ ] OrderCreatedPublisher implements OrderEventPublisher (from business/)
- [ ] Dual Write pattern used (NO Outbox)
- [ ] OrderCancelledConsumer listens to "order.cancelled" topic
- [ ] UseCaseConfiguration wires both use cases
- [ ] GlobalExceptionHandler handles BusinessException -> 400
- [ ] Kafka bootstrap-servers disabled in tests (localhost:0)

---

## Anti-Patterns to Avoid
- Don't implement Outbox pattern — Lite uses Dual Write intentionally
- Don't define DTOs as inner classes in controllers
- Don't add @Transactional to use cases — only to OrderService
- Don't use Avro or Schema Registry — JSON only
- Don't skip REST Assured tests — every controller needs E2E coverage
- Don't put business logic in OrderService — it only coordinates
- Don't catch generic exceptions in consumer — catch specific ones
- Don't use @Autowired field injection — use constructor injection
- Don't forget to handle 404 for GET /orders/{id}
- Don't make Kafka consumer retry on BusinessException (e.g., already cancelled)

---

## Confidence Score: 8/10
- Context completeness: 9 (all endpoints, events, and tests fully specified)
- Pattern availability in codebase: 8 (reference provides all patterns, adapted for Lite)
- Validation gate coverage: 8 (E2E + service integration tests)
- One-pass implementation likelihood: 7 (Kafka integration + TestContainers can be tricky, but patterns are detailed)
