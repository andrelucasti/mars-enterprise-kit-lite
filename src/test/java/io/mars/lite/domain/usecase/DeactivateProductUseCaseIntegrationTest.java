package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.domain.ProductStatus;
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

class DeactivateProductUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DeactivateProductUseCase deactivateProductUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void cleanUp() {
        productJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should deactivate product and persist in database")
    void shouldDeactivateProductAndPersistInDatabase() {
        // Given: an active product
        var product = Product.create("Test Product", new BigDecimal("50.00"));
        productJpaRepository.save(ProductEntity.of(product));

        // When: we deactivate it
        deactivateProductUseCase.execute(product.id());

        // Then: the database shows INACTIVE status
        var entity = productJpaRepository.findById(product.id()).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    @DisplayName("should throw when product not found")
    void shouldThrowWhenProductNotFound() {
        var nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> deactivateProductUseCase.execute(nonExistentId))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("should throw when deactivating already inactive product")
    void shouldThrowWhenDeactivatingAlreadyInactiveProduct() {
        // Given: an inactive product
        var product = Product.create("Test Product", new BigDecimal("50.00")).deactivate();
        productJpaRepository.save(ProductEntity.of(product));

        // When/Then: we try to deactivate it again
        assertThatThrownBy(() -> deactivateProductUseCase.execute(product.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already inactive");
    }
}
