package com.pointbank.bankplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.constant.CustomerStatus;
import com.pointbank.bankplatform.constant.KycStatus;
import com.pointbank.bankplatform.entity.Account;
import com.pointbank.bankplatform.entity.AccountTransaction;
import com.pointbank.bankplatform.entity.Customer;
import com.pointbank.bankplatform.exception.DailyTransactionLimitExceededException;
import com.pointbank.bankplatform.repository.AccountRepository;
import com.pointbank.bankplatform.repository.AccountTransactionRepository;
import com.pointbank.bankplatform.repository.CustomerRepository;
import com.pointbank.bankplatform.utility.AccountIdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.security.jwt.issuer-uri=",
                "app.security.jwt.audience="
        }
)
@TestPropertySource(properties = {
        "spring.datasource.url=${POINTBANK_TEST_DB_URL}",
        "spring.datasource.username=${POINTBANK_TEST_DB_USERNAME:root}",
        "spring.datasource.password=${POINTBANK_TEST_DB_PASSWORD:}"
})
@EnabledIfEnvironmentVariable(named = "POINTBANK_TEST_DB_URL", matches = ".+")
class AccountTransactionLimitConcurrencyTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountIdentifierGenerator identifierGenerator;

    @Autowired
    private Clock clock;

    @Test
    void simultaneousSavingsDepositsCannotExceedRollingLimit() throws Exception {
        Customer customer = createCustomer();
        Account account = createActiveSavingsAccount(customer);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                String key = "concurrent-limit-" + java.util.UUID.randomUUID();
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        var result = accountService.deposit(
                                account.getAccountNumber(),
                                null,
                                key,
                                "integration-test",
                                new BigDecimal("60000.00")
                        );
                        return result.status() == AccountTransactionStatus.COMPLETED;
                    } catch (DailyTransactionLimitExceededException exception) {
                        return false;
                    }
                }));
            }

            ready.await();
            start.countDown();

            long successfulRequests = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulRequests++;
                }
            }
            assertEquals(1, successfulRequests);

            List<AccountTransaction> posted = transactionRepository
                    .findAllByAccount_Id(account.getId(), PageRequest.of(0, 10))
                    .getContent();
            BigDecimal postedTotal = posted.stream()
                    .filter(transaction -> transaction.getStatus() == AccountTransactionStatus.COMPLETED)
                    .map(AccountTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("60000.00"), postedTotal);
        } finally {
            executor.shutdownNow();
            transactionRepository.findAllByAccount_Id(account.getId(), PageRequest.of(0, 10))
                    .forEach(transactionRepository::delete);
            accountRepository.delete(account);
            customerRepository.delete(customer);
        }
    }

    private Customer createCustomer() {
        long customerId = 10_000_000L + Math.floorMod(System.nanoTime(), 89_000_000L);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setGender("Unspecified");
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        long mobileSuffix = Math.floorMod(System.nanoTime(), 1_000_000_000L);
        customer.setMobileNumber("9" + String.format("%09d", mobileSuffix));
        customer.setEmail("account-limit-" + suffix + "@example.test");
        customer.setKycStatus(KycStatus.VERIFIED);
        customer.setCustomerStatus(CustomerStatus.VERIFIED);
        return customerRepository.saveAndFlush(customer);
    }

    private Account createActiveSavingsAccount(Customer customer) {
        AccountIdentifierGenerator.GeneratedAccountIdentifiers identifiers = identifierGenerator.generate();
        Account account = new Account();
        account.setAccountId(identifiers.accountId());
        account.setAccountNumber(identifiers.accountNumber());
        account.setAccountType(AccountType.SAVINGS);
        account.setCurrencyCode("INR");
        account.setAvailableBalance(BigDecimal.ZERO.setScale(2));
        account.setLedgerBalance(BigDecimal.ZERO.setScale(2));
        account.setCustomer(customer);
        account.transitionTo(AccountStatus.ACTIVE, clock.instant());
        return accountRepository.saveAndFlush(account);
    }
}
