package io.mars.lite.infrastructure.persistence;

import io.mars.lite.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    List<ProductEntity> findByStatus(ProductStatus status);
    List<ProductEntity> findByIdIn(Set<UUID> ids);
}
