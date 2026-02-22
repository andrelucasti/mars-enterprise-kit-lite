package io.mars.lite.api.event;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.mars.lite.api.order.OrderService;
import io.mars.lite.order.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderCancelledConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.cancelled", groupId = "order-service")
    public void onOrderCancelled(String message) {
        try {
            var payload = objectMapper.readValue(message, OrderCancelledPayload.class);
            log.info("Received order.cancelled event for orderId={}", payload.orderId());
            orderService.cancelOrder(payload.orderId());
        } catch (JacksonException e) {
            log.error("Failed to deserialize order.cancelled event", e);
        } catch (BusinessException e) {
            log.warn("Failed to cancel order: {}", e.getMessage());
        }
    }

    record OrderCancelledPayload(UUID eventId, UUID orderId, String reason, Instant occurredAt) {}
}
