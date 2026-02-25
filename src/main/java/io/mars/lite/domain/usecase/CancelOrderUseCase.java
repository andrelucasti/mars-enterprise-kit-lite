package io.mars.lite.domain.usecase;

import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;

    public CancelOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository cannot be null");
    }

    @Transactional
    public void execute(UUID orderId) {
        var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found: " + orderId));
        var cancelled = order.cancel();
        orderRepository.update(cancelled);
    }
}
