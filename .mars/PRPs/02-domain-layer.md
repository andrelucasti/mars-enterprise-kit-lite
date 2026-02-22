# PRP 02 — Domain Layer (business/ module)

## Purpose
Implement the complete Order domain model with use cases, value objects, ports, and domain events — all in pure Java with zero framework dependencies.

### Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests the AI can run and fix
3. **TDD Mandatory**: Every feature follows Red-Green-Refactor
4. **Onion Architecture**: Dependencies always point inward (app -> data-provider -> business)
5. **Global Rules**: Follow all rules in CLAUDE.md

---

## Goal
A complete domain layer in `business/` with the Order aggregate root, two use cases (CreateOrder, CancelOrder), value objects, repository port, event publisher port, and domain events — all tested with unit tests.

## Why
- The domain layer is the heart of Onion Architecture — everything depends on it
- Pure Java ensures no framework coupling (the business logic survives framework migrations)
- TDD from this layer outward ensures correctness at every level
- The two use cases (Create and Cancel) represent the complete Lite feature set

## What
The domain layer implements:
1. **Order aggregate** — creates orders with items, supports cancellation
2. **CreateOrderUseCase** — persists order + publishes event (via ports)
3. **CancelOrderUseCase** — finds order by ID + cancels it
4. **Ports** — OrderRepository and OrderEventPublisher interfaces
5. **Domain events** — OrderCreatedEvent record
6. **Value objects** — OrderItem, OrderStatus, DomainResult

### Success Criteria
- [ ] All unit tests pass: `mvn test -pl business`
- [ ] Order.create() returns DomainResult with CREATED status and OrderCreatedEvent
- [ ] Order.cancel() transitions from CREATED to CANCELLED
- [ ] Order.cancel() throws on already-cancelled order
- [ ] CreateOrderUseCase saves order and publishes event
- [ ] CancelOrderUseCase finds and cancels order
- [ ] ZERO Spring/JPA imports in any business/ file

---

## All Needed Context

### Documentation & References
```yaml
- file: CLAUDE.md
  why: Project conventions, code style, TDD rules, naming conventions

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Domain model, use cases, event payloads, Kafka topics

- file: .mars/PRPs/01-project-bootstrap.md
  why: Previous PRP — project structure must exist before this PRP
```

### Reference Implementation Patterns
The following patterns serve as reference. Adapt them for Lite:

**Aggregate Root Pattern (Record with factory + state transitions):**
```java
// Reference: com.mars.order.Order
// Adapted for Lite: io.mars.lite.order.Order
// KEY DIFFERENCES:
//   - Lite has only CREATED and CANCELLED statuses (no PENDING, INVENTORY_RESERVED, etc.)
//   - Lite uses Order.create() -> DomainResult<Order, OrderCreatedEvent>
//   - Lite uses order.cancel() -> Order (no event returned, cancellation is terminal)
//   - Lite uses BigDecimal for total calculation (reference doesn't calculate totals)
```

**Sealed Interface Use Case Pattern:**
```java
// Reference: com.mars.order.usecase.CreateOrderUseCase
public sealed interface CreateOrderUseCase permits CreateOrderUseCaseImpl {
    static CreateOrderUseCase create(OrderRepository repo, OrderEventPublisher publisher) {
        return new CreateOrderUseCaseImpl(repo, publisher);
    }
    UUID execute(Input input);
    record Input(Set<Item> items, UUID customerId) {}
}
```

**DomainResult Pattern:**
```java
// Reference: com.mars.order.DomainResult
public record DomainResult<D, E>(D domain, E event) {}
```

### Current Codebase Structure
```
mars-enterprise-kit-lite/
├── pom.xml                          # Parent POM (from PRP 01)
├── business/
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/mars/lite/order/  (empty)
│       └── test/java/io/mars/lite/order/  (empty)
├── data-provider/  (exists from PRP 01)
└── app/            (exists from PRP 01)
```

