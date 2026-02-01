-- =====================================================
-- E2EE Hospital Records - PostgreSQL (Supabase) Schema
-- =====================================================
-- This schema supports End-to-End Encryption where:
-- - All medical data is stored ENCRYPTED
-- - Server NEVER decrypts the data
-- - RBAC uses metadata fields only
-- =====================================================

-- Create table in public schema
DROP TABLE IF EXISTS public.hospital_records;

CREATE TABLE public.hospital_records (
    -- Identity
    record_index SERIAL PRIMARY KEY,
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
    encrypted_images TEXT,                          -- AES-256-GCM encrypted images
    encrypted_videos TEXT,                          -- AES-256-GCM encrypted videos
    
    -- Encrypted AES Keys (Per recipient, RSA-2048 encrypted)
    doctor_encrypted_aes_key TEXT,                  -- AES key encrypted with doctor's public RSA key
    nurse_encrypted_aes_key TEXT                    -- AES key encrypted with nurse's public RSA key
);

-- Indexes for search performance
CREATE INDEX idx_patient_id_hash ON public.hospital_records(patient_id_hash);
CREATE INDEX idx_patient_name ON public.hospital_records(patient_name);
CREATE INDEX idx_patient_dob ON public.hospital_records(patient_dob);

-- Enable Row Level Security (optional - Supabase feature)
-- ALTER TABLE public.hospital_records ENABLE ROW LEVEL SECURITY;

-- Create policy for read access based on allowed_roles (optional)
-- CREATE POLICY "Users can read based on role" ON public.hospital_records
--     FOR SELECT USING (
--         allowed_roles LIKE '%' || current_setting('app.user_role', true) || '%'
--     );

SELECT 'PostgreSQL (Supabase) schema created successfully!' AS Status;
