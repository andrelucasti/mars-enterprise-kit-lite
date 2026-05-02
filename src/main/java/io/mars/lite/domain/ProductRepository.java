package io.mars.lite.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepository {
    void save(Product product);
    Optional<Product> findById(UUID productId);
    void update(Product product);
    List<Product> findAllActive();
    List<Product> findAllByIds(Set<UUID> productIds);
}
