package com.pointbank.bankplatform.entity;

import java.time.Instant;
import java.util.UUID;

import com.pointbank.bankplatform.constant.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "notification_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_event_channel",
                columnNames = {"event_id", "notification_channel"}
        ),
        indexes = @Index(name = "idx_notification_transfer_reference", columnList = "transfer_reference")
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "transfer_reference", nullable = false, updatable = false, length = 36)
    private String transferReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false, length = 32, updatable = false)
    private NotificationChannel channel;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
}
