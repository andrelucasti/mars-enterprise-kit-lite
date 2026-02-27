package io.mars.lite.domain.usecase;

import io.mars.lite.domain.Order;
import io.mars.lite.domain.OrderEventPublisher;
import io.mars.lite.domain.OrderItem;
import io.mars.lite.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               OrderEventPublisher orderEventPublisher) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository cannot be null");
        this.orderEventPublisher = Objects.requireNonNull(orderEventPublisher, "orderEventPublisher cannot be null");
    }

    @Transactional
    public UUID execute(final Input input) {
        var result = Order.create(input.customerId(), input.items());
        orderRepository.save(result.domain());

        // ⚠️ DUAL WRITE: no atomicity guarantee between DB and Kafka.
        // If publish fails, the order exists in PostgreSQL but the event is silently lost.
        try {
            orderEventPublisher.publish(result.event());
        } catch (Exception e) {
            log.warn("DUAL WRITE FAILURE — EVENT LOST for orderId={}. Order saved in DB but event NOT published to Kafka. Cause: {}",
                    result.domain().id(), e.getMessage());
        }

        return result.domain().id();
    }

    public record Input(Set<OrderItem> items, UUID customerId) {}
}
