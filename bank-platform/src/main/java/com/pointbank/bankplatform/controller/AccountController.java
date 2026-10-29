package com.pointbank.bankplatform.controller;

import java.net.URI;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;

import com.pointbank.bankplatform.constant.AccountStatus;
import com.pointbank.bankplatform.constant.AccountTransactionStatus;
import com.pointbank.bankplatform.constant.AccountType;
import com.pointbank.bankplatform.dto.AccountOpenRequest;
import com.pointbank.bankplatform.dto.AccountResponse;
import com.pointbank.bankplatform.dto.AccountTransferRequest;
import com.pointbank.bankplatform.dto.AccountTransferResponse;
import com.pointbank.bankplatform.dto.AccountTransactionResponse;
import com.pointbank.bankplatform.dto.ApiResponseDto;
import com.pointbank.bankplatform.dto.MoneyTransactionRequest;
import com.pointbank.bankplatform.service.AccountService;
import com.pointbank.bankplatform.service.CustomerIdentityService;
import com.pointbank.bankplatform.service.TransactionStatementPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
@Tag(name = "Accounts", description = "Open accounts, manage lifecycle, and perform simulated balance operations.")
public class AccountController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;
    private final CustomerIdentityService customerIdentityService;
    private final TransactionStatementPdfService transactionStatementPdfService;

    public AccountController(
            AccountService accountService,
            CustomerIdentityService customerIdentityService,
            TransactionStatementPdfService transactionStatementPdfService
    ) {
        this.accountService = accountService;
        this.customerIdentityService = customerIdentityService;
        this.transactionStatementPdfService = transactionStatementPdfService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Open a pending account for the authenticated customer")
    public ResponseEntity<ApiResponseDto<AccountResponse>> openAccount(
            @Valid @RequestBody AccountOpenRequest request,
            Authentication authentication
    ) {
        LOGGER.info("Account opening request received: accountType={}", request.accountType());
        Long customerId = customerIdentityService.getAuthenticatedCustomerId(authenticatedJwt(authentication));
        AccountResponse account = accountService.openAccount(customerId, request.accountType());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{accountNumber}")
                .buildAndExpand(account.accountNumber())
                .toUri();
        return ResponseEntity.created(location)
                .body(ApiResponseDto.success("Account opened and pending activation.", account));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List accounts owned by the authenticated customer")
    public ApiResponseDto<Page<AccountResponse>> getMyAccounts(
            Authentication authentication,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Long customerId = customerIdentityService.getAuthenticatedCustomerId(authenticatedJwt(authentication));
        return ApiResponseDto.success("Customer accounts retrieved.", accountService.getCustomerAccounts(customerId, pageable));
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "List accounts for a customer (operational role)")
    public ApiResponseDto<Page<AccountResponse>> getCustomerAccounts(
            @PathVariable @Positive Long customerId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponseDto.success(
                "Customer accounts retrieved.",
                accountService.getCustomerAccounts(customerId, pageable)
        );
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Search accounts by type and/or status (operational role)")
    public ApiResponseDto<Page<AccountResponse>> searchAccounts(
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) AccountStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponseDto.success("Accounts retrieved.", accountService.getAccounts(type, status, pageable));
    }

    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Get account details")
    public ApiResponseDto<AccountResponse> getAccount(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            Authentication authentication
    ) {
        return ApiResponseDto.success(
                "Account retrieved.",
                accountService.getAccount(accountNumber, ownerCustomerId(authentication))
        );
    }

    @GetMapping("/{accountNumber}/balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(
            summary = "Check an account balance",
            description = "Available and ledger balances reflect the same committed amount; holds and pending settlements are not modeled."
    )
    public ApiResponseDto<AccountResponse> checkBalance(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            Authentication authentication
    ) {
        return ApiResponseDto.success(
                "Account balance retrieved.",
                accountService.checkBalance(accountNumber, ownerCustomerId(authentication))
        );
    }

    @PostMapping("/{accountNumber}/deposits")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Record a simulated deposit; this does not verify external receipt of funds")
    public ResponseEntity<ApiResponseDto<AccountTransactionResponse>> deposit(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody MoneyTransactionRequest request,
            Authentication authentication
    ) {
        LOGGER.info("Deposit request received: accountRef={}", maskAccountNumber(accountNumber));
        AccountTransactionResponse response = accountService.deposit(
                accountNumber,
                ownerCustomerId(authentication),
                idempotencyKey,
                actorReference(authentication),
                request.amount()
        );
        return transactionResult("Deposit processed.", response);
    }

    @PostMapping("/{accountNumber}/withdrawals")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Record a withdrawal from available account funds")
    public ResponseEntity<ApiResponseDto<AccountTransactionResponse>> withdraw(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody MoneyTransactionRequest request,
            Authentication authentication
    ) {
        LOGGER.info("Withdrawal request received: accountRef={}", maskAccountNumber(accountNumber));
        AccountTransactionResponse response = accountService.withdraw(
                accountNumber,
                ownerCustomerId(authentication),
                idempotencyKey,
                actorReference(authentication),
                request.amount()
        );
        return transactionResult("Withdrawal processed.", response);
    }

    @PostMapping("/{accountNumber}/transfers")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Transfer funds to another active account in the same currency")
    public ResponseEntity<ApiResponseDto<AccountTransferResponse>> transfer(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody AccountTransferRequest request,
            Authentication authentication
    ) {
        LOGGER.info("Internal transfer request received: sourceAccountRef={}", maskAccountNumber(accountNumber));
        AccountTransferResponse response = accountService.transfer(
                accountNumber,
                request.destinationAccountNumber(),
                ownerCustomerId(authentication),
                idempotencyKey,
                actorReference(authentication),
                request.amount()
        );
        boolean completed = response.debit().status() == AccountTransactionStatus.COMPLETED;
        HttpStatus status = completed ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(new ApiResponseDto<>(
                completed,
                completed ? "Transfer completed." : "Transfer failed: " + response.debit().failureReason(),
                response,
                Instant.now()
        ));
    }

    @GetMapping("/{accountNumber}/transfers/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Retrieve a transfer result by its idempotency key")
    public ApiResponseDto<AccountTransferResponse> getTransferStatus(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            Authentication authentication
    ) {
        return ApiResponseDto.success(
                "Transfer status retrieved.",
                accountService.getTransferByIdempotencyKey(
                        accountNumber, ownerCustomerId(authentication), idempotencyKey
                )
        );
    }

    @GetMapping("/{accountNumber}/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Get paginated transaction history for an account")
    public ApiResponseDto<Page<AccountTransactionResponse>> getTransactionHistory(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            Authentication authentication,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponseDto.success(
                "Transaction history retrieved.",
                accountService.getTransactionHistory(accountNumber, ownerCustomerId(authentication), pageable)
        );
    }

    @GetMapping(value = "/{accountNumber}/transactions/statement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(
            summary = "Download account transaction history as PDF",
            description = "Provide year for a full-year statement, or year with both fromMonth and toMonth for a month range."
    )
    public ResponseEntity<byte[]> downloadTransactionStatement(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            @RequestParam @Min(1900) @Max(9998) int year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer fromMonth,
            @RequestParam(required = false) @Min(1) @Max(12) Integer toMonth,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(required = false) AccountTransactionStatus status,
            Authentication authentication
    ) throws IOException {
        if ((fromMonth == null) != (toMonth == null)
                || (fromMonth != null && fromMonth > toMonth)) {
            throw new com.pointbank.bankplatform.exception.InvalidRequestException(
                    "Provide both fromMonth and toMonth, with fromMonth no later than toMonth."
            );
        }

        LocalDate periodStartDate = LocalDate.of(year, fromMonth == null ? 1 : fromMonth, 1);
        LocalDate periodEndDate = fromMonth == null
                ? LocalDate.of(year + 1, 1, 1)
                : LocalDate.of(year, toMonth, 1).plusMonths(1);
        Instant periodStart = periodStartDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = periodEndDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        Long ownerCustomerId = ownerCustomerId(authentication);
        AccountResponse account = accountService.getAccount(accountNumber, ownerCustomerId);
        var transactions = accountService.getStatementTransactions(
                accountNumber,
                ownerCustomerId,
                periodStart,
                periodEnd,
                accountType,
                status
        );
        byte[] pdf = transactionStatementPdfService.generate(
                account, year, fromMonth, toMonth, accountType, status, transactions
        );
        String periodName = fromMonth == null
                ? String.valueOf(year)
                : String.format("%04d-%02d-to-%02d", year, fromMonth, toMonth);

        LOGGER.info("Transaction statement generated: accountRef={}, year={}, fromMonth={}, toMonth={}, status={}",
                maskAccountNumber(accountNumber), year, fromMonth, toMonth, status);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transaction-history-" + accountNumber + "-" + periodName + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    @GetMapping("/{accountNumber}/transactions/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Retrieve the result of a transaction request by its idempotency key")
    public ApiResponseDto<AccountTransactionResponse> getTransactionStatus(
            @PathVariable @NotBlank @Pattern(regexp = "^[0-9]{14}$") String accountNumber,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            Authentication authentication
    ) {
        return ApiResponseDto.success(
                "Transaction status retrieved.",
                accountService.getTransactionByIdempotencyKey(
                        accountNumber, ownerCustomerId(authentication), idempotencyKey
                )
        );
    }

    @GetMapping("/transactions/{transactionReference}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ACCOUNT_OFFICER', 'BANK_TELLER', 'ADMIN')")
    @Operation(summary = "Get a transaction by its reference")
    public ApiResponseDto<AccountTransactionResponse> getTransaction(
            @PathVariable @NotBlank String transactionReference,
            Authentication authentication
    ) {
        return ApiResponseDto.success(
                "Transaction retrieved.",
                accountService.getTransaction(transactionReference, ownerCustomerId(authentication))
        );
    }

    @PostMapping("/{accountNumber}/activate")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'ADMIN')")
    public ApiResponseDto<AccountResponse> activate(
            @PathVariable @Pattern(regexp = "^[0-9]{14}$") String accountNumber
    ) {
        return transition(accountNumber, AccountStatus.ACTIVE, "Account activated.");
    }

    @PostMapping("/{accountNumber}/freeze")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'ADMIN')")
    public ApiResponseDto<AccountResponse> freeze(
            @PathVariable @Pattern(regexp = "^[0-9]{14}$") String accountNumber
    ) {
        return transition(accountNumber, AccountStatus.FROZEN, "Account frozen.");
    }

    @PostMapping("/{accountNumber}/unfreeze")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'ADMIN')")
    public ApiResponseDto<AccountResponse> unfreeze(
            @PathVariable @Pattern(regexp = "^[0-9]{14}$") String accountNumber
    ) {
        return transition(accountNumber, AccountStatus.ACTIVE, "Account unfrozen.");
    }

    @PostMapping("/{accountNumber}/block")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'ADMIN')")
    public ApiResponseDto<AccountResponse> block(
            @PathVariable @Pattern(regexp = "^[0-9]{14}$") String accountNumber
    ) {
        return transition(accountNumber, AccountStatus.BLOCKED, "Account blocked.");
    }

    @PostMapping("/{accountNumber}/close")
    @PreAuthorize("hasAnyRole('ACCOUNT_OFFICER', 'ADMIN')")
    public ApiResponseDto<AccountResponse> close(
            @PathVariable @Pattern(regexp = "^[0-9]{14}$") String accountNumber
    ) {
        return transition(accountNumber, AccountStatus.CLOSED, "Account closed.");
    }

    private ApiResponseDto<AccountResponse> transition(
            String accountNumber,
            AccountStatus targetStatus,
            String message
    ) {
        return ApiResponseDto.success(message, accountService.transitionAccount(accountNumber, targetStatus));
    }

    private ResponseEntity<ApiResponseDto<AccountTransactionResponse>> transactionResult(
            String message,
            AccountTransactionResponse response
    ) {
        boolean completed = response.status() == AccountTransactionStatus.COMPLETED;
        HttpStatus status = completed ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(new ApiResponseDto<>(
                completed,
                completed ? message : "Transaction failed: " + response.failureReason(),
                response,
                Instant.now()
        ));
    }

    private Long ownerCustomerId(Authentication authentication) {
        if (hasOperationalRole(authentication)) {
            return null;
        }
        return customerIdentityService.getAuthenticatedCustomerId(authenticatedJwt(authentication));
    }

    private boolean hasOperationalRole(Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"))) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ACCOUNT_OFFICER")
                        || authority.equals("ROLE_BANK_TELLER")
                        || authority.equals("ROLE_ADMIN"));
    }

    private String actorReference(Authentication authentication) {
        String actorReference = authentication.getName();
        if (actorReference == null || actorReference.isBlank()) {
            throw new AccessDeniedException("Authenticated identity is unavailable.");
        }
        return actorReference;
    }

    private String maskAccountNumber(String accountNumber) {
        return accountNumber.length() <= 4
                ? "****"
                : "**********" + accountNumber.substring(accountNumber.length() - 4);
    }

    private Jwt authenticatedJwt(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("Authenticated identity is unavailable.");
    }
}
