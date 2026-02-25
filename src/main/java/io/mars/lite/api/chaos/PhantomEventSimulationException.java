package io.mars.lite.api.chaos;

import java.util.UUID;

public class PhantomEventSimulationException extends RuntimeException {

    private final UUID orderId;

    public PhantomEventSimulationException(UUID orderId) {
        super("Phantom event simulation: DB rollback forced after Kafka publish for order " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
