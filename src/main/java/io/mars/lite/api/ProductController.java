package io.mars.lite.api;

import io.mars.lite.domain.ProductNotFoundException;
import io.mars.lite.domain.ProductRepository;
import io.mars.lite.domain.usecase.CreateProductUseCase;
import io.mars.lite.domain.usecase.DeactivateProductUseCase;
import io.mars.lite.domain.usecase.UpdateProductPriceUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductPriceUseCase updateProductPriceUseCase;
    private final DeactivateProductUseCase deactivateProductUseCase;
    private final ProductRepository productRepository;

    public ProductController(CreateProductUseCase createProductUseCase,
                              UpdateProductPriceUseCase updateProductPriceUseCase,
                              DeactivateProductUseCase deactivateProductUseCase,
                              ProductRepository productRepository) {
        this.createProductUseCase = createProductUseCase;
        this.updateProductPriceUseCase = updateProductPriceUseCase;
        this.deactivateProductUseCase = deactivateProductUseCase;
        this.productRepository = productRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> createProduct(@RequestBody CreateProductRequest request) {
        var input = new CreateProductUseCase.Input(request.name(), request.unitPrice());
        var productId = createProductUseCase.execute(input);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("productId", productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        return productRepository.findById(id)
            .map(ProductResponse::from)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> listActiveProducts() {
        var products = productRepository.findAllActive().stream()
            .map(ProductResponse::from)
            .toList();
        return ResponseEntity.ok(products);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProductPrice(
            @PathVariable UUID id,
            @RequestBody UpdateProductPriceRequest request) {
        var input = new UpdateProductPriceUseCase.Input(id, request.unitPrice());
        var updated = updateProductPriceUseCase.execute(input);
        return ResponseEntity.ok(ProductResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        deactivateProductUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
