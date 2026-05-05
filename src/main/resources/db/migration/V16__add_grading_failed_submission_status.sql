-- Flyway V16: Add GRADING_FAILED status for submissions that must be regraded
-- because AI grading returned truncated / invalid / inconsistent results.

ALTER TYPE submission_status ADD VALUE IF NOT EXISTS 'GRADING_FAILED';
