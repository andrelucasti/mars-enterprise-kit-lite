package io.mars.lite.order.usecase;

import io.mars.lite.order.Order;
import io.mars.lite.order.OrderEventPublisher;
import io.mars.lite.order.OrderRepository;

import java.util.UUID;

record CreateOrderUseCaseImpl(
    OrderRepository orderRepository,
    OrderEventPublisher orderEventPublisher
) implements CreateOrderUseCase {

    @Override
    public UUID execute(Input input) {
        var result = Order.create(input.customerId(), input.items());
        orderRepository.save(result.domain());
        orderEventPublisher.publish(result.event());
        return result.domain().id();
    }
}