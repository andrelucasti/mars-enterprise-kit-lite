package io.mars.lite.domain.usecase;

import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
public class CreateProductUseCase {

    private final ProductRepository productRepository;

    public CreateProductUseCase(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository cannot be null");
    }

    @Transactional
    public UUID execute(final Input input) {
        var product = Product.create(input.name(), input.unitPrice());
        productRepository.save(product);
        return product.id();
    }

    public record Input(String name, BigDecimal unitPrice) {}
}
