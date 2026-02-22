package io.mars.lite.api.event;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.mars.lite.order.OrderCreatedEvent;
import io.mars.lite.order.OrderEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderCreatedPublisher implements OrderEventPublisher {

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
            kafkaTemplate.send(TOPIC, event.orderId().toString(), payload);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
        }
    }
}
