package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pointbank.bankplatform.constant.NotificationChannel;
import com.pointbank.bankplatform.constant.OutboxEventType;
import com.pointbank.bankplatform.dto.MoneyTransferredEvent;
import com.pointbank.bankplatform.entity.ConsumerEventProcessing;
import com.pointbank.bankplatform.entity.NotificationRequest;
import com.pointbank.bankplatform.repository.ConsumerEventProcessingRepository;
import com.pointbank.bankplatform.repository.NotificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferNotificationServiceTest {

    @Mock
    private ConsumerEventProcessingRepository consumerEventProcessingRepository;

    @Mock
    private NotificationRequestRepository notificationRequestRepository;

    private TransferNotificationService service;

    @BeforeEach
    void setUp() {
        service = new TransferNotificationService(
                consumerEventProcessingRepository,
                notificationRequestRepository
        );
    }

    @Test
    void recordsProcessingAndOneSimulatedRequestForEachChannel() {
        MoneyTransferredEvent event = validEvent();
        when(consumerEventProcessingRepository.existsByEventId(event.eventId())).thenReturn(false);

        assertTrue(service.process(event));

        ArgumentCaptor<ConsumerEventProcessing> processed = ArgumentCaptor.forClass(ConsumerEventProcessing.class);
        verify(consumerEventProcessingRepository).save(processed.capture());
        assertEquals(event.eventId(), processed.getValue().getEventId());
        assertEquals(event.transferReference(), processed.getValue().getTransferReference());

        ArgumentCaptor<List<NotificationRequest>> requests = ArgumentCaptor.captor();
        verify(notificationRequestRepository).saveAll(requests.capture());
        assertEquals(NotificationChannel.values().length, requests.getValue().size());
        assertEquals(
                List.of(NotificationChannel.values()),
                requests.getValue().stream().map(NotificationRequest::getChannel).toList()
        );
        assertTrue(requests.getValue().stream().allMatch(
                request -> request.getEventId().equals(event.eventId())
                        && request.getTransferReference().equals(event.transferReference())
        ));
    }

    @Test
    void duplicateEventDoesNotCreateAdditionalRecordsOrNotifications() {
        MoneyTransferredEvent event = validEvent();
        when(consumerEventProcessingRepository.existsByEventId(event.eventId())).thenReturn(true);

        assertFalse(service.process(event));

        verify(consumerEventProcessingRepository, never()).save(any(ConsumerEventProcessing.class));
        verify(notificationRequestRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsInvalidTransferPayloadBeforePersistence() {
        MoneyTransferredEvent event = new MoneyTransferredEvent(
                "event-invalid",
                OutboxEventType.MONEY_TRANSFERRED,
                "transfer-invalid",
                "source",
                "destination",
                BigDecimal.ZERO,
                "INR",
                Instant.now(),
                UUID.randomUUID().toString()
        );

        assertThrows(IllegalArgumentException.class, () -> service.process(event));

        verify(consumerEventProcessingRepository, never()).existsByEventId(any());
        verify(consumerEventProcessingRepository, never()).save(any(ConsumerEventProcessing.class));
        verify(notificationRequestRepository, never()).saveAll(anyList());
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
