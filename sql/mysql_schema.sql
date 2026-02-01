-- =====================================================
-- E2EE Hospital Records - MySQL Schema
-- =====================================================
-- This schema supports End-to-End Encryption where:
-- - All medical data is stored ENCRYPTED
-- - Server NEVER decrypts the data
-- - RBAC uses metadata fields only
-- =====================================================

CREATE DATABASE IF NOT EXISTS hospital;
USE hospital;

DROP TABLE IF EXISTS Hospital_Records;

CREATE TABLE Hospital_Records (
    -- Identity
    record_index INT AUTO_INCREMENT PRIMARY KEY,
    patient_id_hash VARCHAR(64) NOT NULL,          -- SHA-256 hash of patient ID
    
    -- Metadata (Searchable, used for RBAC)
    patient_name VARCHAR(255) NOT NULL,
    patient_dob DATE NOT NULL,
    check_in_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    doctor_name VARCHAR(255),
    nurse_name VARCHAR(255),
    
    -- RBAC Metadata
    created_by VARCHAR(255),                        -- User who created this record
    created_by_role VARCHAR(50),                    -- Role of creator (doctor/nurse/admin)
    allowed_roles VARCHAR(255) DEFAULT 'doctor,nurse',  -- Comma-separated roles that can access
    recipient_ids TEXT,                             -- Comma-separated user IDs who can decrypt
    
    -- Encrypted Medical Data (Base64 encoded ciphertext)
    encrypted_symptoms TEXT,                        -- AES-256-GCM encrypted symptoms
    encrypted_diagnosis TEXT,                       -- AES-256-GCM encrypted diagnosis
    encrypted_images LONGTEXT,                      -- AES-256-GCM encrypted images
    encrypted_videos LONGTEXT,                      -- AES-256-GCM encrypted videos
    
    -- Encrypted AES Keys (Per recipient, RSA-2048 encrypted)
    doctor_encrypted_aes_key TEXT,                  -- AES key encrypted with doctor's public RSA key
    nurse_encrypted_aes_key TEXT,                   -- AES key encrypted with nurse's public RSA key
    
    -- Indexes for search performance
    INDEX idx_patient_id_hash (patient_id_hash),
    INDEX idx_patient_name (patient_name),
    INDEX idx_patient_dob (patient_dob),
    INDEX idx_allowed_roles (allowed_roles(100))
);

-- Create users table for authentication (optional, if not using DB users)
CREATE TABLE IF NOT EXISTS Users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,             -- SHA-256 hash
    role VARCHAR(50) NOT NULL,                      -- doctor, nurse, admin
    public_key TEXT,                                -- User's RSA public key
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample users (passwords are SHA-256 hashes)
-- Password for all: 'password123'
INSERT INTO Users (username, password_hash, role) VALUES
('doctor_smith', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'doctor'),
('nurse_jones', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'nurse');

SELECT 'MySQL schema created successfully!' AS Status;
