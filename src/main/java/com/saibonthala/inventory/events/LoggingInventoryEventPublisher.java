package com.saibonthala.inventory.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default publisher for local development and tests, when Kafka is disabled. */
@Component
@ConditionalOnProperty(prefix = "inventory.events.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingInventoryEventPublisher.class);

    @Override
    public void publish(InventoryEvent event) {
        log.info("Inventory event: {}", event);
    }
}
