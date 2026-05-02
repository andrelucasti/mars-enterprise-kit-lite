package io.mars.lite.infrastructure.persistence;

import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductRepository;
import io.mars.lite.domain.ProductStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryImpl(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Product product) {
        jpaRepository.save(ProductEntity.of(product));
    }

    @Override
    public Optional<Product> findById(UUID productId) {
        return jpaRepository.findById(productId).map(ProductEntity::toDomain);
    }

    @Override
    public void update(Product product) {
        var entity = jpaRepository.findById(product.id())
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + product.id()));
        entity.updateFrom(product);
        jpaRepository.save(entity);
    }

    @Override
    public List<Product> findAllActive() {
        return jpaRepository.findByStatus(ProductStatus.ACTIVE).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByIds(Set<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByIdIn(productIds).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }
}
