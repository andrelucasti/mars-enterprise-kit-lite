package io.mars.lite.api.chaos;

import io.mars.lite.order.usecase.CreateOrderUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("chaos")
public class ChaosOrderExecutor {

    private final CreateOrderUseCase createOrderUseCase;

    public ChaosOrderExecutor(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    public UUID execute(CreateOrderUseCase.Input input) {
        return createOrderUseCase.execute(input);
    }
}
