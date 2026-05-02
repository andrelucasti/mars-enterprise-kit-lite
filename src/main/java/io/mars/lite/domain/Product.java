package io.mars.lite.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Product(
    UUID id,
    String name,
    BigDecimal unitPrice,
    ProductStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public Product {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(unitPrice);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static Product create(String name, BigDecimal unitPrice) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("name cannot be blank");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("unitPrice must be positive");
        }

        var now = Instant.now();
        return new Product(UUID.randomUUID(), name, unitPrice, ProductStatus.ACTIVE, now, now);
    }

    public Product updatePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("unitPrice must be positive");
        }
        return new Product(id, name, newPrice, status, createdAt, Instant.now());
    }

    public Product deactivate() {
        if (status == ProductStatus.INACTIVE) {
            throw new BusinessException("already inactive");
        }
        return new Product(id, name, unitPrice, ProductStatus.INACTIVE, createdAt, Instant.now());
    }

    public static Product reconstitute(UUID id, String name, BigDecimal unitPrice,
                                        ProductStatus status, Instant createdAt, Instant updatedAt) {
        return new Product(id, name, unitPrice, status, createdAt, updatedAt);
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }
}
