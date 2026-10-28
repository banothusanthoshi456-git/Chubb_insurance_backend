SET @claims_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'claims'
);

SET @fingerprint_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'claims'
      AND column_name = 'submission_fingerprint'
);
SET @add_fingerprint_column_sql = IF(
    @claims_table_exists > 0 AND @fingerprint_column_exists = 0,
    'ALTER TABLE claims ADD COLUMN submission_fingerprint CHAR(64) NULL',
    'SELECT 1'
);
PREPARE add_fingerprint_column FROM @add_fingerprint_column_sql;
EXECUTE add_fingerprint_column;
DEALLOCATE PREPARE add_fingerprint_column;

SET @fingerprint_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'claims'
      AND index_name = 'uk_claims_submission_fingerprint'
      AND non_unique = 0
);
SET @add_fingerprint_index_sql = IF(
    @claims_table_exists > 0 AND @fingerprint_index_exists = 0,
    'ALTER TABLE claims
     ADD CONSTRAINT uk_claims_submission_fingerprint UNIQUE (submission_fingerprint)',
    'SELECT 1'
);
PREPARE add_fingerprint_index FROM @add_fingerprint_index_sql;
EXECUTE add_fingerprint_index;
DEALLOCATE PREPARE add_fingerprint_index;
