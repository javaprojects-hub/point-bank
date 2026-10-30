package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.pointbank.bankplatform.config.OutboxPublisherProperties;
import com.pointbank.bankplatform.constant.OutboxEventStatus;
import com.pointbank.bankplatform.entity.OutboxEvent;
import com.pointbank.bankplatform.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventStateServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxPublisherProperties properties;
    private OutboxEventStateService stateService;

    @BeforeEach
    void setUp() {
        properties = new OutboxPublisherProperties();
        stateService = new OutboxEventStateService(outboxEventRepository, properties);
    }

    @Test
    void schedulesRetryAndClearsClaimAfterFailure() {
        OutboxEvent event = processingEvent("claim-1");
        when(outboxEventRepository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        OutboxEventStateService.FailureResult result = stateService.recordFailure(
                event.getEventId(),
                "claim-1",
                "broker unavailable"
        );

        assertTrue(result.updated());
        assertFalse(result.exhausted());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertEquals("broker unavailable", event.getLastError());
        assertTrue(event.getNextRetryAt().isAfter(Instant.now()));
        assertNull(event.getClaimedBy());
        assertNull(event.getLeaseExpiresAt());
    }

    @Test
    void marksEventFailedAtMaximumAttempts() {
        properties.setMaxAttempts(1);
        OutboxEvent event = processingEvent("claim-2");
        when(outboxEventRepository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        OutboxEventStateService.FailureResult result = stateService.recordFailure(
                event.getEventId(),
                "claim-2",
                "broker unavailable"
        );

        assertTrue(result.updated());
        assertTrue(result.exhausted());
        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNull(event.getClaimedBy());
        assertNull(event.getLeaseExpiresAt());
    }

    @Test
    void ignoresAcknowledgmentFromAClaimThatNoLongerOwnsTheEvent() {
        OutboxEvent event = processingEvent("new-claim");
        when(outboxEventRepository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        assertFalse(stateService.markPublished(event.getEventId(), "old-claim"));

        assertEquals(OutboxEventStatus.PROCESSING, event.getStatus());
        assertNull(event.getPublishedAt());
    }

    @Test
    void marksPublishedOnlyForCurrentClaimOwner() {
        OutboxEvent event = processingEvent("claim-3");
        when(outboxEventRepository.findByEventIdForUpdate(event.getEventId())).thenReturn(Optional.of(event));

        assertTrue(stateService.markPublished(event.getEventId(), "claim-3"));

        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertTrue(event.getPublishedAt().isBefore(Instant.now().plusSeconds(1)));
        assertNull(event.getClaimedBy());
        assertNull(event.getLeaseExpiresAt());
    }

    private OutboxEvent processingEvent(String claimant) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId("evt-state");
        event.setStatus(OutboxEventStatus.PROCESSING);
        event.setRetryCount(0);
        event.setClaimedBy(claimant);
        event.setLeaseExpiresAt(Instant.now().plusSeconds(60));
        event.setNextRetryAt(Instant.now());
        return event;
    }
}
