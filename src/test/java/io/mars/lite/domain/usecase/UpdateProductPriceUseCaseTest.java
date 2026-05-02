package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateProductPriceUseCaseTest {

    private ProductRepository productRepository;
    private UpdateProductPriceUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        useCase = new UpdateProductPriceUseCase(productRepository);
    }

    @Test
    void shouldUpdatePriceAndReturnUpdatedProduct() {
        var productId = UUID.randomUUID();
        var existingProduct = Product.create("Test Product", new BigDecimal("50.00"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        var input = new UpdateProductPriceUseCase.Input(productId, new BigDecimal("75.00"));
        var result = useCase.execute(input);

        assertThat(result.unitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
        verify(productRepository).update(any(Product.class));
    }

    @Test
    void shouldThrowProductNotFoundWhenProductDoesNotExist() {
        var productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        var input = new UpdateProductPriceUseCase.Input(productId, new BigDecimal("75.00"));

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining(productId.toString());

        verify(productRepository, never()).update(any());
    }

    @Test
    void shouldThrowWhenNewPriceIsNegative() {
        var productId = UUID.randomUUID();
        var existingProduct = Product.create("Test Product", new BigDecimal("50.00"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        var input = new UpdateProductPriceUseCase.Input(productId, new BigDecimal("-10.00"));

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");

        verify(productRepository, never()).update(any());
    }

    @Test
    void shouldThrowWhenNewPriceIsZero() {
        var productId = UUID.randomUUID();
        var existingProduct = Product.create("Test Product", new BigDecimal("50.00"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        var input = new UpdateProductPriceUseCase.Input(productId, BigDecimal.ZERO);

        assertThatThrownBy(() -> useCase.execute(input))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("unitPrice must be positive");

        verify(productRepository, never()).update(any());
    }
}
