-- ============================================================
-- V6: Add per-question score columns to Answers table
--
-- AnswerScore        : Điểm cuối của câu hỏi (sau guard rule)
--                      = SUM(TestCaseResults.ScoreEarned) trong câu đó
--                        (hoặc 0 nếu guard rule bị trigger)
-- GuardRuleTriggered : TRUE nếu guard rule bị kích hoạt (điểm câu = 0)
-- GuardRuleNote      : Lý do tại sao guard rule bị trigger
--
-- TestCaseResults.ScoreEarned đã có sẵn từ V1 → không cần thêm
-- AIReviews.OopScore   đã có sẵn từ V1 → không cần thêm
-- ============================================================

ALTER TABLE Answers
    ADD COLUMN IF NOT EXISTS AnswerScore         DECIMAL(6,2)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS GuardRuleTriggered   BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS GuardRuleNote        TEXT          DEFAULT NULL;

COMMENT ON COLUMN Answers.AnswerScore IS
    'Điểm cuối của câu hỏi sau khi áp guard rule. NULL = chưa chấm.';

COMMENT ON COLUMN Answers.GuardRuleTriggered IS
    'TRUE nếu guard rule được kích hoạt và điểm câu bị ép về 0.';

COMMENT ON COLUMN Answers.GuardRuleNote IS
    'Giải thích lý do guard rule kích hoạt. Ví dụ: "TC=6.0, OOP=8.0 → 0đ do vi phạm OOP (FailIfOopViolated)"';
