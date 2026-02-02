-- Add encrypted_audios column to Hospital_Records table
-- This is SAFE to run on existing data - it adds the column as NULL for existing records
-- Run this SQL on your MySQL database to add audio support

USE hospital;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'hospital' 
  AND TABLE_NAME = 'Hospital_Records' 
  AND COLUMN_NAME = 'encrypted_audios';

SET @query = IF(@col_exists = 0,
    'ALTER TABLE Hospital_Records ADD COLUMN encrypted_audios LONGBLOB NULL AFTER encrypted_videos',
    'SELECT "Column encrypted_audios already exists" AS message');

PREPARE stmt FROM @query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verify the column was added
DESCRIBE Hospital_Records;
