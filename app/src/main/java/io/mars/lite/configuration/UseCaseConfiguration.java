package io.mars.lite.configuration;

import io.mars.lite.order.OrderEventPublisher;
import io.mars.lite.order.OrderRepository;
import io.mars.lite.order.usecase.CancelOrderUseCase;
import io.mars.lite.order.usecase.CreateOrderUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orderRepository,
                                                  OrderEventPublisher orderEventPublisher) {
        return CreateOrderUseCase.create(orderRepository, orderEventPublisher);
    }

    @Bean
    public CancelOrderUseCase cancelOrderUseCase(OrderRepository orderRepository) {
        return CancelOrderUseCase.create(orderRepository);
    }
}
