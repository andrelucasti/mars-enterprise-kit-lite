# PRP 03 — Persistence Layer (data-provider/ module)

## Purpose
Implement the infrastructure layer with JPA entities, repository adapters, Flyway migrations, and database configuration — connecting the domain model to PostgreSQL.

### Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests the AI can run and fix
3. **TDD Mandatory**: Every feature follows Red-Green-Refactor
4. **Onion Architecture**: Dependencies always point inward (app -> data-provider -> business)
5. **Global Rules**: Follow all rules in CLAUDE.md

---

## Goal
A complete persistence layer in `data-provider/` with JPA entities mapping to normalized tables (orders + order_items), a repository adapter implementing the business port, Flyway migration creating the schema, and integration tests verifying everything with TestContainers.

## Why
- Bridges the gap between domain model and database
- Flyway migrations ensure reproducible schema across environments
- Normalized tables (orders + order_items) match the context-lite.md specification
- TestContainers integration tests prove JPA mappings work correctly
- The adapter pattern keeps business/ decoupled from persistence details

## What
1. `OrderEntity` + `OrderItemEntity` JPA entities mapping to normalized tables
2. `OrderJpaRepository` Spring Data JPA repository
3. `OrderRepositoryImpl` adapter implementing `OrderRepository` port from business/
4. `DataSourceConfiguration` for JPA auditing
5. `V1__create_orders_table.sql` Flyway migration
6. Integration tests verifying save, findById, and update operations

### Success Criteria
- [ ] Integration tests pass: `mvn test -pl data-provider`
- [ ] Flyway migration creates orders + order_items tables
- [ ] OrderRepositoryImpl.save() persists Order with items
- [ ] OrderRepositoryImpl.findById() reconstructs Order domain object
- [ ] OrderRepositoryImpl.update() updates order status
- [ ] JPA audit fields (createdAt, updatedAt) are populated automatically
- [ ] No domain logic in data-provider/ — pure mapping and persistence

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project conventions, testing strategy, module boundaries

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Database schema (section 5.4), table definitions

- file: .mars/PRPs/01-project-bootstrap.md
  why: POM dependencies, application.yaml configuration

- file: .mars/PRPs/02-domain-layer.md
  why: Domain objects that the persistence layer maps to
```

### Reference Implementation Patterns

**JPA Entity with Audit Fields:**
```java
// Reference: com.mars.order.OrderEntity
// Uses @CreatedDate, @LastModifiedDate, @EntityListeners(AuditingEntityListener.class)
// Has static of(Order) factory and toDomain() conversion methods
// Reference uses JSONB for items — Lite uses NORMALIZED tables instead
```

**Repository Adapter Pattern:**
```java
// Reference: com.mars.order.OrderRepositoryImpl
// @Repository annotation
// Constructor injection of JpaRepository
// Delegates to JPA, maps between Entity and Domain
```

### Current Codebase Structure
```
mars-enterprise-kit-lite/
├── business/src/main/java/io/mars/lite/order/
│   ├── Order.java            # Aggregate Root (from PRP 02)
│   ├── OrderStatus.java      # Enum: CREATED, CANCELLED
│   ├── OrderItem.java        # Value Object record
│   ├── OrderRepository.java  # Port interface
│   └── ...
├── data-provider/
│   ├── pom.xml               # Has JPA, PostgreSQL, Flyway deps (from PRP 01)
│   └── src/
│       ├── main/java/io/mars/lite/ (empty)
│       ├── main/resources/db/migration/ (empty)
│       └── test/java/io/mars/lite/ (empty)
└── app/ (from PRP 01)
```

### Desired Codebase Changes
```
data-provider/src/main/java/io/mars/lite/
├── configuration/
│   └── DataSourceConfiguration.java       # NEW: Enable JPA Auditing
└── order/
    ├── OrderEntity.java                    # NEW: JPA Entity for orders table
    ├── OrderItemEntity.java                # NEW: JPA Entity for order_items table
    ├── OrderJpaRepository.java             # NEW: Spring Data JPA interface
    └── OrderRepositoryImpl.java            # NEW: Adapter implements OrderRepository

data-provider/src/main/resources/db/migration/
└── V1__create_orders_table.sql             # NEW: Flyway migration

