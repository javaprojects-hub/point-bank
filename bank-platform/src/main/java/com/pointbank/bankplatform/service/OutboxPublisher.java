package com.pointbank.bankplatform.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.pointbank.bankplatform.config.OutboxPublisherProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String METRIC_NAME = "pointbank.outbox.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxClaimService claimService;
    private final OutboxEventStateService stateService;
    private final OutboxPublisherProperties properties;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxClaimService claimService,
            OutboxEventStateService stateService,
            OutboxPublisherProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.claimService = claimService;
        this.stateService = stateService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${pointbank.outbox.poll-interval:PT1S}")
    public void publishEligibleEvents() {
        if (!properties.isEnabled()) {
            return;
        }

        String claimant = UUID.randomUUID().toString();
        List<ClaimedOutboxEvent> events = claimService.claimBatch(claimant);
        increment("claimed", events.size());

        List<PublishAttempt> attempts = events.stream().map(event -> {
            try {
                CompletableFuture<SendResult<String, String>> acknowledgment = kafkaTemplate.send(
                        properties.getTopic(),
                        event.eventId(),
                        event.payload()
                );
                return new PublishAttempt(event, acknowledgment, null);
            } catch (RuntimeException exception) {
                return new PublishAttempt(event, null, exception);
            }
        }).toList();

        for (PublishAttempt attempt : attempts) {
            if (attempt.sendError() != null) {
                recordFailure(attempt.event(), claimant, attempt.sendError());
                continue;
            }
            if (!publish(attempt.event(), attempt.acknowledgment(), claimant)) {
                break;
            }
        }
    }

    private boolean publish(
            ClaimedOutboxEvent event,
            CompletableFuture<SendResult<String, String>> acknowledgment,
            String claimant
    ) {
        try {
            acknowledgment.get(properties.getAcknowledgmentTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (stateService.markPublished(event.eventId(), claimant)) {
                increment("published", 1);
                LOGGER.info("Outbox event published: eventId={}", event.eventId());
            } else {
                increment("stale_acknowledgment", 1);
                LOGGER.warn("Kafka acknowledged an outbox event after its claim changed: eventId={}", event.eventId());
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for Kafka acknowledgment: eventId={}", event.eventId());
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            recordFailure(event, claimant, exception);
            return true;
        }
    }

    private void recordFailure(ClaimedOutboxEvent event, String claimant, Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        OutboxEventStateService.FailureResult result = stateService.recordFailure(
                event.eventId(),
                claimant,
                error
        );
        if (result.updated()) {
            increment(result.exhausted() ? "failed" : "retry_scheduled", 1);
            LOGGER.warn(
                    "Outbox event publish failed: eventId={}, exhausted={}, error={}",
                    event.eventId(),
                    result.exhausted(),
                    error
            );
        } else {
            increment("stale_failure", 1);
            LOGGER.warn("Outbox publish failed after its claim changed: eventId={}", event.eventId(), exception);
        }
    }

    private void increment(String outcome, int amount) {
        if (amount > 0) {
            meterRegistry.counter(METRIC_NAME, "outcome", outcome).increment(amount);
        }
    }

    private record PublishAttempt(
            ClaimedOutboxEvent event,
            CompletableFuture<SendResult<String, String>> acknowledgment,
            Exception sendError
    ) {
    }
}
