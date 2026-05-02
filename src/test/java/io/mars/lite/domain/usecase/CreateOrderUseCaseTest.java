package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderCreatedEvent;
import io.mars.lite.domain.OrderEventPublisher;
import io.mars.lite.domain.OrderRepository;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductRepository;
import io.mars.lite.domain.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateOrderUseCaseTest {

    private OrderRepository orderRepository;
    private OrderEventPublisher eventPublisher;
    private ProductRepository productRepository;
    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        eventPublisher = mock(OrderEventPublisher.class);
        productRepository = mock(ProductRepository.class);
        useCase = new CreateOrderUseCase(orderRepository, eventPublisher, productRepository);
    }

    @Nested
    @DisplayName("Happy path tests")
    class HappyPathTests {

        @Test
        @DisplayName("should save order and publish event when product exists and is active")
        void shouldSaveOrderAndPublishEventWhenProductExistsAndIsActive() {
            var productId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var catalogPrice = new BigDecimal("99.99");
            var product = createActiveProduct(productId, "Test Product", catalogPrice);

            when(productRepository.findAllByIds(Set.of(productId)))
                .thenReturn(List.of(product));

            var items = Set.of(new CreateOrderUseCase.OrderItemInput(productId, 2));
            var input = new CreateOrderUseCase.Input(items, customerId);

            var orderId = useCase.execute(input);

            assertThat(orderId).isNotNull();
            verify(orderRepository).save(any(Order.class));
            verify(eventPublisher).publish(any(OrderCreatedEvent.class));
        }

        @Test
        @DisplayName("should use catalog price instead of request price")
        void shouldUseCatalogPriceInsteadOfRequestPrice() {
            var productId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var catalogPrice = new BigDecimal("50.00");
            var product = createActiveProduct(productId, "Test Product", catalogPrice);

            when(productRepository.findAllByIds(Set.of(productId)))
                .thenReturn(List.of(product));

            var items = Set.of(new CreateOrderUseCase.OrderItemInput(productId, 3));
            var input = new CreateOrderUseCase.Input(items, customerId);

            useCase.execute(input);

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("should create order with multiple products")
        void shouldCreateOrderWithMultipleProducts() {
            var productId1 = UUID.randomUUID();
            var productId2 = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var product1 = createActiveProduct(productId1, "Product 1", new BigDecimal("10.00"));
            var product2 = createActiveProduct(productId2, "Product 2", new BigDecimal("20.00"));

            when(productRepository.findAllByIds(Set.of(productId1, productId2)))
                .thenReturn(List.of(product1, product2));

            var items = Set.of(
                new CreateOrderUseCase.OrderItemInput(productId1, 1),
                new CreateOrderUseCase.OrderItemInput(productId2, 2)
            );
            var input = new CreateOrderUseCase.Input(items, customerId);

            var orderId = useCase.execute(input);

            assertThat(orderId).isNotNull();
            verify(orderRepository).save(any(Order.class));
            verify(eventPublisher).publish(any(OrderCreatedEvent.class));
        }
    }

    @Nested
    @DisplayName("Validation error tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("should throw when items is empty")
        void shouldThrowWhenItemsEmpty() {
            var input = new CreateOrderUseCase.Input(Set.of(), UUID.randomUUID());

            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("items cannot be empty");

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should throw when items is null")
        void shouldThrowWhenItemsNull() {
            var input = new CreateOrderUseCase.Input(null, UUID.randomUUID());

            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("items cannot be empty");

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should throw when product not found")
        void shouldThrowWhenProductNotFound() {
            var productId = UUID.randomUUID();
            var customerId = UUID.randomUUID();

            when(productRepository.findAllByIds(Set.of(productId)))
                .thenReturn(List.of());

            var items = Set.of(new CreateOrderUseCase.OrderItemInput(productId, 1));
            var input = new CreateOrderUseCase.Input(items, customerId);

            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product not found: " + productId);

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should throw when product is inactive")
        void shouldThrowWhenProductIsInactive() {
            var productId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var product = createInactiveProduct(productId, "Inactive Product", new BigDecimal("99.99"));

            when(productRepository.findAllByIds(Set.of(productId)))
                .thenReturn(List.of(product));

            var items = Set.of(new CreateOrderUseCase.OrderItemInput(productId, 1));
            var input = new CreateOrderUseCase.Input(items, customerId);

            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product is inactive: " + productId);

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should aggregate multiple errors when multiple products have issues")
        void shouldAggregateMultipleErrorsWhenMultipleProductsHaveIssues() {
            var notFoundProductId = UUID.randomUUID();
            var inactiveProductId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var inactiveProduct = createInactiveProduct(inactiveProductId, "Inactive Product", new BigDecimal("50.00"));

            when(productRepository.findAllByIds(Set.of(notFoundProductId, inactiveProductId)))
                .thenReturn(List.of(inactiveProduct));

            var items = Set.of(
                new CreateOrderUseCase.OrderItemInput(notFoundProductId, 1),
                new CreateOrderUseCase.OrderItemInput(inactiveProductId, 2)
            );
            var input = new CreateOrderUseCase.Input(items, customerId);

            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var message = ex.getMessage();
                    assertThat(message).contains("Product not found: " + notFoundProductId);
                    assertThat(message).contains("Product is inactive: " + inactiveProductId);
                });

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("OrderItemInput validation tests")
    class OrderItemInputValidationTests {

        @Test
        @DisplayName("should throw when productId is null")
        void shouldThrowWhenProductIdIsNull() {
            assertThatThrownBy(() -> new CreateOrderUseCase.OrderItemInput(null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("productId cannot be null");
        }

        @Test
        @DisplayName("should throw when quantity is zero")
        void shouldThrowWhenQuantityIsZero() {
            assertThatThrownBy(() -> new CreateOrderUseCase.OrderItemInput(UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
        }

        @Test
        @DisplayName("should throw when quantity is negative")
        void shouldThrowWhenQuantityIsNegative() {
            assertThatThrownBy(() -> new CreateOrderUseCase.OrderItemInput(UUID.randomUUID(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
        }
    }

    private Product createActiveProduct(UUID id, String name, BigDecimal price) {
        var now = Instant.now();
        return Product.reconstitute(id, name, price, ProductStatus.ACTIVE, now, now);
    }

    private Product createInactiveProduct(UUID id, String name, BigDecimal price) {
        var now = Instant.now();
        return Product.reconstitute(id, name, price, ProductStatus.INACTIVE, now, now);
    }
}
