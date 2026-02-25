package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
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

class CreateOrderUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

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

        var orderId = createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, customerId));

        assertThat(orderId).isNotNull();
        var entity = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    }
}
