CREATE DATABASE IF NOT EXISTS hospital;
USE hospital;

DROP TABLE IF EXISTS patient_records;
DROP TABLE IF EXISTS users;

CREATE TABLE patient_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    hashed_patient_id VARCHAR(64) NOT NULL,
    encrypted_name TEXT,
    encrypted_diagnosis TEXT,
    encrypted_treatment TEXT,
    encrypted_prescription TEXT,
    encrypted_media LONGTEXT,
    media_type VARCHAR(50),
    created_by_role VARCHAR(20),
    allowed_roles VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pid (hashed_patient_id)
);

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL
);
