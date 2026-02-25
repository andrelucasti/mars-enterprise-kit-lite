package io.mars.lite.infrastructure.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.mars.lite.domain.BusinessException;
import io.mars.lite.domain.usecase.CancelOrderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);
    private final CancelOrderUseCase cancelOrderUseCase;
    private final ObjectMapper objectMapper;

    public OrderCancelledConsumer(CancelOrderUseCase cancelOrderUseCase, ObjectMapper objectMapper) {
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.cancelled", groupId = "order-service")
    public void onOrderCancelled(String message) {
        try {
            var payload = objectMapper.readValue(message, OrderCancelledPayload.class);
            log.info("Received order.cancelled event for orderId={}", payload.orderId());
            cancelOrderUseCase.execute(payload.orderId());
        } catch (JacksonException e) {
            log.error("Failed to deserialize order.cancelled event", e);
        } catch (BusinessException e) {
            log.warn("Failed to cancel order: {}", e.getMessage());
        }
    }

    record OrderCancelledPayload(UUID eventId, UUID orderId, String reason, Instant occurredAt) {}
}
