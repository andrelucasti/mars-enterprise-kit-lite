package io.mars.lite.api.order;

import io.mars.lite.order.OrderItem;
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

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> createOrder(
            @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
            .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
            .collect(Collectors.toSet());

        var orderId = orderService.createOrder(items, request.customerId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("orderId", orderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
            .map(OrderResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
