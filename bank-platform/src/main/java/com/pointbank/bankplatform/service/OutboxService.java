package com.pointbank.bankplatform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pointbank.bankplatform.config.RequestCorrelationFilter;
import com.pointbank.bankplatform.constant.OutboxEventStatus;
import com.pointbank.bankplatform.constant.OutboxEventType;
import com.pointbank.bankplatform.dto.MoneyTransferredEvent;
import com.pointbank.bankplatform.entity.OutboxEvent;
import com.pointbank.bankplatform.exception.OutboxEventSerializationException;
import com.pointbank.bankplatform.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxService.class);
    private static final String AGGREGATE_TYPE = "ACCOUNT_TRANSFER";

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(ObjectMapper objectMapper, OutboxEventRepository outboxEventRepository) {
        this.objectMapper = objectMapper;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMoneyTransferred(
            String transferReference,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String currency,
            Instant occurredAt
    ) {
        String eventId = UUID.randomUUID().toString();
        String correlationId = MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MoneyTransferredEvent payload = new MoneyTransferredEvent(
                eventId,
                OutboxEventType.MONEY_TRANSFERRED,
                transferReference,
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                currency,
                occurredAt,
                correlationId
        );

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventId(eventId);
        outboxEvent.setAggregateType(AGGREGATE_TYPE);
        outboxEvent.setAggregateId(transferReference);
        outboxEvent.setEventType(payload.eventType());
        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JacksonException exception) {
            throw new OutboxEventSerializationException(exception);
        }
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setCreatedAt(occurredAt);
        outboxEvent.setNextRetryAt(occurredAt);
        outboxEventRepository.save(outboxEvent);

        String eventCorrelationId = correlationId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                LOGGER.info(
                        "Money transfer event added to outbox: eventId={}, transferReference={}, correlationId={}",
                        eventId,
                        transferReference,
                        eventCorrelationId
                );
            }
        });
    }
}