### Desired Codebase Changes
```
business/src/main/java/io/mars/lite/order/
├── Order.java                          # NEW: Aggregate Root (record)
├── OrderStatus.java                    # NEW: Value Object (enum)
├── OrderItem.java                      # NEW: Value Object (record)
├── OrderRepository.java               # NEW: Port (interface)
├── OrderEventPublisher.java           # NEW: Port (interface)
├── BusinessException.java             # NEW: Domain Exception
├── DomainResult.java                  # NEW: Generic wrapper
├── OrderCreatedEvent.java             # NEW: Domain Event (record)
└── usecase/
    ├── CreateOrderUseCase.java        # NEW: Sealed interface
    ├── CreateOrderUseCaseImpl.java    # NEW: Record implementation
    ├── CancelOrderUseCase.java        # NEW: Sealed interface
    └── CancelOrderUseCaseImpl.java    # NEW: Record implementation

business/src/test/java/io/mars/lite/order/
├── OrderTest.java                      # NEW: Aggregate unit tests
├── OrderItemTest.java                  # NEW: Value object tests
└── usecase/
    ├── CreateOrderUseCaseTest.java    # NEW: Use case unit test (mocked ports)
    └── CancelOrderUseCaseTest.java    # NEW: Use case unit test (mocked ports)
```

### Known Gotchas
```java
// CRITICAL: business/ module must have ZERO Spring/JPA dependencies
//   - No @Repository, @Service, @Transactional, @Entity
//   - No jakarta.persistence.*, org.springframework.*, org.apache.kafka.*
//   - Only standard Java library (java.util.*, java.math.*, java.time.*)
//
// CRITICAL: Use Java records for all immutable objects
//   - Order is a record (not a class with getters/setters)
//   - OrderItem, OrderCreatedEvent, DomainResult are all records
//   - Use compact constructors for validation
//
// CRITICAL: OrderStatus has only 2 values: CREATED, CANCELLED
//   - No PENDING (reference has PENDING as initial, Lite uses CREATED)
//   - No INVENTORY_RESERVED, REJECTED, COMPLETED (those are SAGA states, out of scope)
//
// CRITICAL: DomainResult wraps domain object + event
//   - Order.create() returns DomainResult<Order, OrderCreatedEvent>
//   - Order.cancel() returns just Order (no event — cancellation is terminal in Lite)
//
// CRITICAL: OrderEventPublisher is a PORT in business/
//   - It's an interface here, implemented in app/ with Kafka (PRP 04)
//   - This follows the Onion Architecture rule: business defines contracts
//
// CRITICAL: Order must calculate total from items
//   - total = SUM(item.unitPrice * item.quantity) for each item
//   - Stored as BigDecimal (matches orders.total NUMERIC(10,2) in database)
//
// Package: io.mars.lite.order (NOT com.mars.order)
```

---

## Implementation Blueprint

### Data Models

```java
// === DOMAIN OBJECTS (business/ module) ===

// OrderStatus.java — Value Object (enum)
public enum OrderStatus {
    CREATED,
    CANCELLED
}

// OrderItem.java — Value Object (record)
// Represents a line item in an order
public record OrderItem(
    UUID productId,
    int quantity,
    BigDecimal unitPrice
) {
    public OrderItem {
        Objects.requireNonNull(productId, "productId cannot be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("unitPrice must be positive");
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

// Order.java — Aggregate Root (record)
public record Order(
    UUID id,
    UUID customerId,
    OrderStatus status,
    Set<OrderItem> items,
    BigDecimal total,
    Instant createdAt,
    Instant updatedAt
) {
    // Compact constructor validates invariants
    public Order {
        Objects.requireNonNull(id);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(items);
        Objects.requireNonNull(total);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    // Factory method — creates new order with CREATED status
    public static DomainResult<Order, OrderCreatedEvent> create(
            UUID customerId, Set<OrderItem> items) {
        if (customerId == null) throw new BusinessException("customerId cannot be null");
        if (items == null || items.isEmpty()) throw new BusinessException("items cannot be empty");

        var total = items.stream()
            .map(OrderItem::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var now = Instant.now();
        var order = new Order(UUID.randomUUID(), customerId, OrderStatus.CREATED,
                              Set.copyOf(items), total, now, now);
        var event = OrderCreatedEvent.from(order);
        return new DomainResult<>(order, event);
    }

    // State transition — cancel order
    public Order cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessException("Order is already cancelled");
        }
        return new Order(id, customerId, OrderStatus.CANCELLED, items, total,
                         createdAt, Instant.now());
    }

    // Reconstitution — used by data-provider to rebuild from DB
    // (no event, no validation — trusted data from persistence)
    public static Order reconstitute(UUID id, UUID customerId, OrderStatus status,
                                     Set<OrderItem> items, BigDecimal total,
                                     Instant createdAt, Instant updatedAt) {
        return new Order(id, customerId, status, items, total, createdAt, updatedAt);
    }
}

// DomainResult.java — Generic wrapper for domain + event
public record DomainResult<D, E>(D domain, E event) {}

// BusinessException.java — Domain exception
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}

// OrderCreatedEvent.java — Domain event
public record OrderCreatedEvent(
    UUID eventId,
    UUID orderId,
    UUID customerId,
    BigDecimal totalAmount,
    Set<OrderCreatedEvent.Item> items,
    Instant occurredAt
) {
    public static OrderCreatedEvent from(Order order) {
        var eventItems = order.items().stream()
            .map(i -> new Item(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());
        return new OrderCreatedEvent(
            UUID.randomUUID(), order.id(), order.customerId(),
            order.total(), eventItems, Instant.now());
    }

    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {}
}

// OrderRepository.java — Port (interface)
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(UUID orderId);
    void update(Order order);
}

// OrderEventPublisher.java — Port (interface)
public interface OrderEventPublisher {
    void publish(OrderCreatedEvent event);
}
```

