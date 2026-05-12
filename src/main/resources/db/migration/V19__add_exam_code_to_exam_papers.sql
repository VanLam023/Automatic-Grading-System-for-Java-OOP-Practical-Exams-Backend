-- ============================================================
-- Flyway V19: Add exam_code column to ExamPapers table
-- Mã đề thi (exam code) do giảng viên nhập khi upload đề.
-- Nullable để tương thích với dữ liệu cũ (backward compatible).
-- ============================================================

ALTER TABLE exampapers
    ADD COLUMN IF NOT EXISTS examcode VARCHAR(50) NOT NULL DEFAULT '';

-- Remove default after adding — new rows must always provide examCode explicitly
ALTER TABLE exampapers
    ALTER COLUMN examcode DROP DEFAULT;

COMMENT ON COLUMN exampapers.examcode IS
    'Mã đề thi bắt buộc nhập khi upload, ví dụ: PRO192_PE_FA25. Dùng để định danh đề thi.';
