package com.pointbank.bankplatform.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.pointbank.bankplatform.constant.OutboxEventType;

public record MoneyTransferredEvent(
        String eventId,
        OutboxEventType eventType,
        String transferReference,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        String correlationId
) {
}
