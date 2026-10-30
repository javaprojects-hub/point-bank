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
@ConfigurationProperties(prefix = "pointbank.outbox")
public class OutboxPublisherProperties {

    private boolean enabled = true;

    @NotBlank
    private String topic = "pointbank.money-transferred.v1";

    @Min(1)
    private int batchSize = 10;

    @NotNull
    private Duration pollInterval = Duration.ofSeconds(1);

    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(2);

    @NotNull
    private Duration acknowledgmentTimeout = Duration.ofSeconds(10);

    @Min(1)
    private int maxAttempts = 10;

    @NotNull
    private Duration baseRetryDelay = Duration.ofSeconds(1);

    @NotNull
    private Duration maxRetryDelay = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getAcknowledgmentTimeout() {
        return acknowledgmentTimeout;
    }

    public void setAcknowledgmentTimeout(Duration acknowledgmentTimeout) {
        this.acknowledgmentTimeout = acknowledgmentTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBaseRetryDelay() {
        return baseRetryDelay;
    }

    public void setBaseRetryDelay(Duration baseRetryDelay) {
        this.baseRetryDelay = baseRetryDelay;
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    public Duration retryDelayAfter(int failureCount) {
        long baseMillis = baseRetryDelay.toMillis();
        long maxMillis = maxRetryDelay.toMillis();
        long delayMillis = Math.min(baseMillis, maxMillis);
        for (int attempt = 1; attempt < failureCount && delayMillis < maxMillis; attempt++) {
            delayMillis = delayMillis > maxMillis / 2 ? maxMillis : delayMillis * 2;
        }
        return Duration.ofMillis(delayMillis);
    }

    @AssertTrue(message = "outbox durations must be positive and max-retry-delay must be >= base-retry-delay")
    public boolean isConfigurationValid() {
        return isPositive(pollInterval)
                && isPositive(leaseDuration)
                && isPositive(acknowledgmentTimeout)
                && isPositive(baseRetryDelay)
                && isPositive(maxRetryDelay)
                && baseRetryDelay.toMillis() > 0
                && maxRetryDelay.toMillis() > 0
                && maxRetryDelay.compareTo(baseRetryDelay) >= 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
