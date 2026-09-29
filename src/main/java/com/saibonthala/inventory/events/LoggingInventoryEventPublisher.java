package com.saibonthala.inventory.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default publisher for local development and tests (inventory.events.publisher=logging). */
@Component
@ConditionalOnProperty(prefix = "inventory.events", name = "publisher", havingValue = "logging", matchIfMissing = true)
public class LoggingInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingInventoryEventPublisher.class);

    @Override
    public void publish(InventoryEvent event) {
        log.info("Inventory event: {}", event);
    }
}
