-- ============================================================
-- Flyway V16: Create grading_criteria table
-- Stores structural OOP grading criteria per question.
-- Lecturer inputs these when creating exam questions.
-- criterion_type maps to CriterionType enum (10 types).
-- check_params is JSONB used by the deterministic engine.
-- ============================================================

CREATE TABLE grading_criteria (
    criteria_id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID        NOT NULL REFERENCES questions(questionid) ON DELETE CASCADE,
    criteria_code   VARCHAR(20) NOT NULL,
    criteria_group  VARCHAR(20) NOT NULL DEFAULT 'STRUCTURAL'
                        CHECK (criteria_group IN ('STRUCTURAL', 'BEHAVIORAL')),
    criterion_type  VARCHAR(50) NOT NULL,
    description     TEXT        NOT NULL,
    max_score       NUMERIC(6,4) NOT NULL CHECK (max_score >= 0),
    check_params    JSONB       NOT NULL DEFAULT '{}',
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_grading_criteria_code UNIQUE (question_id, criteria_code)
);

CREATE INDEX idx_grading_criteria_question
    ON grading_criteria(question_id);

COMMENT ON TABLE  grading_criteria                IS 'OOP structural grading criteria per question. Each row = 1 sub-criterion checked by JavaParser/Reflection.';
COMMENT ON COLUMN grading_criteria.criteria_code  IS 'Human-readable code, e.g. Q1.1, Q2.3. Must be unique per question.';
COMMENT ON COLUMN grading_criteria.criteria_group IS 'STRUCTURAL = JavaParser/Reflection. BEHAVIORAL = TestCase (reserved for future).';
COMMENT ON COLUMN grading_criteria.criterion_type IS 'Maps to CriterionType enum: CLASS_EXISTS, FIELD_CHECK, CONSTRUCTOR_CHECK, METHOD_SIGNATURE, GETTER_SETTER, EXTENDS_CHECK, IMPLEMENTS_CHECK, INTERFACE_EXISTS, NAMING_CONVENTION.';
COMMENT ON COLUMN grading_criteria.check_params   IS 'JSONB params consumed by the corresponding CriterionHandler. Schema varies by criterion_type.';
