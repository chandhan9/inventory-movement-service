package com.saibonthala.inventory.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes inventory events to Kafka as JSON. Messages are keyed by SKU so every
 * event for the same SKU lands on the same partition and is consumed in order.
 */
@Component
@ConditionalOnProperty(prefix = "inventory.events.kafka", name = "enabled", havingValue = "true")
public class KafkaInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaInventoryEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String topic;

    public KafkaInventoryEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                        ObjectMapper objectMapper,
                                        MeterRegistry meterRegistry,
                                        @Value("${inventory.events.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
    }

    @Override
    public void publish(InventoryEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize inventory event " + event.eventId(), e);
        }
        kafkaTemplate.send(topic, event.sku(), payload).whenComplete((result, error) -> {
            if (error != null) {
                meterRegistry.counter("inventory.events.publish.failed").increment();
                log.error("Failed to publish inventory event {}", event.eventId(), error);
            } else {
                meterRegistry.counter("inventory.events.published").increment();
            }
        });
    }
}
