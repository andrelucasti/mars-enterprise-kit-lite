package io.mars.lite.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
