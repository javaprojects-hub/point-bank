package com.pointbank.bankplatform.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

@Component
@Validated
@ConfigurationProperties(prefix = "pointbank.account.transaction-limits")
public class DailyTransactionLimitProperties {

    @NotNull
    private Duration rollingWindow = Duration.ofHours(24);

    @Valid
    @NotNull
    private DirectionLimits savings = new DirectionLimits(
            new BigDecimal("99999.99"),
            new BigDecimal("99999.99")
    );

    @Valid
    @NotNull
    private DirectionLimits salary = new DirectionLimits(
            new BigDecimal("300000.00"),
            new BigDecimal("300000.00")
    );

    @Valid
    @NotNull
    private DirectionLimits current = new DirectionLimits(null, null);

    public Duration getRollingWindow() {
        return rollingWindow;
    }

    public void setRollingWindow(Duration rollingWindow) {
        this.rollingWindow = rollingWindow;
    }

    @AssertTrue(message = "rolling-window must be greater than zero")
    public boolean isRollingWindowPositive() {
        return rollingWindow != null && !rollingWindow.isZero() && !rollingWindow.isNegative();
    }

    public DirectionLimits getSavings() {
        return savings;
    }

    public void setSavings(DirectionLimits savings) {
        this.savings = savings;
    }

    public DirectionLimits getSalary() {
        return salary;
    }

    public void setSalary(DirectionLimits salary) {
        this.salary = salary;
    }

    public DirectionLimits getCurrent() {
        return current;
    }

    public void setCurrent(DirectionLimits current) {
        this.current = current;
    }

    public static class DirectionLimits {

        @DecimalMin(value = "0.00")
        private BigDecimal deposits;

        @DecimalMin(value = "0.00")
        private BigDecimal withdrawals;

        public DirectionLimits() {
        }

        public DirectionLimits(BigDecimal deposits, BigDecimal withdrawals) {
            this.deposits = deposits;
            this.withdrawals = withdrawals;
        }

        public BigDecimal getDeposits() {
            return deposits;
        }

        public void setDeposits(BigDecimal deposits) {
            this.deposits = deposits;
        }

        public BigDecimal getWithdrawals() {
            return withdrawals;
        }

        public void setWithdrawals(BigDecimal withdrawals) {
            this.withdrawals = withdrawals;
        }
    }
}
