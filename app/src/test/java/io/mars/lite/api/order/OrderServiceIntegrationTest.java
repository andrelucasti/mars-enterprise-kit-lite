package io.mars.lite.api.order;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.order.BusinessException;
import io.mars.lite.order.OrderItem;
import io.mars.lite.order.OrderJpaRepository;
import io.mars.lite.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void cleanUp() {
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should create order and persist in database")
    void shouldCreateOrderAndPersistInDatabase() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")));
        var customerId = UUID.randomUUID();

        var orderId = orderService.createOrder(items, customerId);

        assertThat(orderId).isNotNull();
        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("should cancel existing order")
    void shouldCancelExistingOrder() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var orderId = orderService.createOrder(items, UUID.randomUUID());

        orderService.cancelOrder(orderId);

        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should find order by id")
    void shouldFindOrderById() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("99.99")));
        var orderId = orderService.createOrder(items, UUID.randomUUID());

        var found = orderService.findById(orderId);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("should throw when cancelling non-existent order")
    void shouldThrowWhenCancellingNonExistentOrder() {
        assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID()))
            .isInstanceOf(BusinessException.class);
    }
}