data-provider/src/test/java/io/mars/lite/order/
└── OrderRepositoryImplIntegrationTest.java # NEW: Integration test
```

### Known Gotchas
```java
// CRITICAL: Lite uses NORMALIZED tables (orders + order_items)
//   - NOT JSONB for items (reference uses JSONB — don't copy that)
//   - order_items table has FK to orders(id)
//
// CRITICAL: OrderEntity and OrderItemEntity are SEPARATE from domain objects
//   - OrderEntity maps to `orders` table
//   - OrderItemEntity maps to `order_items` table
//   - Conversion methods: OrderEntity.of(Order) and orderEntity.toDomain()
//
// CRITICAL: Use @OneToMany/@ManyToOne for the Entity relationship
//   - OrderEntity has Set<OrderItemEntity> with cascade ALL
//   - OrderItemEntity has a back-reference to OrderEntity
//
// CRITICAL: No business logic in entities — just mapping
//   - Don't put cancel(), create(), or validation in entities
//   - Entities are dumb data carriers between domain and database
//
// CRITICAL: Database columns must match context-lite.md section 5.4
//   - orders: id (UUID PK), customer_id (UUID), status (VARCHAR 20), total (NUMERIC 10,2),
//             created_at (TIMESTAMP), updated_at (TIMESTAMP)
//   - order_items: id (UUID PK), order_id (UUID FK), product_id (UUID),
//                  quantity (INT), unit_price (NUMERIC 10,2)
//
// CRITICAL: Integration tests use TestContainers (PostgreSQL 16-alpine)
//   - Need AbstractIntegrationTest base class in data-provider/ test
//   - @SpringBootTest requires Application.class from app/ — use @DataJpaTest instead
//     OR create a minimal test configuration in data-provider/
//
// CRITICAL: DataSourceConfiguration enables JPA Auditing
//   - @EnableJpaAuditing annotation
//   - @Configuration class in data-provider/
//
// Package: io.mars.lite.order for entities (NOT io.mars.lite.configuration)
//   Exception: DataSourceConfiguration is in io.mars.lite.configuration
```

---

## Implementation Blueprint

### Data Models

```java
// === JPA ENTITIES (data-provider/ module) ===

// OrderEntity.java
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<OrderItemEntity> items = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {} // JPA requires no-arg constructor

    // Factory: Domain -> Entity
    public static OrderEntity of(final Order order) {
        var entity = new OrderEntity();
        entity.id = order.id();
        entity.customerId = order.customerId();
        entity.status = order.status();
        entity.total = order.total();
        entity.createdAt = order.createdAt();
        entity.updatedAt = order.updatedAt();

        order.items().forEach(item -> {
            var itemEntity = new OrderItemEntity();
            itemEntity.setId(UUID.randomUUID());
            itemEntity.setProductId(item.productId());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setOrder(entity);
            entity.items.add(itemEntity);
        });

        return entity;
    }

    // Entity -> Domain
    public Order toDomain() {
        var domainItems = items.stream()
            .map(e -> new OrderItem(e.getProductId(), e.getQuantity(), e.getUnitPrice()))
            .collect(Collectors.toSet());

        return Order.reconstitute(id, customerId, status, domainItems, total,
                                  createdAt, updatedAt);
    }

    // Used by update operations
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    // Getters for test assertions
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public Set<OrderItemEntity> getItems() { return items; }
}

