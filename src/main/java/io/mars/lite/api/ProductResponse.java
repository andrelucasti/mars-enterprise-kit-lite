package io.mars.lite.api;

import io.mars.lite.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    BigDecimal unitPrice,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.id(),
            product.name(),
            product.unitPrice(),
            product.status().name(),
            product.createdAt(),
            product.updatedAt()
        );
    }
}
