package com.pointbank.bankplatform.config;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "pointbank.outbox.consumer")
public class OutboxConsumerProperties {

    private boolean enabled = true;

    @NotBlank
    private String groupId = "pointbank-money-transfer-consumer";

    @NotBlank
    private String deadLetterTopic = "pointbank.money-transferred.v1.dlt";

    @Min(0)
    private long maxRetries = 5;

    @NotNull
    private Duration retryInterval = Duration.ofSeconds(2);

    @NotNull
    private Duration acknowledgmentTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getDeadLetterTopic() {
        return deadLetterTopic;
    }

    public void setDeadLetterTopic(String deadLetterTopic) {
        this.deadLetterTopic = deadLetterTopic;
    }

    public long getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(long maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Duration retryInterval) {
        this.retryInterval = retryInterval;
    }

    public Duration getAcknowledgmentTimeout() {
        return acknowledgmentTimeout;
    }

    public void setAcknowledgmentTimeout(Duration acknowledgmentTimeout) {
        this.acknowledgmentTimeout = acknowledgmentTimeout;
    }

    @AssertTrue(message = "consumer retry interval and acknowledgment timeout must be positive")
    public boolean isConfigurationValid() {
        return isPositive(retryInterval)
                && isPositive(acknowledgmentTimeout)
                && retryInterval.toMillis() > 0
                && acknowledgmentTimeout.toMillis() > 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
