package io.mars.lite.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Order(
    UUID id,
    UUID customerId,
    OrderStatus status,
    Set<OrderItem> items,
    BigDecimal total,
    Instant createdAt,
    Instant updatedAt
) {
    public Order {
        Objects.requireNonNull(id);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(items);
        Objects.requireNonNull(total);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

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

    public Order cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessException("Order is already cancelled");
        }
        return new Order(id, customerId, OrderStatus.CANCELLED, items, total,
                         createdAt, Instant.now());
    }

    public static Order reconstitute(UUID id, UUID customerId, OrderStatus status,
                                     Set<OrderItem> items, BigDecimal total,
                                     Instant createdAt, Instant updatedAt) {
        return new Order(id, customerId, status, items, total, createdAt, updatedAt);
    }
}
