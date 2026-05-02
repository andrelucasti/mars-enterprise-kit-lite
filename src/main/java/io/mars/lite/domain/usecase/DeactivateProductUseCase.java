package io.mars.lite.domain.usecase;

import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class DeactivateProductUseCase {

    private final ProductRepository productRepository;

    public DeactivateProductUseCase(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository cannot be null");
    }

    @Transactional
    public void execute(UUID productId) {
        var product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        var deactivated = product.deactivate();
        productRepository.update(deactivated);
    }
}
