package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.pointbank.bankplatform.config.OutboxPublisherProperties;
import com.pointbank.bankplatform.constant.OutboxEventStatus;
import com.pointbank.bankplatform.entity.OutboxEvent;
import com.pointbank.bankplatform.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxClaimService claimService;

    @Test
    void claimsEligibleEventsAndSchedulesExpiredLeasesForRetry() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        claimService = new OutboxClaimService(outboxEventRepository, properties);
        OutboxEvent expired = event(OutboxEventStatus.PROCESSING);
        expired.setRetryCount(1);
        expired.setClaimedBy("dead-instance");
        expired.setLeaseExpiresAt(Instant.now().minusSeconds(1));
        OutboxEvent pending = event(OutboxEventStatus.PENDING);

        when(outboxEventRepository.findExpiredProcessingForUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(properties.getBatchSize())
        )).thenReturn(List.of(expired));
        when(outboxEventRepository.findPendingEligibleForUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(properties.getBatchSize())
        )).thenReturn(List.of(pending));

        List<ClaimedOutboxEvent> claimed = claimService.claimBatch("active-instance");

        assertEquals(1, claimed.size());
        assertEquals(pending.getEventId(), claimed.getFirst().eventId());
        assertEquals(OutboxEventStatus.PROCESSING, pending.getStatus());
        assertEquals("active-instance", pending.getClaimedBy());
        assertTrue(pending.getLeaseExpiresAt().isAfter(Instant.now()));
        assertEquals(OutboxEventStatus.PENDING, expired.getStatus());
        assertEquals(2, expired.getRetryCount());
        assertNull(expired.getClaimedBy());
        assertNull(expired.getLeaseExpiresAt());
        assertTrue(expired.getNextRetryAt().isAfter(Instant.now()));
    }

    @Test
    void marksExpiredLeaseFailedAfterMaximumAttempts() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        claimService = new OutboxClaimService(outboxEventRepository, properties);
        OutboxEvent expired = event(OutboxEventStatus.PROCESSING);
        expired.setRetryCount(properties.getMaxAttempts() - 1);
        expired.setLeaseExpiresAt(Instant.now().minusSeconds(1));

        when(outboxEventRepository.findExpiredProcessingForUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(properties.getBatchSize())
        )).thenReturn(List.of(expired));
        when(outboxEventRepository.findPendingEligibleForUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(properties.getBatchSize())
        )).thenReturn(List.of());

        assertEquals(List.of(), claimService.claimBatch("active-instance"));
        assertEquals(OutboxEventStatus.FAILED, expired.getStatus());
        assertEquals(properties.getMaxAttempts(), expired.getRetryCount());
        assertTrue(expired.getLastError().contains("lease expired"));
        verify(outboxEventRepository).findPendingEligibleForUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(properties.getBatchSize())
        );
    }

    private OutboxEvent event(OutboxEventStatus status) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId("evt-" + System.nanoTime());
        event.setPayload("{\"eventId\":\"test\"}");
        event.setStatus(status);
        event.setCreatedAt(Instant.now());
        event.setNextRetryAt(Instant.now());
        return event;
    }
}
