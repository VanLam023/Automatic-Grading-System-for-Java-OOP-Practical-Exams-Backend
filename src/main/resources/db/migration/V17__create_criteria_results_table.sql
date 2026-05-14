-- ============================================================
-- Flyway V17: Create criteria_results table
-- Stores the result of each structural criterion check per answer.
-- Pattern mirrors testcaseresults (1 answer → N rows).
-- OOP score for an answer = SUM(earned_score) from this table.
-- ============================================================

CREATE TABLE criteria_results (
    criteria_result_id  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id           UUID         NOT NULL REFERENCES answers(answerid) ON DELETE CASCADE,
    criteria_id         UUID         NOT NULL REFERENCES grading_criteria(criteria_id) ON DELETE CASCADE,
    passed              BOOLEAN      NOT NULL DEFAULT false,
    earned_score        NUMERIC(6,4) NOT NULL DEFAULT 0 CHECK (earned_score >= 0),
    feedback            TEXT,
    executed_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_criteria_results_answer_criteria UNIQUE (answer_id, criteria_id)
);

CREATE INDEX idx_criteria_results_answer
    ON criteria_results(answer_id);

COMMENT ON TABLE  criteria_results              IS 'Result of each structural OOP criterion check per answer. OOP score = SUM(earned_score) grouped by answer_id.';
COMMENT ON COLUMN criteria_results.passed       IS 'True if the criterion was satisfied completely.';
COMMENT ON COLUMN criteria_results.earned_score IS 'Points earned. 0 if failed. May be partial if criterion supports partial credit.';
COMMENT ON COLUMN criteria_results.feedback     IS 'Human-readable explanation, e.g. "Field name is public, expected private".';
