-- Drop existing tables
DROP TABLE IF EXISTS patient_records CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE patient_records (
    id SERIAL PRIMARY KEY,
    hashed_patient_id VARCHAR(64) NOT NULL,
    encrypted_name TEXT,
    encrypted_diagnosis TEXT,
    encrypted_treatment TEXT,
    encrypted_prescription TEXT,
    encrypted_media TEXT,
    media_type VARCHAR(50),
    created_by_role VARCHAR(20),
    allowed_roles VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pid ON patient_records(hashed_patient_id);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL
);

-- Enable RLS
ALTER TABLE patient_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Policies for anon key access (server-side)
CREATE POLICY "Allow all for anon" ON patient_records FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all for anon" ON users FOR ALL USING (true) WITH CHECK (true);

-- Grant permissions to anon role
GRANT ALL ON patient_records TO anon;
GRANT ALL ON users TO anon;
GRANT USAGE, SELECT ON SEQUENCE patient_records_id_seq TO anon;
GRANT USAGE, SELECT ON SEQUENCE users_id_seq TO anon;
