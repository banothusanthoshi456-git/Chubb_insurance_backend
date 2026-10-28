CREATE TABLE IF NOT EXISTS claimants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

DROP TEMPORARY TABLE IF EXISTS claimant_email_survivors;
CREATE TEMPORARY TABLE claimant_email_survivors AS
    SELECT LOWER(TRIM(email)) AS normalized_email, MIN(id) AS survivor_id
    FROM claimants
    GROUP BY LOWER(TRIM(email));

SET @claims_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'claims'
);
SET @remap_claims_sql = IF(
    @claims_table_exists > 0,
    'UPDATE claims AS c
     JOIN claimants AS duplicate_claimant ON duplicate_claimant.id = c.claimant_id
     JOIN claimant_email_survivors AS survivor
       ON survivor.normalized_email = LOWER(TRIM(duplicate_claimant.email))
     SET c.claimant_id = survivor.survivor_id
     WHERE duplicate_claimant.id <> survivor.survivor_id',
    'SELECT 1'
);
PREPARE remap_claims FROM @remap_claims_sql;
EXECUTE remap_claims;
DEALLOCATE PREPARE remap_claims;

DELETE duplicate_claimant
FROM claimants AS duplicate_claimant
JOIN claimant_email_survivors AS survivor
  ON survivor.normalized_email = LOWER(TRIM(duplicate_claimant.email))
WHERE duplicate_claimant.id <> survivor.survivor_id;

UPDATE claimants SET email = LOWER(TRIM(email));
DROP TEMPORARY TABLE claimant_email_survivors;

SET @normalized_email_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'claimants'
      AND index_name = 'uk_claimants_normalized_email'
      AND non_unique = 0
);
SET @add_normalized_email_index_sql = IF(
    @normalized_email_index_exists = 0,
    'ALTER TABLE claimants
     ADD CONSTRAINT uk_claimants_normalized_email UNIQUE (email)',
    'SELECT 1'
);
PREPARE add_normalized_email_index FROM @add_normalized_email_index_sql;
EXECUTE add_normalized_email_index;
DEALLOCATE PREPARE add_normalized_email_index;
