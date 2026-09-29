package com.saibonthala.inventory.events;

/**
 * Abstraction over the messaging system (Dependency Inversion): the service layer
 * depends on this interface, not on Kafka, which keeps it testable and swappable.
 * Implementations: logging (default), Kafka, and AWS SQS, chosen with inventory.events.publisher.
 */
public interface InventoryEventPublisher {

    void publish(InventoryEvent event);
}
