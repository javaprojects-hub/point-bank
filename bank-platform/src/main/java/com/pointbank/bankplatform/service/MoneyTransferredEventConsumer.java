package com.pointbank.bankplatform.service;

import com.pointbank.bankplatform.constant.NotificationChannel;
import com.pointbank.bankplatform.dto.MoneyTransferredEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class MoneyTransferredEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoneyTransferredEventConsumer.class);
    private static final String METRIC_NAME = "pointbank.outbox.consumer.events";

    private final ObjectMapper objectMapper;
    private final TransferNotificationService transferNotificationService;
    private final MeterRegistry meterRegistry;

    public MoneyTransferredEventConsumer(
            ObjectMapper objectMapper,
            TransferNotificationService transferNotificationService,
            MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.transferNotificationService = transferNotificationService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${pointbank.outbox.topic}",
            groupId = "${pointbank.outbox.consumer.group-id:pointbank-money-transfer-consumer}",
            autoStartup = "${pointbank.outbox.consumer.enabled:true}"
    )
    public void onMessage(String payload) {
        MoneyTransferredEvent event;
        try {
            event = objectMapper.readValue(payload, MoneyTransferredEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to deserialize money-transferred event", exception);
        }

        if (transferNotificationService.process(event)) {
            meterRegistry.counter(METRIC_NAME, "outcome", "processed").increment();
            meterRegistry.counter(METRIC_NAME, "outcome", "notification_requested")
                    .increment(NotificationChannel.values().length);
            LOGGER.info("Transfer event processed: eventId={}", event.eventId());
        } else {
            meterRegistry.counter(METRIC_NAME, "outcome", "duplicate").increment();
            LOGGER.debug("Duplicate transfer event ignored: eventId={}", event.eventId());
        }
    }
}
