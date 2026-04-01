-- ============================================================
-- Flyway V10: Make Payments.AppealID nullable
-- Các payment nạp ví (WALLET_DEPOSIT) không cần AppealID
-- ============================================================

ALTER TABLE Payments
    ALTER COLUMN AppealID DROP NOT NULL;
