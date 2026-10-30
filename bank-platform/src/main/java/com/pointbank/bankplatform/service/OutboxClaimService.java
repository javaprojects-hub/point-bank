package com.pointbank.bankplatform.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.pointbank.bankplatform.config.OutboxPublisherProperties;
import com.pointbank.bankplatform.constant.OutboxEventStatus;
import com.pointbank.bankplatform.entity.OutboxEvent;
import com.pointbank.bankplatform.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {

    private static final String EXPIRED_LEASE_ERROR = "Processing lease expired before Kafka acknowledgment";

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisherProperties properties;

    public OutboxClaimService(
            OutboxEventRepository outboxEventRepository,
            OutboxPublisherProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimBatch(String claimant) {
        Instant now = Instant.now();
        recoverExpiredLeases(now);

        List<OutboxEvent> eligible = outboxEventRepository.findPendingEligibleForUpdate(
                now,
                properties.getBatchSize()
        );
        List<ClaimedOutboxEvent> claimed = new ArrayList<>(eligible.size());
        Instant leaseExpiration = now.plus(properties.getLeaseDuration());
        for (OutboxEvent event : eligible) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setClaimedBy(claimant);
            event.setLeaseExpiresAt(leaseExpiration);
            claimed.add(new ClaimedOutboxEvent(event.getEventId(), event.getPayload()));
        }
        return claimed;
    }

    private void recoverExpiredLeases(Instant now) {
        List<OutboxEvent> expired = outboxEventRepository.findExpiredProcessingForUpdate(
                now,
                properties.getBatchSize()
        );
        for (OutboxEvent event : expired) {
            int failureCount = event.getRetryCount() + 1;
            event.setRetryCount(failureCount);
            event.setLastError(EXPIRED_LEASE_ERROR);
            clearClaim(event);
            if (failureCount >= properties.getMaxAttempts()) {
                event.setStatus(OutboxEventStatus.FAILED);
            } else {
                event.setStatus(OutboxEventStatus.PENDING);
                event.setNextRetryAt(now.plus(properties.retryDelayAfter(failureCount)));
            }
        }
    }

    static void clearClaim(OutboxEvent event) {
        event.setClaimedBy(null);
        event.setLeaseExpiresAt(null);
    }
}
