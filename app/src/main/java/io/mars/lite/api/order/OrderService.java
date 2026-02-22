package io.mars.lite.api.order;

import io.mars.lite.order.Order;
import io.mars.lite.order.OrderItem;
import io.mars.lite.order.OrderRepository;
import io.mars.lite.order.usecase.CancelOrderUseCase;
import io.mars.lite.order.usecase.CreateOrderUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderRepository orderRepository;

    public OrderService(CreateOrderUseCase createOrderUseCase,
                        CancelOrderUseCase cancelOrderUseCase,
                        OrderRepository orderRepository) {
        this.createOrderUseCase = Objects.requireNonNull(createOrderUseCase);
        this.cancelOrderUseCase = Objects.requireNonNull(cancelOrderUseCase);
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @Transactional
    public UUID createOrder(Set<OrderItem> items, UUID customerId) {
        return createOrderUseCase.execute(
            new CreateOrderUseCase.Input(items, customerId));
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        cancelOrderUseCase.execute(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return orderRepository.findById(orderId);
    }
}
