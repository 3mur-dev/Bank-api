CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role_id BIGINT NULL,
    version BIGINT NULL,
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(255) NOT NULL UNIQUE,
    balance DECIMAL(19,4) NOT NULL,
    type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    user_id BIGINT NULL,
    version BIGINT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_account_id BIGINT NULL,
    receiver_account_id BIGINT NULL,
    amount DECIMAL(19,4) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    version BIGINT NULL,
    successful BOOLEAN NOT NULL,
    is_flagged BOOLEAN NULL,
    fraud_reason VARCHAR(255) NULL,
    approved_at TIMESTAMP NULL,
    CONSTRAINT fk_transactions_sender
        FOREIGN KEY (sender_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_receiver
        FOREIGN KEY (receiver_account_id) REFERENCES accounts (id)
);

CREATE TABLE audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    success BOOLEAN NOT NULL,
    reason VARCHAR(1000) NULL,
    before_data LONGTEXT NULL,
    after_data LONGTEXT NULL,
    actor_username VARCHAR(100) NULL,
    actor_user_id BIGINT NULL,
    trace_id VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE idempotency_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    operation_type VARCHAR(50) NOT NULL,
    actor_identifier VARCHAR(100) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    transaction_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    response_data TEXT NULL,
    response_status_code INT NULL,
    response_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_idempotency_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);

CREATE INDEX idx_idempotency_keys_transaction_id ON idempotency_keys (transaction_id);
CREATE INDEX idx_transactions_sender_account_id ON transactions (sender_account_id);
CREATE INDEX idx_transactions_receiver_account_id ON transactions (receiver_account_id);
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
