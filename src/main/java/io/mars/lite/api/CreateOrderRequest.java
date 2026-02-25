package io.mars.lite.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
