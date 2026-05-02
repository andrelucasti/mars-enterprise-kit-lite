package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.OrderStatus;
import io.mars.lite.domain.Product;
import io.mars.lite.infrastructure.persistence.OrderJpaRepository;
import io.mars.lite.infrastructure.persistence.ProductEntity;
import io.mars.lite.infrastructure.persistence.ProductJpaRepository;
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

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void cleanUp() {
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should cancel existing order")
    void shouldCancelExistingOrder() {
        // Arrange: Create a product first
        var product = Product.create("Test Product", new BigDecimal("50.00"));
        productJpaRepository.save(ProductEntity.of(product));

        var items = Set.of(new CreateOrderUseCase.OrderItemInput(product.id(), 1));
        var orderId = createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, UUID.randomUUID()));

        // Act
        cancelOrderUseCase.execute(orderId);

        // Assert
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
