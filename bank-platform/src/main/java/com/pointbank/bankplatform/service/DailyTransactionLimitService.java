package com.pointbank.bankplatform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pointbank.bankplatform.config.DailyTransactionLimitProperties;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountTransactionType;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.DailyTransactionLimitDetails;
import com.pointbank.bankplatform.entity.Account;
import com.pointbank.bankplatform.exception.DailyTransactionLimitExceededException;
import com.pointbank.bankplatform.repository.AccountTransactionRepository;

@Service
public class DailyTransactionLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyTransactionLimitService.class);

    private final AccountTransactionRepository transactionRepository;
    private final DailyTransactionLimitProperties properties;

    public DailyTransactionLimitService(
            AccountTransactionRepository transactionRepository,
            DailyTransactionLimitProperties properties
    ) {
        this.transactionRepository = transactionRepository;
        this.properties = properties;
    }

    public void assertWithinLimit(
            Account account,
            AccountTransactionType type,
            BigDecimal amount,
            Instant now
    ) {
        AccountTransactionType limitType = switch (type) {
            case TRANSFER_OUT -> AccountTransactionType.WITHDRAWAL;
            case TRANSFER_IN -> AccountTransactionType.DEPOSIT;
            default -> type;
        };
        Optional<BigDecimal> configuredLimit = configuredLimit(account.getAccountType(), limitType);
        if (configuredLimit.isEmpty()) {
            return;
        }

        BigDecimal limit = configuredLimit.get();
        Instant windowStart = now.minus(properties.getRollingWindow());
        List<AccountTransactionType> countedTypes = switch (type) {
            case TRANSFER_OUT -> List.of(AccountTransactionType.WITHDRAWAL, AccountTransactionType.TRANSFER_OUT);
            case TRANSFER_IN -> List.of(AccountTransactionType.DEPOSIT, AccountTransactionType.TRANSFER_IN);
            default -> List.of(type);
        };
        BigDecimal postedTotal = countedTypes.stream()
                .map(countedType -> transactionRepository.sumPostedAmount(
                        account.getId(),
                        countedType,
                        AccountTransactionStatus.COMPLETED,
                        windowStart,
                        now
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (postedTotal.add(amount).compareTo(limit) > 0) {
            LOGGER.warn(
                    "Daily transaction limit rejected: accountType={}, direction={}, windowSeconds={}",
                    account.getAccountType(),
                    type,
                    properties.getRollingWindow().toSeconds()
            );
            throw new DailyTransactionLimitExceededException(new DailyTransactionLimitDetails(
                    account.getAccountType(),
                    type,
                    limit,
                    postedTotal,
                    amount,
                    limit.subtract(postedTotal).max(BigDecimal.ZERO),
                    account.getCurrencyCode()
            ));
        }
    }

    private Optional<BigDecimal> configuredLimit(AccountType accountType, AccountTransactionType type) {
        DailyTransactionLimitProperties.DirectionLimits accountTypeLimits = switch (accountType) {
            case SAVINGS -> properties.getSavings();
            case SALARY -> properties.getSalary();
            case CURRENT -> properties.getCurrent();
        };
        BigDecimal limit = type == AccountTransactionType.DEPOSIT
                ? accountTypeLimits.getDeposits()
                : accountTypeLimits.getWithdrawals();
        return Optional.ofNullable(limit);
    }
}
