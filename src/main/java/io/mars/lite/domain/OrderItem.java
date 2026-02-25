package io.mars.lite.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

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
