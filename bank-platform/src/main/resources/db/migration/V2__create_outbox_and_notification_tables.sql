CREATE TABLE outbox_event (
    id BINARY(16) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) DEFAULT NULL,
    next_retry_at DATETIME(6) NOT NULL,
    last_error TEXT DEFAULT NULL,
    claimed_by VARCHAR(100) DEFAULT NULL,
    lease_expires_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_eligible (status, next_retry_at, created_at),
    KEY idx_outbox_expired_lease (status, lease_expires_at),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    CONSTRAINT chk_outbox_retry_count CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consumer_event_processing (
    id BINARY(16) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    transfer_reference VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumer_processed_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_request (
    id BINARY(16) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    transfer_reference VARCHAR(36) NOT NULL,
    notification_channel VARCHAR(32) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event_channel (event_id, notification_channel),
    KEY idx_notification_transfer_reference (transfer_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
