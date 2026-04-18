-- V13: Fix auditlogs.ipaddress column type from inet to VARCHAR(45)
-- Reason: Hibernate 6 with Spring Boot 3.x does not automatically cast
--         Java String to PostgreSQL inet type, causing batch insert failures.
--         VARCHAR(45) is sufficient for both IPv4 (15 chars) and IPv6 (39 chars).
-- Note: PostgreSQL folds unquoted identifiers to lowercase, so table/column
--       names here must match the actual stored names (lowercase).

ALTER TABLE auditlogs
    ALTER COLUMN ipaddress TYPE VARCHAR(45)
        USING ipaddress::TEXT;
