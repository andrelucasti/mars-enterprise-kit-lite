package io.mars.lite.api;

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

    /**
     * Order item request without price. Price comes from product catalog.
     */
    public record ItemRequest(
        UUID productId,
        int quantity
    ) {
        public ItemRequest {
            Objects.requireNonNull(productId, "productId cannot be null");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
