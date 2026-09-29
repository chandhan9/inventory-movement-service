package com.saibonthala.inventory.events;

/**
 * Abstraction over the messaging system (Dependency Inversion): the service layer
 * depends on this interface, not on Kafka, which keeps it testable and swappable (e.g. SQS).
 */
public interface InventoryEventPublisher {

    void publish(InventoryEvent event);
}
