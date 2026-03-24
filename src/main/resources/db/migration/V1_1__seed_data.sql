-- ============================================================
-- SEED DATA: Initial reference data
-- Flyway V1.1: Seed Data
-- ON CONFLICT DO NOTHING — safe to re-run on existing DB
-- ============================================================

-- Roles
INSERT INTO Roles (Name, DisplayName, Description) VALUES
    ('SYSTEM_ADMIN', 'System Admin',  'Manages system configuration: AI, PayOS, Grading settings'),
    ('EXAM_STAFF',   'Exam Staff',    'Manages exams, triggers grading, handles appeals'),
    ('LECTURER',     'Lecturer',      'Reviews and re-grades assigned appeal submissions'),
    ('STUDENT',      'Student',       'Submits exam files, views results, submits appeals')
ON CONFLICT (Name) DO NOTHING;

-- Grading Mode Configs
INSERT INTO GradingModeConfigs
    (Mode, DisplayName, TestCaseWeight, OopWeight, OopCommentOnly, FailIfZeroTestCase, FailIfOopViolated)
VALUES
    ('MODE_1', 'Test Case 100% + OOP Guard',   100, 0,   FALSE, FALSE, TRUE),
    ('MODE_2', 'Test Case 50% + OOP 50%',       50, 50,  FALSE, FALSE, FALSE),
    ('MODE_3', 'OOP 100% + Test Case Guard',     0, 100, FALSE, TRUE,  FALSE),
    ('MODE_4', 'Test Case 100% + OOP Comment', 100, 0,   TRUE,  FALSE, FALSE)
ON CONFLICT (Mode) DO NOTHING;

-- System Configs
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
    ('DEFAULT_GRADING_MODE', 'MODE_1',                     FALSE, 'Default grading mode')
ON CONFLICT (ConfigKey) DO NOTHING;
