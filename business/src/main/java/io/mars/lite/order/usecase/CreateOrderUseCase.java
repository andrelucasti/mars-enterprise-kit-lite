package io.mars.lite.order.usecase;

import io.mars.lite.order.OrderEventPublisher;
import io.mars.lite.order.OrderItem;
import io.mars.lite.order.OrderRepository;

import java.util.Set;
import java.util.UUID;

public sealed interface CreateOrderUseCase permits CreateOrderUseCaseImpl {

    static CreateOrderUseCase create(OrderRepository repository, OrderEventPublisher publisher) {
        return new CreateOrderUseCaseImpl(repository, publisher);
    }

    UUID execute(Input input);

    record Input(Set<OrderItem> items, UUID customerId) {}
}
