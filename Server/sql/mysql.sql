CREATE DATABASE IF NOT EXISTS hospital;
USE hospital;
CREATE TABLE IF NOT EXISTS patient_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    hashed_patient_id VARCHAR(64) NOT NULL,
    encrypted_name TEXT,
    encrypted_diagnosis TEXT,
    encrypted_treatment TEXT,
    encrypted_prescription TEXT,
    created_by_role VARCHAR(20),
    allowed_roles VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pid (hashed_patient_id)
);
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL
);
