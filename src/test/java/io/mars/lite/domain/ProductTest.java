package io.mars.lite.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void shouldCreateProductWithActiveStatus() {
        var product = Product.create("Mars Rover Kit", new BigDecimal("299.99"));

        assertThat(product.id()).isNotNull();
        assertThat(product.name()).isEqualTo("Mars Rover Kit");
        assertThat(product.unitPrice()).isEqualByComparingTo(new BigDecimal("299.99"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.createdAt()).isNotNull();
        assertThat(product.updatedAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        assertThatThrownBy(() -> Product.create("", new BigDecimal("10.00")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("name cannot be blank");
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        assertThatThrownBy(() -> Product.create(null, new BigDecimal("10.00")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("name cannot be blank");
    }

    @Test
    void shouldThrowWhenPriceIsNegative() {
        assertThatThrownBy(() -> Product.create("Test", new BigDecimal("-5.00")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");
    }

    @Test
    void shouldThrowWhenPriceIsZero() {
        assertThatThrownBy(() -> Product.create("Test", BigDecimal.ZERO))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");
    }

    @Test
    void shouldThrowWhenPriceIsNull() {
        assertThatThrownBy(() -> Product.create("Test", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");
    }

    @Test
    void shouldUpdatePrice() {
        var product = Product.create("Test", new BigDecimal("50.00"));
        var updated = product.updatePrice(new BigDecimal("75.00"));

        assertThat(updated.unitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(updated.id()).isEqualTo(product.id());
        assertThat(updated.name()).isEqualTo(product.name());
    }

    @Test
    void shouldThrowWhenUpdatingToNegativePrice() {
        var product = Product.create("Test", new BigDecimal("50.00"));

        assertThatThrownBy(() -> product.updatePrice(new BigDecimal("-10.00")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");
    }

    @Test
    void shouldDeactivateProduct() {
        var product = Product.create("Test", new BigDecimal("50.00"));
        var deactivated = product.deactivate();

        assertThat(deactivated.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(deactivated.id()).isEqualTo(product.id());
    }

    @Test
    void shouldThrowWhenDeactivatingAlreadyInactiveProduct() {
        var product = Product.create("Test", new BigDecimal("50.00"));
        var deactivated = product.deactivate();

        assertThatThrownBy(deactivated::deactivate)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already inactive");
    }

    @Test
    void shouldReconstituteProduct() {
        var id = UUID.randomUUID();
        var now = Instant.now();
        var product = Product.reconstitute(id, "Test", new BigDecimal("25.00"),
            ProductStatus.ACTIVE, now, now);

        assertThat(product.id()).isEqualTo(id);
        assertThat(product.name()).isEqualTo("Test");
    }

    @Test
    void shouldReturnTrueForIsActiveWhenActive() {
        var product = Product.create("Test", new BigDecimal("10.00"));
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void shouldReturnFalseForIsActiveWhenInactive() {
        var product = Product.create("Test", new BigDecimal("10.00")).deactivate();
        assertThat(product.isActive()).isFalse();
    }
}
