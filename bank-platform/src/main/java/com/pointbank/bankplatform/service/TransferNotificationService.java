package com.pointbank.bankplatform.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.pointbank.bankplatform.constant.NotificationChannel;
import com.pointbank.bankplatform.constant.OutboxEventType;
import com.pointbank.bankplatform.dto.MoneyTransferredEvent;
import com.pointbank.bankplatform.entity.ConsumerEventProcessing;
import com.pointbank.bankplatform.entity.NotificationRequest;
import com.pointbank.bankplatform.repository.ConsumerEventProcessingRepository;
import com.pointbank.bankplatform.repository.NotificationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferNotificationService {

    private final ConsumerEventProcessingRepository consumerEventProcessingRepository;
    private final NotificationRequestRepository notificationRequestRepository;

    public TransferNotificationService(
            ConsumerEventProcessingRepository consumerEventProcessingRepository,
            NotificationRequestRepository notificationRequestRepository
    ) {
        this.consumerEventProcessingRepository = consumerEventProcessingRepository;
        this.notificationRequestRepository = notificationRequestRepository;
    }

    @Transactional
    public boolean process(MoneyTransferredEvent event) {
        validate(event);
        if (consumerEventProcessingRepository.existsByEventId(event.eventId())) {
            return false;
        }

        ConsumerEventProcessing processing = new ConsumerEventProcessing();
        processing.setEventId(event.eventId());
        processing.setTransferReference(event.transferReference());
        processing.setProcessedAt(Instant.now());
        consumerEventProcessingRepository.save(processing);

        Instant requestedAt = Instant.now();
        List<NotificationRequest> requests = new ArrayList<>(NotificationChannel.values().length);
        for (NotificationChannel channel : NotificationChannel.values()) {
            NotificationRequest request = new NotificationRequest();
            request.setEventId(event.eventId());
            request.setTransferReference(event.transferReference());
            request.setChannel(channel);
            request.setRequestedAt(requestedAt);
            requests.add(request);
        }
        notificationRequestRepository.saveAll(requests);
        return true;
    }

    private void validate(MoneyTransferredEvent event) {
        if (event == null
                || isBlank(event.eventId())
                || event.eventType() != OutboxEventType.MONEY_TRANSFERRED
                || isBlank(event.transferReference())
                || isBlank(event.sourceAccountNumber())
                || isBlank(event.destinationAccountNumber())
                || event.amount() == null
                || event.amount().signum() <= 0
                || isBlank(event.currency())
                || event.occurredAt() == null
                || isBlank(event.correlationId())) {
            throw new IllegalArgumentException("Invalid money-transferred event");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