### Tasks (in execution order)

```yaml
Task 1: Value Objects + Domain Exception
  module: business/
  files:
    - OrderStatus.java
    - OrderItem.java
    - BusinessException.java
    - DomainResult.java
  tests:
    - OrderItemTest.java (written FIRST)
  TDD: Red -> Green -> Refactor

Task 2: Order Aggregate Root
  module: business/
  files:
    - Order.java
    - OrderCreatedEvent.java
  tests:
    - OrderTest.java (written FIRST)
  TDD: Red -> Green -> Refactor

Task 3: Ports (Interfaces)
  module: business/
  files:
    - OrderRepository.java
    - OrderEventPublisher.java
  tests: none (interfaces have no logic)

Task 4: CreateOrderUseCase
  module: business/
  files:
    - usecase/CreateOrderUseCase.java
    - usecase/CreateOrderUseCaseImpl.java
  tests:
    - usecase/CreateOrderUseCaseTest.java (written FIRST, mock ports)
  TDD: Red -> Green -> Refactor

Task 5: CancelOrderUseCase
  module: business/
  files:
    - usecase/CancelOrderUseCase.java
    - usecase/CancelOrderUseCaseImpl.java
  tests:
    - usecase/CancelOrderUseCaseTest.java (written FIRST, mock ports)
  TDD: Red -> Green -> Refactor
```

### Per-Task Pseudocode

```java
// Task 1 — Value Objects
// OrderItem: record with productId (UUID), quantity (int), unitPrice (BigDecimal)
//   - Compact constructor validates: non-null, positive quantity, positive price
//   - subtotal() method returns unitPrice * quantity
// Test: shouldCalculateSubtotalCorrectly, shouldRejectNegativeQuantity, etc.

// Task 2 — Order Aggregate
// Order.create(customerId, items):
//   1. Validate customerId not null, items not empty
//   2. Calculate total from items
//   3. Create Order record with CREATED status, generated UUID, now() timestamps
//   4. Create OrderCreatedEvent from the order
//   5. Return DomainResult<Order, OrderCreatedEvent>
//
// order.cancel():
//   1. If already CANCELLED → throw BusinessException
//   2. Return new Order with CANCELLED status and updated timestamp
//
// Test: shouldCreateOrderWithCreatedStatus, shouldCalculateTotal,
//       shouldCancelOrder, shouldThrowWhenCancellingAlreadyCancelled,
//       shouldThrowWhenItemsEmpty, shouldReturnEvent

// Task 4 — CreateOrderUseCase
// Sealed interface with factory method
// Impl is a record with (OrderRepository, OrderEventPublisher)
// execute(Input):
//   1. Call Order.create(input.customerId(), input.items())
//   2. Get order and event from DomainResult
//   3. orderRepository.save(order)
//   4. orderEventPublisher.publish(event)
//   5. Return order.id()
// Test: Mock OrderRepository and OrderEventPublisher
//       Verify save called, verify publish called, verify returns orderId

// Task 5 — CancelOrderUseCase
// Sealed interface with factory method
// Impl is a record with (OrderRepository)
// execute(UUID orderId):
//   1. orderRepository.findById(orderId) → throw if not found
//   2. order.cancel() → get cancelled order
//   3. orderRepository.update(cancelledOrder)
// Test: Mock OrderRepository
//       Verify findById called, verify update called with CANCELLED status
//       Verify throws when order not found
```

