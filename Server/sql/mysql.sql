CREATE DATABASE IF NOT EXISTS hospital;
USE hospital;

DROP TABLE IF EXISTS Hospital_Records;
DROP TABLE IF EXISTS users;

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
    encrypted_audios LONGBLOB,
    doctor_encrypted_aes_key BLOB,
    nurse_encrypted_aes_key BLOB,
    UNIQUE(patient_id_hash),
    INDEX idx_name (patient_name),
    INDEX idx_doctor (doctor_name),
    INDEX idx_nurse (nurse_name)
);

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL
);

INSERT INTO users (username, password_hash, role) VALUES ('doctor_bob', '5a6cc72b29128f7b42d44158bb89f2de3478e5dfa41903dc57ec27ebe0ffef41', 'doctor');
INSERT INTO users (username, password_hash, role) VALUES ('nurse_akino', '5a6cc72b29128f7b42d44158bb89f2de3478e5dfa41903dc57ec27ebe0ffef41', 'nurse');