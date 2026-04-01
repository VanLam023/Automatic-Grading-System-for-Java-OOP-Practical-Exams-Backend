-- ============================================================
-- Flyway V8: Wallet System Schema
-- Chức năng: Ví điện tử cho Student
-- ============================================================

-- ── Enums ───────────────────────────────────────────────────

CREATE TYPE wallet_transaction_type AS ENUM (
    'DEPOSIT',          -- Nạp tiền vào ví qua PayOS
    'APPEAL_PAYMENT',   -- Trừ tiền ví để tạo phúc khảo
    'APPEAL_REFUND',    -- Hoàn tiền khi appeal APPROVED
    'WITHDRAWAL'        -- Rút tiền ra ngoài
);

CREATE TYPE withdrawal_status AS ENUM (
    'PENDING',      -- Chờ admin xử lý
    'APPROVED',     -- Admin đã duyệt
    'REJECTED',     -- Admin từ chối
    'COMPLETED'     -- Đã hoàn tất chuyển khoản
);

-- ── Wallets ──────────────────────────────────────────────────
-- Mỗi Student có đúng 1 ví (tạo khi nạp tiền lần đầu)

CREATE TABLE Wallets (
    WalletID    UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    StudentID   UUID            NOT NULL UNIQUE REFERENCES Users(UserID),
    Balance     DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    CreatedAt   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UpdatedAt   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── WalletTransactions ────────────────────────────────────────
-- Lịch sử giao dịch: mỗi lần tiền vào/ra đều ghi lại

CREATE TABLE WalletTransactions (
    TransactionID   UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    WalletID        UUID            NOT NULL REFERENCES Wallets(WalletID),
    Type            wallet_transaction_type NOT NULL,
    Amount          DECIMAL(12,2)   NOT NULL,   -- Số tiền giao dịch (luôn dương)
    BalanceBefore   DECIMAL(12,2)   NOT NULL,   -- Số dư trước giao dịch
    BalanceAfter    DECIMAL(12,2)   NOT NULL,   -- Số dư sau giao dịch
    ReferenceID     UUID,                       -- AppealID hoặc WithdrawalID
    ReferenceType   VARCHAR(50),                -- 'APPEAL', 'WITHDRAWAL', 'DEPOSIT'
    Description     TEXT,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── WithdrawalRequests ────────────────────────────────────────
-- Yêu cầu rút tiền của Student gửi cho Admin xử lý

CREATE TABLE WithdrawalRequests (
    WithdrawalID    UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    WalletID        UUID            NOT NULL REFERENCES Wallets(WalletID),
    StudentID       UUID            NOT NULL REFERENCES Users(UserID),
    Amount          DECIMAL(12,2)   NOT NULL,
    BankName        VARCHAR(100)    NOT NULL,
    AccountNumber   VARCHAR(50)     NOT NULL,
    AccountHolder   VARCHAR(255)    NOT NULL,
    Status          withdrawal_status NOT NULL DEFAULT 'PENDING',
    AdminNote       TEXT,
    ProcessedBy     UUID            REFERENCES Users(UserID),
    ProcessedAt     TIMESTAMPTZ,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UpdatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────

CREATE INDEX IDX_Wallets_StudentID                  ON Wallets(StudentID);
CREATE INDEX IDX_WalletTransactions_WalletID        ON WalletTransactions(WalletID);
CREATE INDEX IDX_WalletTransactions_CreatedAt       ON WalletTransactions(CreatedAt DESC);
CREATE INDEX IDX_WithdrawalRequests_StudentID       ON WithdrawalRequests(StudentID);
CREATE INDEX IDX_WithdrawalRequests_Status          ON WithdrawalRequests(Status);
