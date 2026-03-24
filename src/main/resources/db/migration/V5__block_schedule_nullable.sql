-- ============================================================
-- V5: Make Block schedule columns nullable
-- Block 10 và Block 3 được tạo tự động khi tạo exam,
-- nhưng lịch thi (ExamDate, StartTime, EndTime) chỉ được
-- điền khi Exam Staff cập nhật — nên các cột này phải nullable.
-- ============================================================

-- Xóa constraint CHK_BlockTime cũ (yêu cầu cả 2 NOT NULL)
ALTER TABLE Blocks DROP CONSTRAINT IF EXISTS "CHK_BlockTime";

-- Cho phép NULL trên 3 cột lịch thi
ALTER TABLE Blocks ALTER COLUMN ExamDate  DROP NOT NULL;
ALTER TABLE Blocks ALTER COLUMN StartTime DROP NOT NULL;
ALTER TABLE Blocks ALTER COLUMN EndTime   DROP NOT NULL;

-- Thêm lại constraint nhưng chỉ áp dụng khi CẢ HAI cột đều được set
ALTER TABLE Blocks ADD CONSTRAINT "CHK_BlockTime"
    CHECK (EndTime IS NULL OR StartTime IS NULL OR EndTime > StartTime);

