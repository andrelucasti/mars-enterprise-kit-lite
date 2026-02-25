package io.mars.lite.infrastructure.persistence;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderRepositoryImpl orderRepository;

    @BeforeEach
    void setUp() {
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should save order with items and find by id")
    void shouldSaveOrderAndFindById() {
        var items = Set.of(
            new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")),
            new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))
        );
        var result = Order.create(UUID.randomUUID(), items);
        var order = result.domain();

        orderRepository.save(order);
        var found = orderRepository.findById(order.id());

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(order.id());
        assertThat(found.get().customerId()).isEqualTo(order.customerId());
        assertThat(found.get().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(found.get().total()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(found.get().items()).hasSize(2);
    }

    @Test
    @DisplayName("should return empty when order not found")
    void shouldReturnEmptyWhenOrderNotFound() {
        var found = orderRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should update order status")
    void shouldUpdateOrderStatus() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var result = Order.create(UUID.randomUUID(), items);
        var order = result.domain();
        orderRepository.save(order);

        var cancelled = order.cancel();
        orderRepository.update(cancelled);

        var found = orderRepository.findById(order.id());
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should persist audit timestamps")
    void shouldPersistAuditTimestamps() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00")));
        var result = Order.create(UUID.randomUUID(), items);
        orderRepository.save(result.domain());

        var entity = orderJpaRepository.findById(result.domain().id()).orElseThrow();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }
}
