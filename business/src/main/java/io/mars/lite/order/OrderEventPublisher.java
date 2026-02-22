package io.mars.lite.order;

public interface OrderEventPublisher {
    void publish(OrderCreatedEvent event);
}
