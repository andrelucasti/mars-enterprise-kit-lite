package io.mars.lite.infrastructure.persistence;

import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
public class OrderEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<OrderItemEntity> items = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private transient boolean isNew = true;

    protected OrderEntity() {}

    public static OrderEntity of(final Order order) {
        var entity = new OrderEntity();
        entity.id = order.id();
        entity.customerId = order.customerId();
        entity.status = order.status();
        entity.total = order.total();
        entity.createdAt = order.createdAt();
        entity.updatedAt = order.updatedAt();

        order.items().forEach(item -> {
            var itemEntity = new OrderItemEntity();
            itemEntity.setId(UUID.randomUUID());
            itemEntity.setProductId(item.productId());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setOrder(entity);
            entity.items.add(itemEntity);
        });

        return entity;
    }

    public Order toDomain() {
        var domainItems = items.stream()
            .map(e -> new OrderItem(e.getProductId(), e.getQuantity(), e.getUnitPrice()))
            .collect(Collectors.toSet());

        return Order.reconstitute(id, customerId, status, domainItems, total,
                                  createdAt, updatedAt);
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }

    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public Set<OrderItemEntity> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
