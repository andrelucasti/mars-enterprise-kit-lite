package io.mars.lite.infrastructure.persistence;

import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class ProductEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private transient boolean isNew = true;

    protected ProductEntity() {}

    public static ProductEntity of(final Product product) {
        var entity = new ProductEntity();
        entity.id = product.id();
        entity.name = product.name();
        entity.unitPrice = product.unitPrice();
        entity.status = product.status();
        entity.createdAt = product.createdAt();
        entity.updatedAt = product.updatedAt();
        return entity;
    }

    public Product toDomain() {
        return Product.reconstitute(id, name, unitPrice, status, createdAt, updatedAt);
    }

    public void updateFrom(Product product) {
        this.name = product.name();
        this.unitPrice = product.unitPrice();
        this.status = product.status();
        // updatedAt is handled by @LastModifiedDate
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }

    // Getters for JPA and testing
    public String getName() { return name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public ProductStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
