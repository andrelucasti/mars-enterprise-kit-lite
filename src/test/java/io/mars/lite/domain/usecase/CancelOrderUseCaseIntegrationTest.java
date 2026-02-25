package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderStatus;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelOrderUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private CancelOrderUseCase cancelOrderUseCase;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @BeforeEach
    void cleanUp() {
        orderJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should cancel existing order")
    void shouldCancelExistingOrder() {
        var items = Set.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("50.00")));
        var orderId = createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, UUID.randomUUID()));

        cancelOrderUseCase.execute(orderId);

        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("should throw when cancelling non-existent order")
    void shouldThrowWhenCancellingNonExistentOrder() {
        assertThatThrownBy(() -> cancelOrderUseCase.execute(UUID.randomUUID()))
            .isInstanceOf(BusinessException.class);
    }
}