### Test Patterns

```java
// === UNIT TEST EXAMPLES (follow these patterns exactly) ===

// OrderItemTest.java
class OrderItemTest {

    @Test
    void shouldCalculateSubtotalCorrectly() {
        var item = new OrderItem(UUID.randomUUID(), 3, new BigDecimal("10.00"));
        assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), 0, new BigDecimal("10.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity must be positive");
    }

    @Test
    void shouldRejectNullProductId() {
        assertThatThrownBy(() -> new OrderItem(null, 1, new BigDecimal("10.00")))
            .isInstanceOf(NullPointerException.class);
    }
}

// OrderTest.java
class OrderTest {

    @Test
    void shouldCreateOrderWithCreatedStatus() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")));
        var result = Order.create(UUID.randomUUID(), items);

        assertThat(result.domain().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.domain().id()).isNotNull();
        assertThat(result.event()).isNotNull();
        assertThat(result.event().orderId()).isEqualTo(result.domain().id());
    }

    @Test
    void shouldCalculateTotalFromItems() {
        var items = Set.of(
            new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")),
            new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))
        );
        var result = Order.create(UUID.randomUUID(), items);

        assertThat(result.domain().total()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    void shouldThrowWhenItemsAreEmpty() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), Set.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("items cannot be empty");
    }

    @Test
    void shouldCancelOrder() {
        var result = Order.create(UUID.randomUUID(),
            Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
        var cancelled = result.domain().cancel();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldThrowWhenCancellingAlreadyCancelledOrder() {
        var result = Order.create(UUID.randomUUID(),
            Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
        var cancelled = result.domain().cancel();

        assertThatThrownBy(cancelled::cancel)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already cancelled");
    }
}

// CreateOrderUseCaseTest.java (with mocks)
class CreateOrderUseCaseTest {

    private OrderRepository orderRepository;
    private OrderEventPublisher eventPublisher;
    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        eventPublisher = mock(OrderEventPublisher.class);
        useCase = CreateOrderUseCase.create(orderRepository, eventPublisher);
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

// CancelOrderUseCaseTest.java (with mocks)
class CancelOrderUseCaseTest {

    private OrderRepository orderRepository;
    private CancelOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        useCase = CancelOrderUseCase.create(orderRepository);
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

---

## Validation Loop

### Level 1: Compilation
```bash
mvn clean compile -pl business
# Expected: BUILD SUCCESS. No Spring/JPA imports in any file.
```

### Level 2: Unit Tests (business/)
```bash
mvn test -pl business
# Expected: BUILD SUCCESS. All tests pass.
# Tests run: OrderItemTest, OrderTest, CreateOrderUseCaseTest, CancelOrderUseCaseTest
```

### Level 3: Full Build
```bash
mvn clean verify
# Expected: BUILD SUCCESS for all modules.
```

---

## Final Checklist
- [ ] All tests pass: `mvn test -pl business`
- [ ] TDD followed: tests written before production code
- [ ] Java records used for Order, OrderItem, OrderCreatedEvent, DomainResult
- [ ] Order.create() returns DomainResult with OrderCreatedEvent
- [ ] Order.cancel() transitions CREATED -> CANCELLED
- [ ] BusinessException used for domain rule violations
- [ ] OrderRepository and OrderEventPublisher are interfaces (ports)
- [ ] Sealed interface pattern used for use cases
- [ ] ZERO imports from Spring, JPA, Kafka in business/ module
- [ ] Package is `io.mars.lite.order` throughout

---

## Anti-Patterns to Avoid
- Don't add Spring/JPA annotations in business/ module
- Don't write production code before a failing test
- Don't use setters — Order is a record (immutable by definition)
- Don't add PENDING, INVENTORY_RESERVED, or other SAGA states (out of scope)
- Don't implement Event Sourcing — Lite uses simple CRUD
- Don't use `com.mars.order` package — use `io.mars.lite.order`
- Don't add @Transactional to use cases — that's the service layer's job (app/ module)
- Don't create Money value object — use BigDecimal directly (keep it simple for Lite)

---

## Confidence Score: 9/10
- Context completeness: 10 (all domain objects, tests, and patterns fully specified)
- Pattern availability in codebase: 9 (reference implementation provides exact patterns)
- Validation gate coverage: 9 (unit tests cover all domain logic)
- One-pass implementation likelihood: 9 (pure Java, no framework complexity)
