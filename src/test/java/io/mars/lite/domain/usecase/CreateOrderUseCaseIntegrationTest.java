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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateOrderUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void cleanUp() {
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @Nested
    @DisplayName("Happy path integration tests")
    class HappyPathIntegrationTests {

        @Test
        @DisplayName("should create order and persist in database with catalog price")
        void shouldCreateOrderAndPersistInDatabaseWithCatalogPrice() {
            // Arrange: Create an active product in the database
            var catalogPrice = new BigDecimal("10.00");
            var product = Product.create("Test Product", catalogPrice);
            productJpaRepository.save(ProductEntity.of(product));

            var customerId = UUID.randomUUID();
            var items = Set.of(new CreateOrderUseCase.OrderItemInput(product.id(), 2));

            // Act
            var orderId = createOrderUseCase.execute(
                new CreateOrderUseCase.Input(items, customerId));

            // Assert
            assertThat(orderId).isNotNull();
            var entity = orderJpaRepository.findById(orderId).orElseThrow();
            assertThat(entity.getCustomerId()).isEqualTo(customerId);
            assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
            // Total should be 2 * 10.00 = 20.00 (using catalog price)
            assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("should create order with multiple products using catalog prices")
        void shouldCreateOrderWithMultipleProductsUsingCatalogPrices() {
            // Arrange: Create multiple active products
            var product1 = Product.create("Product 1", new BigDecimal("15.00"));
            var product2 = Product.create("Product 2", new BigDecimal("25.00"));
            productJpaRepository.save(ProductEntity.of(product1));
            productJpaRepository.save(ProductEntity.of(product2));

            var customerId = UUID.randomUUID();
            var items = Set.of(
                new CreateOrderUseCase.OrderItemInput(product1.id(), 1),
                new CreateOrderUseCase.OrderItemInput(product2.id(), 2)
            );

            // Act
            var orderId = createOrderUseCase.execute(
                new CreateOrderUseCase.Input(items, customerId));

            // Assert
            assertThat(orderId).isNotNull();
            var entity = orderJpaRepository.findById(orderId).orElseThrow();
            // Total should be 1 * 15.00 + 2 * 25.00 = 65.00
            assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("65.00"));
        }
    }

    @Nested
    @DisplayName("Product validation integration tests")
    class ProductValidationIntegrationTests {

        @Test
        @DisplayName("should throw when product does not exist")
        void shouldThrowWhenProductDoesNotExist() {
            var nonExistentProductId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var items = Set.of(new CreateOrderUseCase.OrderItemInput(nonExistentProductId, 1));

            assertThatThrownBy(() -> createOrderUseCase.execute(
                new CreateOrderUseCase.Input(items, customerId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product not found: " + nonExistentProductId);

            // Verify no order was created
            assertThat(orderJpaRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should throw when product is inactive")
        void shouldThrowWhenProductIsInactive() {
            // Arrange: Create an inactive product
            var activeProduct = Product.create("Active Product", new BigDecimal("10.00"));
            var inactiveProduct = activeProduct.deactivate();
            productJpaRepository.save(ProductEntity.of(inactiveProduct));

            var customerId = UUID.randomUUID();
            var items = Set.of(new CreateOrderUseCase.OrderItemInput(inactiveProduct.id(), 1));

            // Act & Assert
            assertThatThrownBy(() -> createOrderUseCase.execute(
                new CreateOrderUseCase.Input(items, customerId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product is inactive: " + inactiveProduct.id());

            // Verify no order was created
            assertThat(orderJpaRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should aggregate errors when multiple products have issues")
        void shouldAggregateErrorsWhenMultipleProductsHaveIssues() {
            // Arrange: Create one inactive product, another product doesn't exist
            var activeProduct = Product.create("Inactive Product", new BigDecimal("10.00"));
            var inactiveProduct = activeProduct.deactivate();
            productJpaRepository.save(ProductEntity.of(inactiveProduct));

            var nonExistentProductId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var items = Set.of(
                new CreateOrderUseCase.OrderItemInput(inactiveProduct.id(), 1),
                new CreateOrderUseCase.OrderItemInput(nonExistentProductId, 2)
            );

            // Act & Assert
            assertThatThrownBy(() -> createOrderUseCase.execute(
                new CreateOrderUseCase.Input(items, customerId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var message = ex.getMessage();
                    assertThat(message).contains("Product is inactive: " + inactiveProduct.id());
                    assertThat(message).contains("Product not found: " + nonExistentProductId);
                });

            // Verify no order was created
            assertThat(orderJpaRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should create order when mixed with valid active products")
        void shouldCreateOrderWhenAllProductsAreValid() {
            // Arrange: Create two active products
            var product1 = Product.create("Product 1", new BigDecimal("100.00"));
            var product2 = Product.create("Product 2", new BigDecimal("50.00"));
            productJpaRepository.save(ProductEntity.of(product1));
            productJpaRepository.save(ProductEntity.of(product2));

            var customerId = UUID.randomUUID();
            var items = Set.of(
                new CreateOrderUseCase.OrderItemInput(product1.id(), 1),
                new CreateOrderUseCase.OrderItemInput(product2.id(), 3)
            );

            // Act
            var orderId = createOrderUseCase.execute(
                new CreateOrderUseCase.Input(items, customerId));

            // Assert
            assertThat(orderId).isNotNull();
            var entity = orderJpaRepository.findById(orderId).orElseThrow();
            assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
            // Total should be 1 * 100.00 + 3 * 50.00 = 250.00
            assertThat(entity.getTotal()).isEqualByComparingTo(new BigDecimal("250.00"));
        }
    }

    @Nested
    @DisplayName("Input validation integration tests")
    class InputValidationIntegrationTests {

        @Test
        @DisplayName("should throw when items is empty")
        void shouldThrowWhenItemsEmpty() {
            var customerId = UUID.randomUUID();
            var input = new CreateOrderUseCase.Input(Set.of(), customerId);

            assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("items cannot be empty");

            assertThat(orderJpaRepository.findAll()).isEmpty();
        }
    }
}
