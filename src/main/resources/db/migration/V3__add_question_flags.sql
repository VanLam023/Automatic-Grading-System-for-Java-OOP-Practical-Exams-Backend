-- ============================================================
-- Flyway V3: Add REMOVE_SPACES and CASE_SENSITIVE flags to Questions
-- These flags are parsed from tc{n}.txt inside the exam paper zip.
-- They control test-case comparison behaviour during grading:
--   RemoveSpaces  = strip all whitespace from student's INPUT before feeding to program
--   CaseSensitive = whether output comparison is case-sensitive
-- Note: No double quotes — PostgreSQL stores table/column names as lowercase.
-- ============================================================

ALTER TABLE Questions
    ADD COLUMN IF NOT EXISTS RemoveSpaces  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS CaseSensitive BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN Questions.RemoveSpaces  IS 'If TRUE, strip all whitespace characters from INPUT before feeding to student program (parsed from tc{n}.txt REMOVE_SPACES flag)';
COMMENT ON COLUMN Questions.CaseSensitive IS 'If TRUE, output comparison is case-sensitive when checking student program output vs expected (parsed from tc{n}.txt CASE_SENSITIVE flag)';
