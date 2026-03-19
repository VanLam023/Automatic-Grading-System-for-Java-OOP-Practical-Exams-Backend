-- ============================================================
-- V4: Auto Grading Schema Updates
-- 1. Rename test_case_status PASS → PASS_TESTCASE, FAIL → FAIL_TESTCASE
-- 2. Create grading_result_status enum
-- 3. Add Status column to GradingResults
-- ============================================================

-- Step 1: Rename PASS → PASS_TESTCASE
ALTER TYPE test_case_status RENAME VALUE 'PASS' TO 'PASS_TESTCASE';

-- Step 2: Rename FAIL → FAIL_TESTCASE
ALTER TYPE test_case_status RENAME VALUE 'FAIL' TO 'FAIL_TESTCASE';

-- Step 3: Create new grading_result_status enum
CREATE TYPE grading_result_status AS ENUM ('PASS', 'FAIL');

-- Step 4: Add Status column to GradingResults (default FAIL until grading completes)
ALTER TABLE GradingResults
    ADD COLUMN Status grading_result_status NOT NULL DEFAULT 'FAIL';
