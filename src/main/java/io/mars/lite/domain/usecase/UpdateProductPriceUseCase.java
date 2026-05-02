package io.mars.lite.domain.usecase;

import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateProductPriceUseCase {

    private final ProductRepository productRepository;

    public UpdateProductPriceUseCase(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository cannot be null");
    }

    @Transactional
    public Product execute(final Input input) {
        var product = productRepository.findById(input.productId())
            .orElseThrow(() -> new ProductNotFoundException(input.productId()));
        var updated = product.updatePrice(input.newPrice());
        productRepository.update(updated);
        return updated;
    }

    public record Input(UUID productId, BigDecimal newPrice) {}
}
