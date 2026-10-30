CREATE TABLE customer (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_status ENUM('ACTIVE','BLOCKED','CREATED','INACTIVE','PENDING','SUSPENDED','VERIFIED') DEFAULT NULL,
    date_of_birth DATE NOT NULL,
    email_id VARCHAR(255) NOT NULL,
    email_verified BIT(1) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    gender VARCHAR(255) NOT NULL,
    identity_issuer VARCHAR(512) DEFAULT NULL,
    identity_subject VARCHAR(255) DEFAULT NULL,
    kyc_rejection_reason VARCHAR(500) DEFAULT NULL,
    kyc_reviewed_at DATETIME(6) DEFAULT NULL,
    kyc_reviewed_by VARCHAR(255) DEFAULT NULL,
    kyc_status ENUM('CREATED','EXPIRED','IN_PROGRESS','PENDING','REJECTED','VERIFIED') NOT NULL,
    kyc_verified_at DATETIME(6) DEFAULT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100) DEFAULT NULL,
    mobile_number VARCHAR(255) NOT NULL,
    mobile_number_verified BIT(1) NOT NULL,
    password VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UKjt63q2suy91q2uch0ll9wcxx5 (customer_id),
    UNIQUE KEY UKp1nyof8six1aupbuhnlax3tkk (email_id),
    UNIQUE KEY UK5v8hijx47m783qo8i4sox2n5t (mobile_number),
    UNIQUE KEY uk_customer_identity (identity_issuer, identity_subject),
    KEY idx_customer_id (customer_id),
    KEY idx_mobile_number (mobile_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_identifier_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(14) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK3inec9f79opg06kj4em0w3klb (account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customer_address (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255) DEFAULT NULL,
    address_type ENUM('MAILING','OTHER','RESIDENTIAL','WORK') NOT NULL,
    city VARCHAR(100) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    primary_address BIT(1) NOT NULL,
    state_or_province VARCHAR(100) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    KEY FKr9ofa0ydsgbaqmt9leb3v5eii (customer_id),
    CONSTRAINT FKr9ofa0ydsgbaqmt9leb3v5eii
        FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE kyc_document (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    current_version TINYINT(1) NOT NULL DEFAULT 1,
    document_type ENUM('AADHAAR','PAN') NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    customer_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UKc9ytxj7fmigju99ptyx19l3y (stored_file_name),
    KEY FK4clro0kmem7k12lt8lxn7gqld (customer_id),
    CONSTRAINT FK4clro0kmem7k12lt8lxn7gqld
        FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bank_account (
    id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    account_id BIGINT NOT NULL,
    account_number VARCHAR(14) NOT NULL,
    account_status ENUM('ACTIVE','BLOCKED','CLOSED','FROZEN','INACTIVE','PENDING','SUSPENDED') NOT NULL,
    account_type ENUM('CURRENT','SALARY','SAVINGS') NOT NULL,
    activated_at DATETIME(6) DEFAULT NULL,
    available_balance DECIMAL(19,2) NOT NULL,
    closed_at DATETIME(6) DEFAULT NULL,
    currency_code VARCHAR(3) NOT NULL,
    is_active BIT(1) NOT NULL,
    ledger_balance DECIMAL(19,2) NOT NULL,
    version BIGINT NOT NULL,
    customer_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_account_number (account_number),
    UNIQUE KEY uk_account_account_id (account_id),
    UNIQUE KEY uk_account_customer_type (customer_id, account_type),
    KEY idx_account_customer_id (customer_id),
    KEY idx_account_status_type (account_status, account_type),
    CONSTRAINT FKj818ht4ban0c4uw4bmsbf3jme
        FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_transaction (
    id BINARY(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    available_balance_after DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    failure_reason VARCHAR(100) DEFAULT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    ledger_balance_after DECIMAL(19,2) NOT NULL,
    transaction_status ENUM('CANCELLED','COMPLETED','FAILED','PENDING') NOT NULL,
    transaction_reference VARCHAR(36) NOT NULL,
    transaction_type ENUM('DEPOSIT','TRANSFER_IN','TRANSFER_OUT','WITHDRAWAL') NOT NULL,
    account_id BINARY(16) NOT NULL,
    initiated_by VARCHAR(255) NOT NULL,
    counterparty_account_number VARCHAR(14) DEFAULT NULL,
    transfer_reference VARCHAR(36) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_transaction_reference (transaction_reference),
    UNIQUE KEY uk_account_transaction_idempotency (account_id, idempotency_key),
    KEY idx_transaction_account_created (account_id, created_at),
    KEY idx_transaction_limit_lookup (account_id, transaction_type, transaction_status, created_at),
    KEY idx_transaction_transfer_reference (transfer_reference),
    CONSTRAINT FKh9yd2wqdun1ptcx4bxjwsrpyh
        FOREIGN KEY (account_id) REFERENCES bank_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
