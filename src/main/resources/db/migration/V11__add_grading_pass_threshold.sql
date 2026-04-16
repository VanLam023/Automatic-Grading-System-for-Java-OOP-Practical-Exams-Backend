-- ============================================================
-- Flyway V11: Add GRADING_PASS_THRESHOLD system config key
-- Minimum score threshold to determine PASS/FAIL after grading.
-- Default = 0: any score > 0 is considered PASS (backward-compatible).
-- Admin can change this value via the SystemConfigs table directly.
-- ON CONFLICT DO NOTHING — safe to re-run.
-- ============================================================

INSERT INTO systemconfigs (configkey, configvalue, isencrypted, description, updatedat)
VALUES (
    'GRADING_PASS_THRESHOLD',
    '0',
    FALSE,
    'Minimum score to pass an exam. Score must be GREATER THAN this value to be PASS. Default 0 = any score > 0 passes.',
    NOW()
)
ON CONFLICT (configkey) DO NOTHING;
