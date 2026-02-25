package io.mars.lite.api.chaos;

import io.mars.lite.domain.OrderRepository;
import io.mars.lite.domain.usecase.CreateOrderUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Profile("chaos")
public class ChaosService {

    private final ChaosOrderExecutor chaosOrderExecutor;
    private final OrderRepository orderRepository;

    public ChaosService(ChaosOrderExecutor chaosOrderExecutor,
                        OrderRepository orderRepository) {
        this.chaosOrderExecutor = chaosOrderExecutor;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public UUID attemptPhantomOrder(CreateOrderUseCase.Input input) {
        return chaosOrderExecutor.execute(input);
    }

    @Transactional(readOnly = true)
    public boolean orderExists(UUID orderId) {
        return orderRepository.findById(orderId).isPresent();
    }
}
