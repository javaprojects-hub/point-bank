package com.pointbank.bankplatform.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.pointbank.bankplatform.config.OutboxPublisherProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxClaimService claimService;

    @Mock
    private OutboxEventStateService stateService;

    private OutboxPublisherProperties properties;
    private OutboxPublisher publisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        properties = new OutboxPublisherProperties();
        meterRegistry = new SimpleMeterRegistry();
        publisher = new OutboxPublisher(kafkaTemplate, claimService, stateService, properties, meterRegistry);
    }

    @Test
    void waitsForKafkaAcknowledgmentBeforeMarkingEventPublished() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent("event-123", "{\"eventId\":\"event-123\"}");
        when(claimService.claimBatch(anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(properties.getTopic(), event.eventId(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(stateService.markPublished(eq(event.eventId()), anyString())).thenReturn(true);

        publisher.publishEligibleEvents();

        ArgumentCaptor<String> claimant = ArgumentCaptor.forClass(String.class);
        verify(claimService).claimBatch(claimant.capture());
        verify(kafkaTemplate).send(properties.getTopic(), event.eventId(), event.payload());
        verify(stateService).markPublished(event.eventId(), claimant.getValue());
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0,
                meterRegistry.counter("pointbank.outbox.events", "outcome", "published").count()
        );
    }

    @Test
    void schedulesRetryWhenKafkaPublishFails() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent("event-456", "{\"eventId\":\"event-456\"}");
        when(claimService.claimBatch(anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(properties.getTopic(), event.eventId(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        when(stateService.recordFailure(eq(event.eventId()), anyString(), anyString()))
                .thenReturn(new OutboxEventStateService.FailureResult(true, false));

        publisher.publishEligibleEvents();

        verify(stateService).recordFailure(eq(event.eventId()), anyString(), eq(
                "IllegalStateException: broker unavailable"
        ));
        verify(stateService, never()).markPublished(anyString(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0,
                meterRegistry.counter("pointbank.outbox.events", "outcome", "retry_scheduled").count()
        );
    }

    @Test
    void doesNotClaimEventsWhenPublisherIsDisabled() {
        properties.setEnabled(false);

        publisher.publishEligibleEvents();

        verify(claimService, never()).claimBatch(anyString());
    }
}
