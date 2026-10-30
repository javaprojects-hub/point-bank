package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.constant.OutboxEventType;
import com.pointbank.bankplatform.dto.MoneyTransferredEvent;
import com.pointbank.bankplatform.entity.Account;
import com.pointbank.bankplatform.entity.AccountTransaction;
import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.repository.ConsumerEventProcessingRepository;
import com.pointbank.bankplatform.repository.NotificationRequestRepository;
import com.pointbank.bankplatform.entity.OutboxEvent;
import com.pointbank.bankplatform.repository.AccountRepository;
import com.pointbank.bankplatform.repository.AccountTransactionRepository;
import com.pointbank.bankplatform.repository.CustomerRepository;
import com.pointbank.bankplatform.repository.OutboxEventRepository;
import com.pointbank.bankplatform.utility.AccountIdentifierGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.security.jwt.issuer-uri=",
                "app.security.jwt.audience="
        }
)
@TestPropertySource(properties = {
        "spring.datasource.url=${POINTBANK_TEST_DB_URL}",
        "spring.datasource.username=${POINTBANK_TEST_DB_USERNAME:root}"
})
@EnabledIfEnvironmentVariable(named = "POINTBANK_TEST_DB_URL", matches = ".+")
class AccountTransferOutboxIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountIdentifierGenerator identifierGenerator;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ConsumerEventProcessingRepository consumerEventProcessingRepository;

    @Autowired
    private NotificationRequestRepository notificationRequestRepository;

    @Autowired
    private TransferNotificationService transferNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<String> transferReferences = new ArrayList<>();
    private final List<String> consumerEventIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String eventId : consumerEventIds) {
            notificationRequestRepository.deleteAllByEventId(eventId);
            consumerEventProcessingRepository.deleteAllByEventId(eventId);
        }
        for (String transferReference : transferReferences) {
            outboxEventRepository.deleteAll(outboxEventRepository.findAllByAggregateId(transferReference));
        }
        for (UUID accountId : accountIds) {
            List<AccountTransaction> transactions = transactionRepository
                    .findAllByAccount_Id(accountId, PageRequest.of(0, 100))
                    .getContent();
            transactionRepository.deleteAll(transactions);
            accountRepository.deleteById(accountId);
        }
        customerRepository.deleteAllById(customerIds);
    }

    @Test
    void consumerCreatesSimulatedNotificationsOnceForRedeliveredEvent() {
        String eventId = UUID.randomUUID().toString();
        String transferReference = UUID.randomUUID().toString();
        consumerEventIds.add(eventId);
        MoneyTransferredEvent event = new MoneyTransferredEvent(
                eventId,
                OutboxEventType.MONEY_TRANSFERRED,
                transferReference,
                "source-account",
                "destination-account",
                new BigDecimal("40.00"),
                "INR",
                java.time.Instant.now(),
                UUID.randomUUID().toString()
        );

        assertTrue(transferNotificationService.process(event));
        assertFalse(transferNotificationService.process(event));

        assertTrue(consumerEventProcessingRepository.existsByEventId(eventId));
        assertEquals(2, notificationRequestRepository.findAllByEventId(eventId).size());
    }

    @Test
    void successfulTransferCreatesOneOutboxEventAndIdempotentReplayDoesNotDuplicateIt() throws Exception {
        Account source = createActiveAccount(new BigDecimal("500.00"));
        Account destination = createActiveAccount(BigDecimal.ZERO);

        var firstResponse = accountService.transfer(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                null,
                "integration-transfer-success",
                "integration-test",
                new BigDecimal("100.00")
        );
        transferReferences.add(firstResponse.transferReference());

        var replayResponse = accountService.transfer(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                null,
                "integration-transfer-success",
                "integration-test",
                new BigDecimal("100.00")
        );

        assertEquals(firstResponse.transferReference(), replayResponse.transferReference());
        List<OutboxEvent> events = outboxEventRepository.findAllByAggregateId(firstResponse.transferReference());
        assertEquals(1, events.size());
        assertEquals(AccountTransactionStatus.COMPLETED, firstResponse.debit().status());
        assertEquals(new BigDecimal("400.00"), accountRepository
                .findByAccountNumber(source.getAccountNumber()).orElseThrow().getAvailableBalance());
        assertEquals(new BigDecimal("100.00"), accountRepository
                .findByAccountNumber(destination.getAccountNumber()).orElseThrow().getAvailableBalance());

        MoneyTransferredEvent event = objectMapper.readValue(events.getFirst().getPayload(), MoneyTransferredEvent.class);
        assertEquals(events.getFirst().getEventId(), event.eventId());
        assertEquals(firstResponse.transferReference(), event.transferReference());
        assertEquals(source.getAccountNumber(), event.sourceAccountNumber());
        assertEquals(destination.getAccountNumber(), event.destinationAccountNumber());
        assertEquals(0, new BigDecimal("100.00").compareTo(event.amount()));
        assertEquals("INR", event.currency());
        assertFalse(event.correlationId().isBlank());
    }

    @Test
    void failedTransferDoesNotCreateAnOutboxEvent() {
        Account source = createActiveAccount(new BigDecimal("50.00"));
        Account destination = createActiveAccount(BigDecimal.ZERO);

        var response = accountService.transfer(
                source.getAccountNumber(),
                destination.getAccountNumber(),
                null,
                "integration-transfer-failed",
                "integration-test",
                new BigDecimal("100.00")
        );

        assertEquals(AccountTransactionStatus.FAILED, response.debit().status());
        assertEquals(List.of(), outboxEventRepository.findAllByAggregateId(response.transferReference()));
        assertEquals(new BigDecimal("50.00"), accountRepository
                .findByAccountNumber(source.getAccountNumber()).orElseThrow().getAvailableBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), accountRepository
                .findByAccountNumber(destination.getAccountNumber()).orElseThrow().getAvailableBalance());
    }

    @Test
    void rollingBackTransferAlsoRollsBackItsOutboxEventAndBalanceChanges() {
        Account source = createActiveAccount(new BigDecimal("500.00"));
        Account destination = createActiveAccount(BigDecimal.ZERO);
        long outboxCountBefore = outboxEventRepository.count();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    var response = accountService.transfer(
                            source.getAccountNumber(),
                            destination.getAccountNumber(),
                            null,
                            "integration-transfer-rollback",
                            "integration-test",
                            new BigDecimal("100.00")
                    );
                    transferReferences.add(response.transferReference());
                    throw new ForcedRollbackException();
                })
        );

        assertEquals(outboxCountBefore, outboxEventRepository.count());
        assertFalse(transactionRepository
                .findByAccount_IdAndIdempotencyKey(source.getId(), "integration-transfer-rollback").isPresent());
        assertEquals(new BigDecimal("500.00"), accountRepository
                .findByAccountNumber(source.getAccountNumber()).orElseThrow().getAvailableBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), accountRepository
                .findByAccountNumber(destination.getAccountNumber()).orElseThrow().getAvailableBalance());
    }

    private Account createActiveAccount(BigDecimal balance) {
        Customer customer = new Customer();
        customer.setCustomerId(10_000_000L + Math.floorMod(System.nanoTime(), 89_000_000L));
        customer.setFirstName("Outbox");
        customer.setLastName("Integration");
        customer.setGender("Unspecified");
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        customer.setMobileNumber("9" + String.format("%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L)));
        customer.setEmail("outbox-" + suffix + "@example.test");
        customer.setKycStatus(KycStatus.VERIFIED);
        customer.setCustomerStatus(CustomerStatus.VERIFIED);
        Customer savedCustomer = customerRepository.saveAndFlush(customer);
        customerIds.add(savedCustomer.getId());

        AccountIdentifierGenerator.GeneratedAccountIdentifiers identifiers = identifierGenerator.generate();
        Account account = new Account();
        account.setAccountId(identifiers.accountId());
        account.setAccountNumber(identifiers.accountNumber());
        account.setAccountType(AccountType.SAVINGS);
        account.setCurrencyCode("INR");
        account.setAvailableBalance(balance);
        account.setLedgerBalance(balance);
        account.setCustomer(savedCustomer);
        account.transitionTo(AccountStatus.ACTIVE, java.time.Instant.now());
        Account savedAccount = accountRepository.saveAndFlush(account);
        accountIds.add(savedAccount.getId());
        return savedAccount;
    }

    private static final class ForcedRollbackException extends RuntimeException {
    }
}
