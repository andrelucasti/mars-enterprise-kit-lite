package io.mars.lite.api;

import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import io.mars.lite.domain.usecase.CreateOrderUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderRepository orderRepository;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           OrderRepository orderRepository) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> createOrder(
            @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
            .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());

        var input = new CreateOrderUseCase.Input(items, request.customerId());
        var orderId = createOrderUseCase.execute(input);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("orderId", orderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
            .map(OrderResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
