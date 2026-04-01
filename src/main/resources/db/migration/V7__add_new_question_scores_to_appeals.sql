-- Thêm cột lưu trữ điểm thành phần dạng JSON (vì UI thay đổi sang JSON array/map)
-- Dùng JSONB (thay vì JSON) vì Supabase/Postgres xử lý JSONB tốt hơn rất nhiều.

ALTER TABLE Appeals ADD COLUMN IF NOT EXISTS NewQuestionScores JSONB;
