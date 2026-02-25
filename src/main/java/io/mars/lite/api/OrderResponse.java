package io.mars.lite.api;

import io.mars.lite.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
