package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.domain.ProductRepository;
import io.mars.lite.domain.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeactivateProductUseCaseTest {

    private ProductRepository productRepository;
    private DeactivateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        useCase = new DeactivateProductUseCase(productRepository);
    }

    @Test
    void shouldDeactivateProduct() {
        var productId = UUID.randomUUID();
        var existingProduct = Product.create("Test Product", new BigDecimal("50.00"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        useCase.execute(productId);

        var captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).update(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void shouldThrowProductNotFoundWhenProductDoesNotExist() {
        var productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(productId))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining(productId.toString());

        verify(productRepository, never()).update(any());
    }

    @Test
    void shouldThrowWhenProductAlreadyInactive() {
        var productId = UUID.randomUUID();
        var now = Instant.now();
        var inactiveProduct = Product.reconstitute(productId, "Test", new BigDecimal("50.00"),
            ProductStatus.INACTIVE, now, now);
        when(productRepository.findById(productId)).thenReturn(Optional.of(inactiveProduct));

        assertThatThrownBy(() -> useCase.execute(productId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already inactive");

        verify(productRepository, never()).update(any());
    }
}
