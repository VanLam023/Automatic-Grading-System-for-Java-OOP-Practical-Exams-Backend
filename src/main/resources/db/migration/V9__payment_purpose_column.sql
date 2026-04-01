-- ============================================================
-- Flyway V9: Add DepositForStudentID to Payments table
-- Dùng để phân biệt Payment nạp ví (DEPOSIT) vs Payment appeal
-- Nếu DepositForStudentID IS NOT NULL → đây là lệnh nạp tiền ví
-- ============================================================

ALTER TABLE Payments
    ADD COLUMN IF NOT EXISTS DepositForStudentID UUID REFERENCES Users(UserID),
    ADD COLUMN IF NOT EXISTS PaymentPurpose VARCHAR(20) NOT NULL DEFAULT 'APPEAL';

COMMENT ON COLUMN Payments.PaymentPurpose IS 'APPEAL hoặc WALLET_DEPOSIT';
COMMENT ON COLUMN Payments.DepositForStudentID IS 'Khi PaymentPurpose=WALLET_DEPOSIT, đây là student được cộng tiền ví';
