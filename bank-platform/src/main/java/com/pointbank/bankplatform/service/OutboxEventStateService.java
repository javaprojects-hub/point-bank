package com.pointbank.bankplatform.service;

import java.time.Instant;
import java.util.Optional;

import com.pointbank.bankplatform.config.OutboxPublisherProperties;
import com.pointbank.bankplatform.constant.OutboxEventStatus;
import com.pointbank.bankplatform.entity.OutboxEvent;
import com.pointbank.bankplatform.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventStateService {

    private static final int MAX_ERROR_LENGTH = 2_000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisherProperties properties;

    public OutboxEventStateService(
            OutboxEventRepository outboxEventRepository,
            OutboxPublisherProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
    }

    @Transactional
    public boolean markPublished(String eventId, String claimant) {
        Optional<OutboxEvent> found = outboxEventRepository.findByEventIdForUpdate(eventId);
        if (found.isEmpty()) {
            return false;
        }
        OutboxEvent event = found.get();
        if (!isClaimedBy(event, claimant)) {
            return false;
        }
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
        OutboxClaimService.clearClaim(event);
        return true;
    }

    @Transactional
    public FailureResult recordFailure(String eventId, String claimant, String error) {
        Optional<OutboxEvent> found = outboxEventRepository.findByEventIdForUpdate(eventId);
        if (found.isEmpty()) {
            return new FailureResult(false, false);
        }
        OutboxEvent event = found.get();
        if (!isClaimedBy(event, claimant)) {
            return new FailureResult(false, false);
        }

        int failureCount = event.getRetryCount() + 1;
        boolean exhausted = failureCount >= properties.getMaxAttempts();
        event.setRetryCount(failureCount);
        event.setLastError(truncate(error));
        event.setStatus(exhausted ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING);
        if (!exhausted) {
            event.setNextRetryAt(Instant.now().plus(properties.retryDelayAfter(failureCount)));
        }
        OutboxClaimService.clearClaim(event);
        return new FailureResult(true, exhausted);
    }

    private boolean isClaimedBy(OutboxEvent event, String claimant) {
        return event.getStatus() == OutboxEventStatus.PROCESSING
                && claimant.equals(event.getClaimedBy());
    }

    private String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "Kafka publish failed";
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public record FailureResult(boolean updated, boolean exhausted) {
    }
}
