-- ============================================================
-- DATABASE SCHEMA: OOP Exam Grading System
-- Database: PostgreSQL 15+
-- Created: 2026-02-22
-- Naming Convention:
--   - PK: {TableName}ID  (e.g., UserID, RoleID, ExamID)
--   - FK: same name as referenced PK column
--   - All names: PascalCase, English only
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE exam_status AS ENUM ('UPCOMING', 'ONGOING', 'COMPLETED');

CREATE TYPE submission_status AS ENUM ('SUBMITTED', 'GRADING', 'GRADED');

CREATE TYPE appeal_status AS ENUM (
    'PENDING_PAYMENT', 'PENDING', 'PROCESSING',
    'COMPLETED', 'APPROVED', 'DENIED', 'CANCELLED'
);

CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');

CREATE TYPE notification_type AS ENUM ('IN_APP', 'EMAIL', 'BOTH');

CREATE TYPE grading_mode AS ENUM ('MODE_1', 'MODE_2', 'MODE_3', 'MODE_4');

CREATE TYPE test_case_status AS ENUM ('PASS', 'FAIL', 'ERROR', 'TIMEOUT');

CREATE TYPE audit_action AS ENUM (
    'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT',
    'SUBMIT', 'GRADE', 'APPROVE', 'DENY', 'ASSIGN',
    'PAYMENT', 'REFUND', 'CONFIG_CHANGE'
);

-- ============================================================
-- GROUP 1: AUTHENTICATION & AUTHORIZATION
-- ============================================================

