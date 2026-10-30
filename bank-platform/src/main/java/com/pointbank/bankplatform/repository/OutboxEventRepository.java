package com.pointbank.bankplatform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pointbank.bankplatform.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    Optional<OutboxEvent> findByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM OutboxEvent event WHERE event.eventId = :eventId")
    Optional<OutboxEvent> findByEventIdForUpdate(@Param("eventId") String eventId);

    List<OutboxEvent> findAllByAggregateId(String aggregateId);

    @Query(value = """
            SELECT *
            FROM outbox_event
            WHERE status = 'PROCESSING'
              AND (lease_expires_at IS NULL OR lease_expires_at <= :now)
            ORDER BY lease_expires_at, created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findExpiredProcessingForUpdate(
            @Param("now") java.time.Instant now,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
            SELECT *
            FROM outbox_event
            WHERE status = 'PENDING'
              AND next_retry_at <= :now
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingEligibleForUpdate(
            @Param("now") java.time.Instant now,
            @Param("batchSize") int batchSize
    );
}
