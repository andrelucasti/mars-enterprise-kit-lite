package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CreateProductUseCaseTest {

    private ProductRepository productRepository;
    private CreateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        useCase = new CreateProductUseCase(productRepository);
    }

    @Test
    void shouldCreateProductAndCallRepository() {
        var input = new CreateProductUseCase.Input("Mars Rover", new BigDecimal("299.99"));

        var productId = useCase.execute(input);

        assertThat(productId).isNotNull();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        var input = new CreateProductUseCase.Input("", new BigDecimal("99.99"));

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("name cannot be blank");

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPriceIsNegative() {
        var input = new CreateProductUseCase.Input("Test Product", new BigDecimal("-10.00"));

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPriceIsZero() {
        var input = new CreateProductUseCase.Input("Test Product", BigDecimal.ZERO);

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");

        verify(productRepository, never()).save(any());
    }
}