-- 1. Roles
CREATE TABLE Roles (
    RoleID          SERIAL          PRIMARY KEY,
    Name            VARCHAR(50)     NOT NULL UNIQUE,
    DisplayName     VARCHAR(100)    NOT NULL,
    Description     TEXT,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 2. Users
CREATE TABLE Users (
    UserID          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    RoleID          INT             NOT NULL REFERENCES Roles(RoleID),
    Username        VARCHAR(100)    NOT NULL UNIQUE,
    Email           VARCHAR(255)    NOT NULL UNIQUE,
    PasswordHash    VARCHAR(255)    NOT NULL,
    FullName        VARCHAR(255)    NOT NULL,
    MSSV            VARCHAR(20)     UNIQUE,
    Phone           VARCHAR(20),
    AvatarUrl       TEXT,
    IsActive        BOOLEAN         NOT NULL DEFAULT FALSE,
    IsLocked        BOOLEAN         NOT NULL DEFAULT FALSE,
    LoginFailCount  INT             NOT NULL DEFAULT 0,
    LockedUntil     TIMESTAMPTZ,
    LastLoginAt     TIMESTAMPTZ,
    EmailVerifiedAt TIMESTAMPTZ,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UpdatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    DeletedAt       TIMESTAMPTZ
);

-- 3. RefreshTokens
CREATE TABLE RefreshTokens (
    RefreshTokenID  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    UserID          UUID            NOT NULL REFERENCES Users(UserID) ON DELETE CASCADE,
    TokenHash       VARCHAR(255)    NOT NULL UNIQUE,
    ExpiresAt       TIMESTAMPTZ     NOT NULL,
    IsRevoked       BOOLEAN         NOT NULL DEFAULT FALSE,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 4. PasswordResetTokens
CREATE TABLE PasswordResetTokens (
    PasswordResetTokenID UUID       PRIMARY KEY DEFAULT uuid_generate_v4(),
    UserID              UUID        NOT NULL REFERENCES Users(UserID) ON DELETE CASCADE,
    TokenHash           VARCHAR(255) NOT NULL UNIQUE,
    ExpiresAt           TIMESTAMPTZ NOT NULL,
    IsUsed              BOOLEAN     NOT NULL DEFAULT FALSE,
    CreatedAt           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- GROUP 2: EXAM STRUCTURE
-- ============================================================

-- 5. Exams
CREATE TABLE Exams (
    ExamID          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    CreatedBy       UUID            NOT NULL REFERENCES Users(UserID),
    Name            VARCHAR(255)    NOT NULL,
    Semester        VARCHAR(50)     NOT NULL,
    AcademicYear    VARCHAR(20)     NOT NULL,
    Description     TEXT,
    StartTime       TIMESTAMPTZ     NOT NULL,
    EndTime         TIMESTAMPTZ     NOT NULL,
    Status          exam_status     NOT NULL DEFAULT 'UPCOMING',
    GradingMode     grading_mode    NOT NULL DEFAULT 'MODE_1',
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UpdatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    DeletedAt       TIMESTAMPTZ,

    CONSTRAINT CHK_ExamTime CHECK (EndTime > StartTime)
);

-- 6. Blocks
CREATE TABLE Blocks (
    BlockID         UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    ExamID          UUID            NOT NULL REFERENCES Exams(ExamID) ON DELETE CASCADE,
    Name            VARCHAR(50)     NOT NULL,
    Description     TEXT,
    ExamDate        DATE            NOT NULL,
    StartTime       TIMESTAMPTZ     NOT NULL,
    EndTime         TIMESTAMPTZ     NOT NULL,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT UQ_ExamBlock UNIQUE (ExamID, Name),
    CONSTRAINT CHK_BlockTime CHECK (EndTime > StartTime)
);

-- 7. ExamPapers
CREATE TABLE ExamPapers (
    ExamPaperID     UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    BlockID         UUID            NOT NULL UNIQUE REFERENCES Blocks(BlockID) ON DELETE CASCADE,
    UploadedBy      UUID            NOT NULL REFERENCES Users(UserID),
    FileName        VARCHAR(255)    NOT NULL,
    FilePath        TEXT            NOT NULL,
    FileSizeBytes   BIGINT          NOT NULL,
    TotalQuestions  INT             NOT NULL DEFAULT 0,
    TotalTestCases  INT             NOT NULL DEFAULT 0,
    UploadedAt      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 8. Questions
CREATE TABLE Questions (
    QuestionID      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    ExamPaperID     UUID            NOT NULL REFERENCES ExamPapers(ExamPaperID) ON DELETE CASCADE,
    QuestionNumber  INT             NOT NULL,
    Title           VARCHAR(255)    NOT NULL,
    Description     TEXT,
    MaxScore        DECIMAL(5,2)    NOT NULL,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT UQ_PaperQuestion UNIQUE (ExamPaperID, QuestionNumber)
);

-- 9. TestCases
CREATE TABLE TestCases (
    TestCaseID      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    QuestionID      UUID            NOT NULL REFERENCES Questions(QuestionID) ON DELETE CASCADE,
    TestCaseNumber  INT             NOT NULL,
    InputData       TEXT,
    ExpectedOutput  TEXT            NOT NULL,
    Score           DECIMAL(5,2)    NOT NULL DEFAULT 0,
    TimeLimitMs     INT             NOT NULL DEFAULT 5000,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT UQ_QuestionTestCase UNIQUE (QuestionID, TestCaseNumber)
);

-- ============================================================
-- GROUP 3: SUBMISSION & GRADING
-- ============================================================

-- 10. Submissions
CREATE TABLE Submissions (
    SubmissionID    UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    StudentID       UUID            NOT NULL REFERENCES Users(UserID),
    BlockID         UUID            NOT NULL REFERENCES Blocks(BlockID),
    FileName        VARCHAR(255)    NOT NULL,
    FilePath        TEXT            NOT NULL,
    FileSizeBytes   BIGINT          NOT NULL,
    Status          submission_status NOT NULL DEFAULT 'SUBMITTED',
    SubmittedAt     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    GradedAt        TIMESTAMPTZ,

    CONSTRAINT UQ_StudentBlock UNIQUE (StudentID, BlockID)
);

-- 11. Answers
CREATE TABLE Answers (
    AnswerID        UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    SubmissionID    UUID            NOT NULL REFERENCES Submissions(SubmissionID) ON DELETE CASCADE,
    QuestionID      UUID            NOT NULL REFERENCES Questions(QuestionID),
    JarFilePath     TEXT,
    SourceCodePath  TEXT,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT UQ_SubmissionQuestion UNIQUE (SubmissionID, QuestionID)
);

-- 12. TestCaseResults
CREATE TABLE TestCaseResults (
    TestCaseResultID UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    AnswerID        UUID            NOT NULL REFERENCES Answers(AnswerID) ON DELETE CASCADE,
    TestCaseID      UUID            NOT NULL REFERENCES TestCases(TestCaseID),
    Status          test_case_status NOT NULL,
    ActualOutput    TEXT,
    ExecutionTimeMs INT,
    ErrorMessage    TEXT,
    ScoreEarned     DECIMAL(5,2)    NOT NULL DEFAULT 0,
    ExecutedAt      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT UQ_AnswerTestCase UNIQUE (AnswerID, TestCaseID)
);

-- 13. AIReviews
CREATE TABLE AIReviews (
    AIReviewID      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    AnswerID        UUID            NOT NULL UNIQUE REFERENCES Answers(AnswerID) ON DELETE CASCADE,
    AIModel         VARCHAR(100)    NOT NULL,
    OopScore        DECIMAL(5,2),
    Comment         TEXT,
    RawResponse     JSONB,
    IsOopViolated   BOOLEAN         NOT NULL DEFAULT FALSE,
    TokensUsed      INT,
    ReviewedAt      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 14. GradingResults
CREATE TABLE GradingResults (
    GradingResultID UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    SubmissionID    UUID            NOT NULL UNIQUE REFERENCES Submissions(SubmissionID) ON DELETE CASCADE,
    GradingMode     grading_mode    NOT NULL,
    TotalScore      DECIMAL(6,2)    NOT NULL DEFAULT 0,
    MaxScore        DECIMAL(6,2)    NOT NULL,
    TestCaseScore   DECIMAL(6,2)    NOT NULL DEFAULT 0,
    OopScore        DECIMAL(6,2)    NOT NULL DEFAULT 0,
    GradedBy        UUID            REFERENCES Users(UserID),
    Note            TEXT,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UpdatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- GROUP 4: APPEAL & PAYMENT
-- ============================================================

-- 15. Appeals
CREATE TABLE Appeals (
    AppealID            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    SubmissionID        UUID        NOT NULL UNIQUE REFERENCES Submissions(SubmissionID),
    StudentID           UUID        NOT NULL REFERENCES Users(UserID),
    AssignedLecturerID  UUID        REFERENCES Users(UserID),
    AssignedBy          UUID        REFERENCES Users(UserID),
    Status              appeal_status NOT NULL DEFAULT 'PENDING_PAYMENT',
    Reason              TEXT        NOT NULL,
    LecturerComment     TEXT,
    NewScore            DECIMAL(6,2),
    DeadlineAt          TIMESTAMPTZ,
    AssignedAt          TIMESTAMPTZ,
    CompletedAt         TIMESTAMPTZ,
    CreatedAt           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UpdatedAt           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 16. Payments
CREATE TABLE Payments (
    PaymentID           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    AppealID            UUID        NOT NULL REFERENCES Appeals(AppealID) ON DELETE CASCADE,
    StudentID           UUID        NOT NULL REFERENCES Users(UserID),
    Amount              DECIMAL(12,2) NOT NULL,
    Currency            VARCHAR(10) NOT NULL DEFAULT 'VND',
    Status              payment_status NOT NULL DEFAULT 'PENDING',
    PayosOrderID        VARCHAR(255) UNIQUE,
    PayosPaymentLinkID  VARCHAR(255),
    QrCodeUrl           TEXT,
    CheckoutUrl         TEXT,
    ExpiresAt           TIMESTAMPTZ NOT NULL,
    PaidAt              TIMESTAMPTZ,
    RefundedAt          TIMESTAMPTZ,
    PayosWebhookData    JSONB,
    CreatedAt           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UpdatedAt           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- GROUP 5: NOTIFICATION & AUDIT
-- ============================================================

-- 17. Notifications
CREATE TABLE Notifications (
    NotificationID      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    UserID              UUID        NOT NULL REFERENCES Users(UserID) ON DELETE CASCADE,
    Title               VARCHAR(255) NOT NULL,
    Body                TEXT        NOT NULL,
    Type                notification_type NOT NULL DEFAULT 'IN_APP',
    RelatedEntityType   VARCHAR(50),
    RelatedEntityID     UUID,
    IsRead              BOOLEAN     NOT NULL DEFAULT FALSE,
    ReadAt              TIMESTAMPTZ,
    CreatedAt           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 18. AuditLogs
CREATE TABLE AuditLogs (
    AuditLogID      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    UserID          UUID            REFERENCES Users(UserID),
    Action          audit_action    NOT NULL,
    EntityType      VARCHAR(100)    NOT NULL,
    EntityID        UUID,
    OldValues       JSONB,
    NewValues       JSONB,
    IpAddress       INET,
    UserAgent       TEXT,
    CorrelationID   UUID,
    CreatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- GROUP 6: SYSTEM CONFIGURATION
-- ============================================================

-- 19. SystemConfigs
CREATE TABLE SystemConfigs (
    SystemConfigID  SERIAL          PRIMARY KEY,
    ConfigKey       VARCHAR(100)    NOT NULL UNIQUE,
    ConfigValue     TEXT            NOT NULL,
    IsEncrypted     BOOLEAN         NOT NULL DEFAULT FALSE,
    Description     TEXT,
    UpdatedBy       UUID            REFERENCES Users(UserID),
    UpdatedAt       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 20. GradingModeConfigs
CREATE TABLE GradingModeConfigs (
    GradingModeConfigID SERIAL      PRIMARY KEY,
    Mode                grading_mode NOT NULL UNIQUE,
    DisplayName         VARCHAR(100) NOT NULL,
    TestCaseWeight      DECIMAL(5,2) NOT NULL,
    OopWeight           DECIMAL(5,2) NOT NULL,
    OopCommentOnly      BOOLEAN      NOT NULL DEFAULT FALSE,
    FailIfZeroTestCase  BOOLEAN      NOT NULL DEFAULT FALSE,
    FailIfOopViolated   BOOLEAN      NOT NULL DEFAULT FALSE,
    IsActive            BOOLEAN      NOT NULL DEFAULT TRUE,
    Description         TEXT,

    CONSTRAINT CHK_WeightSum CHECK (TestCaseWeight + OopWeight = 100)
);

-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX IDX_Users_RoleID         ON Users(RoleID);
CREATE INDEX IDX_Users_Email          ON Users(Email);
CREATE INDEX IDX_Users_MSSV           ON Users(MSSV) WHERE MSSV IS NOT NULL;
CREATE INDEX IDX_Users_DeletedAt      ON Users(DeletedAt) WHERE DeletedAt IS NULL;

CREATE INDEX IDX_RefreshTokens_UserID ON RefreshTokens(UserID);

CREATE INDEX IDX_Exams_Status         ON Exams(Status);
CREATE INDEX IDX_Exams_CreatedBy      ON Exams(CreatedBy);
CREATE INDEX IDX_Exams_StartTime      ON Exams(StartTime);
CREATE INDEX IDX_Exams_DeletedAt      ON Exams(DeletedAt) WHERE DeletedAt IS NULL;

CREATE INDEX IDX_Blocks_ExamID        ON Blocks(ExamID);

CREATE INDEX IDX_ExamPapers_BlockID   ON ExamPapers(BlockID);

CREATE INDEX IDX_Questions_ExamPaperID ON Questions(ExamPaperID);

CREATE INDEX IDX_TestCases_QuestionID  ON TestCases(QuestionID);

CREATE INDEX IDX_Submissions_StudentID ON Submissions(StudentID);
CREATE INDEX IDX_Submissions_BlockID   ON Submissions(BlockID);
CREATE INDEX IDX_Submissions_Status    ON Submissions(Status);

CREATE INDEX IDX_Answers_SubmissionID  ON Answers(SubmissionID);
CREATE INDEX IDX_Answers_QuestionID    ON Answers(QuestionID);

CREATE INDEX IDX_TestCaseResults_AnswerID   ON TestCaseResults(AnswerID);
CREATE INDEX IDX_TestCaseResults_TestCaseID ON TestCaseResults(TestCaseID);

CREATE INDEX IDX_AIReviews_AnswerID        ON AIReviews(AnswerID);

CREATE INDEX IDX_GradingResults_SubmissionID ON GradingResults(SubmissionID);

CREATE INDEX IDX_Appeals_StudentID          ON Appeals(StudentID);
CREATE INDEX IDX_Appeals_Status             ON Appeals(Status);
CREATE INDEX IDX_Appeals_AssignedLecturerID ON Appeals(AssignedLecturerID);

CREATE INDEX IDX_Payments_AppealID          ON Payments(AppealID);
CREATE INDEX IDX_Payments_Status            ON Payments(Status);
CREATE INDEX IDX_Payments_PayosOrderID      ON Payments(PayosOrderID);

CREATE INDEX IDX_Notifications_UserID       ON Notifications(UserID);
CREATE INDEX IDX_Notifications_IsRead       ON Notifications(IsRead);
CREATE INDEX IDX_Notifications_CreatedAt    ON Notifications(CreatedAt DESC);

CREATE INDEX IDX_AuditLogs_UserID           ON AuditLogs(UserID);
CREATE INDEX IDX_AuditLogs_Entity           ON AuditLogs(EntityType, EntityID);
CREATE INDEX IDX_AuditLogs_CreatedAt        ON AuditLogs(CreatedAt DESC);

-- ============================================================
-- SEED DATA
-- ============================================================

INSERT INTO Roles (Name, DisplayName, Description) VALUES
    ('SYSTEM_ADMIN', 'System Admin',  'Manages system configuration: AI, PayOS, Grading settings'),
    ('EXAM_STAFF',   'Exam Staff',    'Manages exams, triggers grading, handles appeals'),
    ('LECTURER',     'Lecturer',      'Reviews and re-grades assigned appeal submissions'),
    ('STUDENT',      'Student',       'Submits exam files, views results, submits appeals');

INSERT INTO GradingModeConfigs
    (Mode, DisplayName, TestCaseWeight, OopWeight, OopCommentOnly, FailIfZeroTestCase, FailIfOopViolated)
VALUES
    ('MODE_1', 'Test Case 100% + OOP Guard',   100, 0,   FALSE, FALSE, TRUE),
    ('MODE_2', 'Test Case 50% + OOP 50%',       50, 50,  FALSE, FALSE, FALSE),
    ('MODE_3', 'OOP 100% + Test Case Guard',     0, 100, FALSE, TRUE,  FALSE),
    ('MODE_4', 'Test Case 100% + OOP Comment', 100, 0,   TRUE,  FALSE, FALSE);

INSERT INTO SystemConfigs (ConfigKey, ConfigValue, IsEncrypted, Description) VALUES
    ('AI_PROVIDER',          'openai',                      FALSE, 'AI provider: openai or google'),
    ('AI_MODEL',             'gpt-4o',                      FALSE, 'AI model name'),
    ('AI_API_KEY',           '',                            TRUE,  'AI provider API key (encrypted)'),
    ('AI_LANGUAGE',          'vi',                          FALSE, 'AI review language: vi or en'),
    ('PAYOS_CLIENT_ID',      '',                            TRUE,  'PayOS Client ID (encrypted)'),
    ('PAYOS_API_KEY',        '',                            TRUE,  'PayOS API Key (encrypted)'),
    ('PAYOS_CHECKSUM_KEY',   '',                            TRUE,  'PayOS Checksum Key (encrypted)'),
    ('APPEAL_FEE',           '200000',                      FALSE, 'Appeal fee amount (VND)'),
    ('PAYMENT_TIMEOUT_MIN',  '15',                          FALSE, 'Payment timeout in minutes'),
    ('APPEAL_DEADLINE_DAYS', '7',                           FALSE, 'Lecturer deadline to complete appeal review (days)'),
    ('MAX_UPLOAD_SIZE_MB',   '50',                          FALSE, 'Max submission upload size (MB)'),
    ('MAX_EXAM_PAPER_MB',    '100',                         FALSE, 'Max exam paper upload size (MB)'),
    ('SMTP_HOST',            '',                            FALSE, 'SMTP server host'),
    ('SMTP_PORT',            '587',                         FALSE, 'SMTP server port'),
    ('SMTP_USERNAME',        '',                            TRUE,  'SMTP username (encrypted)'),
    ('SMTP_PASSWORD',        '',                            TRUE,  'SMTP password (encrypted)'),
    ('SMTP_FROM_EMAIL',      'noreply@oopexam.fpt.edu.vn', FALSE, 'Sender email address'),
    ('DEFAULT_GRADING_MODE', 'MODE_1',                     FALSE, 'Default grading mode');
