package io.mars.lite.order.usecase;

import io.mars.lite.order.BusinessException;
import io.mars.lite.order.OrderRepository;

import java.util.UUID;

record CancelOrderUseCaseImpl(OrderRepository orderRepository) implements CancelOrderUseCase {

    @Override
    public void execute(UUID orderId) {
        var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found: " + orderId));
        var cancelled = order.cancel();
        orderRepository.update(cancelled);
    }
}
