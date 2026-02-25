package io.mars.lite.domain;

public interface OrderEventPublisher {
    void publish(OrderCreatedEvent event);
}
