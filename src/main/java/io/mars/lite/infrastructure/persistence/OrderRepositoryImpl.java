package io.mars.lite.infrastructure.persistence;

import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Order order) {
        jpaRepository.save(OrderEntity.of(order));
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(OrderEntity::toDomain);
    }

    @Override
    public void update(Order order) {
        var entity = jpaRepository.findById(order.id())
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + order.id()));
        entity.updateStatus(order.status());
        jpaRepository.save(entity);
    }
}
