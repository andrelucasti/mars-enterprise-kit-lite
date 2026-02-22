package io.mars.lite.order.usecase;

import io.mars.lite.order.OrderRepository;

import java.util.UUID;

public sealed interface CancelOrderUseCase permits CancelOrderUseCaseImpl {

    static CancelOrderUseCase create(OrderRepository repository) {
        return new CancelOrderUseCaseImpl(repository);
    }

    void execute(UUID orderId);
}
