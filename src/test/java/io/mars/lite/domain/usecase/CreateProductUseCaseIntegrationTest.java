package io.mars.lite.domain.usecase;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.ProductStatus;
import io.mars.lite.infrastructure.persistence.ProductJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateProductUseCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateProductUseCase createProductUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void cleanUp() {
        productJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should create product and persist in database")
    void shouldCreateProductAndPersistInDatabase() {
        var input = new CreateProductUseCase.Input("Mars Rover Kit", new BigDecimal("299.99"));

        var productId = createProductUseCase.execute(input);

        assertThat(productId).isNotNull();
        var entity = productJpaRepository.findById(productId).orElseThrow();
        assertThat(entity.getName()).isEqualTo("Mars Rover Kit");
        assertThat(entity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("299.99"));
        assertThat(entity.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should not persist when validation fails")
    void shouldNotPersistWhenValidationFails() {
        var input = new CreateProductUseCase.Input("", new BigDecimal("99.99"));

        assertThatThrownBy(() -> createProductUseCase.execute(input))
            .isInstanceOf(BusinessException.class);

        assertThat(productJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("should not persist when price is invalid")
    void shouldNotPersistWhenPriceIsInvalid() {
        var input = new CreateProductUseCase.Input("Valid Name", new BigDecimal("-10.00"));

        assertThatThrownBy(() -> createProductUseCase.execute(input))
            .isInstanceOf(BusinessException.class);

        assertThat(productJpaRepository.findAll()).isEmpty();
    }
}
