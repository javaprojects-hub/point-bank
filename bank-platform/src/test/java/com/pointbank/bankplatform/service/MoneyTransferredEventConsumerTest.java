package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pointbank.bankplatform.constant.OutboxEventType;
import com.pointbank.bankplatform.dto.MoneyTransferredEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MoneyTransferredEventConsumerTest {

    @Mock
    private TransferNotificationService transferNotificationService;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private MoneyTransferredEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        meterRegistry = new SimpleMeterRegistry();
        consumer = new MoneyTransferredEventConsumer(
                objectMapper,
                transferNotificationService,
                meterRegistry
        );
    }

    @Test
    void processesMessageAndRecordsConsumerMetrics() throws Exception {
        MoneyTransferredEvent event = validEvent();
        when(transferNotificationService.process(event)).thenReturn(true);

        consumer.onMessage(objectMapper.writeValueAsString(event));

        verify(transferNotificationService).process(event);
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0,
                meterRegistry.counter("pointbank.outbox.consumer.events", "outcome", "processed").count()
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                2.0,
                meterRegistry.counter("pointbank.outbox.consumer.events", "outcome", "notification_requested").count()
        );
    }

    @Test
    void ignoresAndCountsDuplicateEvents() throws Exception {
        MoneyTransferredEvent event = validEvent();
        when(transferNotificationService.process(event)).thenReturn(false);

        consumer.onMessage(objectMapper.writeValueAsString(event));

        verify(transferNotificationService).process(event);
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0,
                meterRegistry.counter("pointbank.outbox.consumer.events", "outcome", "duplicate").count()
        );
    }

    @Test
    void malformedMessageFailsForConfiguredRetryAndDeadLetterHandling() {
        assertThrows(IllegalArgumentException.class, () -> consumer.onMessage("{not-json"));

        verify(transferNotificationService, never()).process(org.mockito.ArgumentMatchers.any());
    }

    private MoneyTransferredEvent validEvent() {
        return new MoneyTransferredEvent(
                "event-" + UUID.randomUUID(),
                OutboxEventType.MONEY_TRANSFERRED,
                "transfer-" + UUID.randomUUID(),
                "source-account",
                "destination-account",
                new BigDecimal("25.00"),
                "INR",
                Instant.now(),
                UUID.randomUUID().toString()
        );
    }
}
