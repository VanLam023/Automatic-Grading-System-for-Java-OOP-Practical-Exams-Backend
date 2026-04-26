-- 1. Add MODE_5 to grading_mode enum (PostgreSQL requires separate transaction usually, but Flyway handles it)
ALTER TYPE grading_mode ADD VALUE IF NOT EXISTS 'MODE_5';

-- 2. Add StructureWeight column to GradingModeConfigs
-- Using IF NOT EXISTS pattern or checking column existence to be safe
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='gradingmodeconfigs' AND column_name='structureweight') THEN
        ALTER TABLE GradingModeConfigs ADD COLUMN StructureWeight DECIMAL(5,2) DEFAULT 0 NOT NULL;
    END IF;
END $$;

-- 3. Insert Default Configuration for MODE_5
-- DisplayName: "Deterministic (No-AI)"
-- Weights: 60% TestCase, 40% Structure
INSERT INTO GradingModeConfigs (Mode, DisplayName, TestCaseWeight, OopWeight, StructureWeight, OopCommentOnly, FailIfZeroTestCase, FailIfOopViolated, IsActive, Description)
VALUES (
    'MODE_5', 
    'Deterministic (No-AI)', 
    60.00, 
    0.00, 
    40.00, 
    false, 
    false, 
    false, 
    true, 
    'Chấm điểm dựa trên Test Case (60%) và phân tích cấu trúc code bằng JavaParser + Reflection (40%). Không sử dụng AI.'
) ON CONFLICT (Mode) DO UPDATE SET 
    DisplayName = EXCLUDED.DisplayName,
    TestCaseWeight = EXCLUDED.TestCaseWeight,
    OopWeight = EXCLUDED.OopWeight,
    StructureWeight = EXCLUDED.StructureWeight,
    Description = EXCLUDED.Description;
