-- Exam Staff query/index optimizations
-- Added as a new migration to avoid touching existing schema migrations.

CREATE INDEX IF NOT EXISTS IDX_Exams_Semester_DeletedAt_CreatedAt
    ON Exams (Semester, CreatedAt DESC)
    WHERE DeletedAt IS NULL;

CREATE INDEX IF NOT EXISTS IDX_Appeals_Status_CreatedAt
    ON Appeals (Status, CreatedAt DESC);

CREATE INDEX IF NOT EXISTS IDX_Submissions_BlockID_Status_SubmittedAt
    ON Submissions (BlockID, Status, SubmittedAt DESC);

CREATE INDEX IF NOT EXISTS IDX_AuditLogs_Action_CreatedAt
    ON AuditLogs (Action, CreatedAt DESC);