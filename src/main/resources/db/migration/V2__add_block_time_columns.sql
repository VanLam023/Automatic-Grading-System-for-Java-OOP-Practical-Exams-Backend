-- ============================================================
-- V2: Add exam scheduling columns to Blocks table
-- Each block now has its own exam date and time window
-- ============================================================

ALTER TABLE Blocks ADD COLUMN ExamDate  DATE         NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE Blocks ADD COLUMN StartTime TIMESTAMPTZ  NOT NULL DEFAULT NOW();
ALTER TABLE Blocks ADD COLUMN EndTime   TIMESTAMPTZ  NOT NULL DEFAULT NOW();

-- Constraint: EndTime must be after StartTime
ALTER TABLE Blocks ADD CONSTRAINT CHK_BlockTime CHECK (EndTime > StartTime);

-- Remove defaults after backfilling (they were only for existing rows)
ALTER TABLE Blocks ALTER COLUMN ExamDate  DROP DEFAULT;
ALTER TABLE Blocks ALTER COLUMN StartTime DROP DEFAULT;
ALTER TABLE Blocks ALTER COLUMN EndTime   DROP DEFAULT;
