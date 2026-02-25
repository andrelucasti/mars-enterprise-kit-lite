package io.mars.lite.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(UUID orderId);
    void update(Order order);
}
