-- ============================================================
-- Flyway V18: Add display_order column to grading_criteria
-- Renames sort_order concept to display_order for clarity.
-- Existing rows get display_order = sort_order value.
-- ============================================================

ALTER TABLE grading_criteria
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

-- Sync existing rows from sort_order if it exists
UPDATE grading_criteria SET display_order = sort_order;

COMMENT ON COLUMN grading_criteria.display_order IS 'UI display order for criteria within a question. Lower = shown first.';