// OrderItemEntity.java
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItemEntity() {} // JPA requires no-arg constructor

    // Getters and setters (JPA entities need setters)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public OrderEntity getOrder() { return order; }
    public void setOrder(OrderEntity order) { this.order = order; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
```

```sql
-- V1__create_orders_table.sql
-- Source: mars-enterprise-kit-context-lite.md section 5.4
CREATE TABLE orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total       NUMERIC(10,2) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders(id),
    product_id  UUID        NOT NULL,
    quantity    INT         NOT NULL,
    unit_price  NUMERIC(10,2) NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### Tasks (in execution order)

```yaml
Task 1: Flyway Migration
  module: data-provider/
  files:
    - src/main/resources/db/migration/V1__create_orders_table.sql
  tests: Will be validated by integration tests
  TDD: Migration runs when tests start TestContainers

Task 2: JPA Entities
  module: data-provider/
  files:
    - src/main/java/io/mars/lite/order/OrderEntity.java
    - src/main/java/io/mars/lite/order/OrderItemEntity.java
  tests: Tested via repository integration tests
  TDD: Red (integration test) -> Green (entities + adapter)

Task 3: DataSourceConfiguration
  module: data-provider/
  files:
    - src/main/java/io/mars/lite/configuration/DataSourceConfiguration.java
  tests: Tested implicitly by integration tests (@CreatedDate/@LastModifiedDate)

Task 4: Spring Data Repository + Adapter
  module: data-provider/
  files:
    - src/main/java/io/mars/lite/order/OrderJpaRepository.java
    - src/main/java/io/mars/lite/order/OrderRepositoryImpl.java
  tests:
    - src/test/java/io/mars/lite/order/OrderRepositoryImplIntegrationTest.java (FIRST)
  TDD: Red -> Green -> Refactor
```

### Per-Task Pseudocode

```java
// Task 3 — DataSourceConfiguration
@Configuration
@EnableJpaAuditing
public class DataSourceConfiguration {
    // Enable JPA auditing for @CreatedDate/@LastModifiedDate
}

// Task 4 — Spring Data Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {}

// Task 4 — Repository Adapter
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Order order) {
        jpaRepository.save(OrderEntity.of(order));
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(OrderEntity::toDomain);
    }

    @Override
    public void update(Order order) {
        var entity = jpaRepository.findById(order.id())
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + order.id()));
        entity.updateStatus(order.status());
        jpaRepository.save(entity);
    }
}
```

### Test Patterns

```java
// === INTEGRATION TEST (data-provider/ module) ===
// CRITICAL: data-provider/ does NOT have Application.java
// Use @DataJpaTest with @AutoConfigureTestDatabase(replace = NONE) + TestContainers
// OR create a test-only @SpringBootApplication in test/

// Option: Use @DataJpaTest approach (simpler, recommended)

// Test configuration class (in test sources)
@SpringBootApplication
@EnableJpaAuditing
class TestApplication {
    // Minimal Spring Boot app for data-provider tests
}

// OR use @DataJpaTest with explicit config:

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DataSourceConfiguration.class)
@Testcontainers
class OrderRepositoryImplIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("orders_db")
        .withUsername("mars")
        .withPassword("mars");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    private OrderRepositoryImpl orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new OrderRepositoryImpl(orderJpaRepository);
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should save order with items and find by id")
    void shouldSaveOrderAndFindById() {
        // Arrange
        var items = Set.of(
            new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")),
            new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))
        );
        var result = Order.create(UUID.randomUUID(), items);
        var order = result.domain();

        // Act
        orderRepository.save(order);
        var found = orderRepository.findById(order.id());

        // Assert
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
        // Arrange
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var result = Order.create(UUID.randomUUID(), items);
        var order = result.domain();
        orderRepository.save(order);

        // Act
        var cancelled = order.cancel();
        orderRepository.update(cancelled);

        // Assert
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

### Integration Points
```yaml
DATABASE:
  - migration: V1__create_orders_table.sql
  - tables: orders, order_items
  - indexes: idx_orders_customer_id, idx_order_items_order_id

CONFIGURATION:
  - DataSourceConfiguration.java enables @EnableJpaAuditing
  - application.yaml already configured for PostgreSQL (from PRP 01)
```

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile
# Expected: BUILD SUCCESS for all modules
```

### Level 2: Integration Tests (data-provider/)
```bash
mvn test -pl data-provider
# Expected: BUILD SUCCESS. Tests use TestContainers PostgreSQL.
# Docker must be running for TestContainers.
```

### Level 3: Full Build
```bash
mvn clean verify
# Expected: BUILD SUCCESS for all modules
```

---

## Final Checklist
- [ ] All integration tests pass: `mvn test -pl data-provider`
- [ ] TDD followed: tests written before production code
- [ ] Flyway migration creates orders + order_items tables correctly
- [ ] OrderEntity has of(Order) and toDomain() conversion methods
- [ ] OrderItemEntity has FK to OrderEntity
- [ ] OrderRepositoryImpl implements all 3 methods: save, findById, update
- [ ] JPA auditing works (@CreatedDate, @LastModifiedDate)
- [ ] Normalized tables used (NOT JSONB for items)
- [ ] TestContainers used for integration tests (PostgreSQL 16-alpine)

---

## Anti-Patterns to Avoid
- Don't use JSONB for items — Lite uses normalized order_items table
- Don't put business logic in JPA entities
- Don't use @Version in entities (reference has it, but Lite is simpler)
- Don't add Spring annotations in business/ module
- Don't skip TestContainers — unit tests can't verify JPA mappings
- Don't use H2 for tests — use real PostgreSQL via TestContainers
- Don't forget @EnableJpaAuditing in DataSourceConfiguration
- Don't create OrderEntity with setters — use static of() factory
- Don't map OrderStatus with @Ordinal — use @Enumerated(EnumType.STRING)

---

## Confidence Score: 8/10
- Context completeness: 9 (schema, entities, and tests fully specified)
- Pattern availability in codebase: 8 (reference provides entity patterns, but Lite normalizes differently)
- Validation gate coverage: 8 (integration tests cover CRUD, but edge cases may need additions)
- One-pass implementation likelihood: 8 (JPA cascade mapping can be tricky, but pseudocode is detailed)
