package io.mars.lite.infrastructure.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.mars.lite.domain.OrderCreatedEvent;
import io.mars.lite.domain.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderCreatedPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedPublisher.class);
    private static final String TOPIC = "order.created";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderCreatedPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.orderId().toString(), payload)
                    .exceptionally(ex -> {
                        log.error("DUAL WRITE FAILURE — EVENT LOST for orderId={}." +
                                " Order exists in DB but event was NOT delivered to Kafka. Cause: {}",
                                event.orderId(), ex.getMessage());
                        return null;
                    });
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
        }
    }
}
