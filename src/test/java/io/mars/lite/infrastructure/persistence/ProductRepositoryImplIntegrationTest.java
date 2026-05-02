package io.mars.lite.infrastructure.persistence;

import io.mars.lite.AbstractIntegrationTest;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductRepository;
import io.mars.lite.domain.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("should save product and find by id")
    void shouldSaveProductAndFindById() {
        var product = Product.create("Mars Rover", new BigDecimal("299.99"));
        productRepository.save(product);

        var found = productRepository.findById(product.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Mars Rover");
        assertThat(found.get().unitPrice()).isEqualByComparingTo(new BigDecimal("299.99"));
        assertThat(found.get().status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("should return empty when product not found")
    void shouldReturnEmptyWhenProductNotFound() {
        var found = productRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should update product price")
    void shouldUpdateProductPrice() {
        var product = Product.create("Test Product", new BigDecimal("50.00"));
        productRepository.save(product);

        var updated = product.updatePrice(new BigDecimal("75.00"));
        productRepository.update(updated);

        var found = productRepository.findById(product.id());
        assertThat(found).isPresent();
        assertThat(found.get().unitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("should update product status")
    void shouldUpdateProductStatus() {
        var product = Product.create("Test Product", new BigDecimal("50.00"));
        productRepository.save(product);

        var deactivated = product.deactivate();
        productRepository.update(deactivated);

        var found = productRepository.findById(product.id());
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    @DisplayName("should find all active products")
    void shouldFindAllActiveProducts() {
        // Create active products
        var active1 = Product.create("Active 1", new BigDecimal("10.00"));
        var active2 = Product.create("Active 2", new BigDecimal("20.00"));
        productRepository.save(active1);
        productRepository.save(active2);

        // Create and deactivate one product
        var inactive = Product.create("Inactive", new BigDecimal("30.00"));
        productRepository.save(inactive);
        productRepository.update(inactive.deactivate());

        var activeProducts = productRepository.findAllActive();

        assertThat(activeProducts).hasSize(2);
        assertThat(activeProducts).extracting(Product::name)
            .containsExactlyInAnyOrder("Active 1", "Active 2");
    }

    @Test
    @DisplayName("should find all by ids")
    void shouldFindAllByIds() {
        var product1 = Product.create("Product 1", new BigDecimal("10.00"));
        var product2 = Product.create("Product 2", new BigDecimal("20.00"));
        var product3 = Product.create("Product 3", new BigDecimal("30.00"));

        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        var found = productRepository.findAllByIds(Set.of(product1.id(), product3.id()));

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Product::name)
            .containsExactlyInAnyOrder("Product 1", "Product 3");
    }

    @Test
    @DisplayName("should return empty list when no ids match")
    void shouldReturnEmptyListWhenNoIdsMatch() {
        var found = productRepository.findAllByIds(Set.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for empty id set")
    void shouldReturnEmptyListForEmptyIdSet() {
        var found = productRepository.findAllByIds(Set.of());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for null id set")
    void shouldReturnEmptyListForNullIdSet() {
        var found = productRepository.findAllByIds(null);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should persist audit timestamps")
    void shouldPersistAuditTimestamps() {
        var product = Product.create("Test", new BigDecimal("10.00"));
        productRepository.save(product);

        var entity = jpaRepository.findById(product.id()).orElseThrow();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }
}
