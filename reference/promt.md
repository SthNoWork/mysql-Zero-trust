-- 1. Create the 'container'
CREATE SCHEMA IF NOT EXISTS hospital;

-- 2. Use the container
USE hospital;

-- 3. Create the table
CREATE TABLE Hospital_Records (
    record_index INT PRIMARY KEY AUTO_INCREMENT,
    patient_id_hash CHAR(64) NOT NULL,
    patient_name VARCHAR(100),
    patient_dob DATE,
    doctor_name VARCHAR(100),
    nurse_name VARCHAR(100),

    check_in_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    encrypted_symptoms BLOB,
    encrypted_diagnosis BLOB,
    encrypted_images LONGBLOB, 
    encrypted_videos LONGBLOB,
    doctor_encrypted_aes_key BLOB,
    nurse_encrypted_aes_key BLOB,
    UNIQUE(patient_id_hash)
);