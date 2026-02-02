-- Test user: doctor_bob / password123
-- password123 hashed with SHA-256
INSERT INTO users (username, password_hash, role) VALUES ('doctor_bob', '5a6cc72b29128f7b42d44158bb89f2de3478e5dfa41903dc57ec27ebe0ffef41', 'doctor');
INSERT INTO users (username, password_hash, role) VALUES ('nurse_akino', '5a6cc72b29128f7b42d44158bb89f2de3478e5dfa41903dc57ec27ebe0ffef41', 'nurse');