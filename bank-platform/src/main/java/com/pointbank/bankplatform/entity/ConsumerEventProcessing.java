package com.pointbank.bankplatform.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "consumer_event_processing",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consumer_processed_event_id",
                columnNames = "event_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ConsumerEventProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "transfer_reference", nullable = false, updatable = false, length = 36)
    private String transferReference;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
