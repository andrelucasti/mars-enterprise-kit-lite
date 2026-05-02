package io.mars.lite.api;

import java.math.BigDecimal;
import java.util.Objects;

public record CreateProductRequest(
    String name,
    BigDecimal unitPrice
) {
    public CreateProductRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
    }
}
