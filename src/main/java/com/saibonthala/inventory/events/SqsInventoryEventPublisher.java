package com.saibonthala.inventory.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * Publishes inventory events to an AWS SQS queue as JSON (inventory.events.publisher=sqs).
 * Credentials and region come from the standard AWS provider chain
 * (environment variables, ~/.aws, or the EC2/ECS instance role).
 */
@Component
@ConditionalOnProperty(prefix = "inventory.events", name = "publisher", havingValue = "sqs")
public class SqsInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsInventoryEventPublisher.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String queueUrl;

    public SqsInventoryEventPublisher(ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry,
                                      @Value("${inventory.events.sqs.queue-url}") String queueUrl) {
        this.sqsClient = SqsClient.create();
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(InventoryEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize inventory event " + event.eventId(), e);
        }
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)
                .messageAttributes(Map.of(
                        "sku", MessageAttributeValue.builder().dataType("String").stringValue(event.sku()).build(),
                        "type", MessageAttributeValue.builder().dataType("String").stringValue(event.type().name()).build()))
                .build();
        try {
            sqsClient.sendMessage(request);
            meterRegistry.counter("inventory.events.published").increment();
        } catch (SdkException e) {
            meterRegistry.counter("inventory.events.publish.failed").increment();
            log.error("Failed to publish inventory event {} to SQS", event.eventId(), e);
        }
    }

    @PreDestroy
    public void close() {
        sqsClient.close();
    }
}
