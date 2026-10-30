package com.pointbank.bankplatform.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfiguration {

    private static final String METRIC_NAME = "pointbank.outbox.consumer.events";

    @Bean
    public DefaultErrorHandler kafkaConsumerErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxConsumerProperties properties,
            MeterRegistry meterRegistry
    ) {
        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(properties.getDeadLetterTopic(), record.partition())
        );
        deadLetterPublisher.setFailIfSendResultIsError(true);
        deadLetterPublisher.setWaitForSendResultTimeout(properties.getAcknowledgmentTimeout());

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            deadLetterPublisher.accept(record, exception);
            meterRegistry.counter(METRIC_NAME, "outcome", "dead_lettered").increment();
        };
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(properties.getRetryInterval().toMillis(), properties.getMaxRetries())
        );
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                meterRegistry.counter(METRIC_NAME, "outcome", "retry").increment());
        return errorHandler;
    }
}
