package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderEventPublisher;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import io.mars.lite.domain.Product;
import io.mars.lite.domain.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ProductRepository productRepository;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               OrderEventPublisher orderEventPublisher,
                               ProductRepository productRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository cannot be null");
        this.orderEventPublisher = Objects.requireNonNull(orderEventPublisher, "orderEventPublisher cannot be null");
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository cannot be null");
    }

    @Transactional
    public UUID execute(final Input input) {
        // 1. Validate input is not empty
        if (input.items() == null || input.items().isEmpty()) {
            throw new BusinessException("items cannot be empty");
        }

        // 2. Collect productIds for batch fetch
        var productIds = input.items().stream()
            .map(OrderItemInput::productId)
            .collect(Collectors.toSet());

        // 3. Batch fetch products (prevents N+1)
        var products = productRepository.findAllByIds(productIds);
        var productMap = products.stream()
            .collect(Collectors.toMap(Product::id, Function.identity()));

        // 4. Validate products and build OrderItems with aggregated errors
        var errors = new ArrayList<String>();
        var orderItems = new HashSet<OrderItem>();

        for (var itemInput : input.items()) {
            var product = productMap.get(itemInput.productId());
            if (product == null) {
                errors.add("Product not found: " + itemInput.productId());
            } else if (!product.isActive()) {
                errors.add("Product is inactive: " + itemInput.productId());
            } else {
                // Build OrderItem with catalog price (not request price)
                orderItems.add(new OrderItem(
                    itemInput.productId(),
                    itemInput.quantity(),
                    product.unitPrice()
                ));
            }
        }

        // 5. Throw aggregated errors if any
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("; ", errors));
        }

        // 6. Create order with validated items (using catalog prices)
        var result = Order.create(input.customerId(), orderItems);
        orderRepository.save(result.domain());

        // 7. Dual Write: publish event (no atomicity guarantee)
        try {
            orderEventPublisher.publish(result.event());
        } catch (Exception e) {
            log.warn("DUAL WRITE FAILURE - EVENT LOST for orderId={}. Order saved in DB but event NOT published to Kafka. Cause: {}",
                    result.domain().id(), e.getMessage());
        }

        return result.domain().id();
    }

    /**
     * Input for order creation. Items contain only productId and quantity.
     * The unitPrice is fetched from the product catalog.
     */
    public record Input(Set<OrderItemInput> items, UUID customerId) {}

    /**
     * Order item input without price. Price comes from product catalog.
     */
    public record OrderItemInput(UUID productId, int quantity) {
        public OrderItemInput {
            Objects.requireNonNull(productId, "productId cannot be null");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }
}
