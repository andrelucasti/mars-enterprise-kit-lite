package io.mars.lite.api;

import java.math.BigDecimal;
import java.util.Objects;

public record UpdateProductPriceRequest(BigDecimal unitPrice) {
    public UpdateProductPriceRequest {
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
    }
}
