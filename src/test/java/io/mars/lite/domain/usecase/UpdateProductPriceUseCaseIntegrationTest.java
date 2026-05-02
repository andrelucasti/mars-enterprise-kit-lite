package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.infrastructure.persistence.ProductEntity;
import io.mars.lite.infrastructure.persistence.ProductJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateProductPriceUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UpdateProductPriceUseCase updateProductPriceUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void cleanUp() {
        productJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should update price and persist in database")
    void shouldUpdatePriceAndPersistInDatabase() {
        // Given: a product in the database
        var product = Product.create("Test Product", new BigDecimal("50.00"));
        productJpaRepository.save(ProductEntity.of(product));

        // When: we update the price
        var input = new UpdateProductPriceUseCase.Input(product.id(), new BigDecimal("75.00"));
        var updated = updateProductPriceUseCase.execute(input);

        // Then: the database reflects the new price
        assertThat(updated.unitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));

        var entity = productJpaRepository.findById(product.id()).orElseThrow();
        assertThat(entity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("should throw when product not found")
    void shouldThrowWhenProductNotFound() {
        var nonExistentId = UUID.randomUUID();
        var input = new UpdateProductPriceUseCase.Input(nonExistentId, new BigDecimal("75.00"));

        assertThatThrownBy(() -> updateProductPriceUseCase.execute(input))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("should not update when new price is invalid")
    void shouldNotUpdateWhenNewPriceIsInvalid() {
        // Given: a product in the database
        var product = Product.create("Test Product", new BigDecimal("50.00"));
        productJpaRepository.save(ProductEntity.of(product));

        // When: we try to update with invalid price
        var input = new UpdateProductPriceUseCase.Input(product.id(), new BigDecimal("-10.00"));

        assertThatThrownBy(() -> updateProductPriceUseCase.execute(input))
            .isInstanceOf(BusinessException.class);

        // Then: original price is preserved
        var entity = productJpaRepository.findById(product.id()).orElseThrow();
        assertThat(entity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
