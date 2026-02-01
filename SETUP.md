# E2EE Hospital Record System - Setup Guide

## 🔐 Security Architecture

This system implements **true End-to-End Encryption (E2EE)** where:

| Component | Responsibility | Access to Plaintext |
|-----------|---------------|---------------------|
| Client (Browser) | Encryption & Decryption | ✅ YES |
| Server | Auth, RBAC, Routing | ❌ NO |
| Database | Encrypted Storage | ❌ NO |

**Key Security Rules:**
- ✅ Client private keys NEVER leave client devices
- ✅ Server NEVER decrypts medical data
- ✅ Database stores ONLY encrypted data
- ✅ RBAC enforced using metadata, not decrypted content

---

## 📋 Prerequisites

1. **Java 17+** (for server)
2. **MySQL 8.0+** or **Supabase PostgreSQL** (database)
3. **Web Browser** with Web Crypto API support (Chrome, Firefox, Edge)
4. **OpenSSL** or **Java keytool** (for certificate generation)

---

## 🚀 Quick Start

### Step 1: Generate Keys

```bash
# Generate RSA key pairs for doctor and nurse
cd mysql-Zero-trust

# Create directories
mkdir -p keys/doctor keys/nurse

# Generate Doctor keys
openssl genrsa -out keys/doctor/private.pem 2048
openssl rsa -in keys/doctor/private.pem -pubout -out keys/doctor/public.pem
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in keys/doctor/private.pem -out keys/doctor/private.key
openssl rsa -in keys/doctor/private.pem -pubout -out keys/doctor/public.key

# Generate Nurse keys
openssl genrsa -out keys/nurse/private.pem 2048
openssl rsa -in keys/nurse/private.pem -pubout -out keys/nurse/public.pem
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in keys/nurse/private.pem -out keys/nurse/private.key
openssl rsa -in keys/nurse/private.pem -pubout -out keys/nurse/public.key
```

Or use the Java KeyGen utility:
```bash
java KeyGen.java
```

### Step 2: Generate Server Certificate (for mTLS)

```bash
# Run the certificate setup tool
java CertificateSetup.java

# Select Option 1 to generate server keystore
# Select Option 2 to create client certificates (doctor, nurse)
```

### Step 3: Setup Database

**For MySQL:**
```bash
mysql -u root -p < sql/mysql_schema.sql
```

**For Supabase PostgreSQL:**
1. Go to Supabase Dashboard → SQL Editor
2. Run the contents of `sql/supabase_schema.sql`

### Step 4: Configure Database Type

Edit `src/util/DatabaseConfig.java`:

```java
// For MySQL (default)
private static DatabaseType DB_TYPE = DatabaseType.MYSQL;

// For Supabase PostgreSQL
private static DatabaseType DB_TYPE = DatabaseType.SUPABASE_POSTGRESQL;
```

Or pass command line argument:
```bash
java WebMain mysql    # Use MySQL
java WebMain supabase # Use Supabase
```

### Step 5: Compile and Run Server

```bash
# Compile
javac -d bin src/**/*.java

# Run server
java -cp bin WebMain
```

Server starts at: `https://localhost:8000`

---

## 🧪 Testing the System

### Test as Doctor

1. Open browser: `https://localhost:8000`
2. Accept self-signed certificate warning
3. Login with MySQL credentials (e.g., `root` / your-password)
4. **Load your private key:**
   - Click "Load Private Key"
   - Select `keys/doctor/private.key`
5. **Insert a record:**
   - Fill patient details
   - Enter symptoms and diagnosis
   - Click "Encrypt & Submit"
   - Data is encrypted in browser before sending!
6. **Search records:**
   - Search by name
   - Records are decrypted in browser after receiving

### Test as Nurse

1. Use a different browser or incognito mode
2. Use a client certificate with "nurse" in CN (if using mTLS)
3. Login and load `keys/nurse/private.key`
4. Search records - can view but not create/update

---

## 🔧 Client Certificate Setup (for mTLS)

Generate client certificates for mutual TLS:

```bash
java CertificateSetup.java

# Option 2: Create New Client Certificate
# Enter filename: doctor
# Enter password: (your choice)
# Enter server password: password (default)

# Repeat for nurse
```

Import client certificate in browser:
- **Chrome/Edge**: Settings → Privacy → Security → Manage certificates → Import
- **Firefox**: Options → Privacy → View Certificates → Import

---

## 📁 Project Structure

```
mysql-Zero-trust/
├── src/
│   ├── WebMain.java           # Server entry point
│   ├── model/
│   │   └── PatientRecord.java # Data model with RBAC fields
│   ├── repository/
│   │   ├── HospitalRepository.java
│   │   └── MySQLHospitalRepository.java
│   ├── server/
│   │   └── E2EEWebServer.java # E2EE server (no decryption!)
│   ├── util/
│   │   ├── DatabaseConfig.java    # DB type switch
│   │   ├── DatabaseFactory.java   # Connection factory
│   │   └── Hashing.java
│   └── web/
│       ├── index.html         # E2EE Web UI
│       └── crypto.js          # Client-side encryption
├── keys/
│   ├── doctor/
│   │   ├── private.key        # Doctor's private key (KEEP SECRET!)
│   │   └── public.key         # Doctor's public key
│   └── nurse/
│       ├── private.key        # Nurse's private key (KEEP SECRET!)
│       └── public.key         # Nurse's public key
├── clients/
│   └── doctor.p12             # Client certificate for mTLS
├── sql/
│   ├── mysql_schema.sql       # MySQL database schema
│   └── supabase_schema.sql    # PostgreSQL schema
└── src/certs/
    └── server.p12             # Server certificate
```

---

## 🔀 Switching Databases

### Option 1: Code Change

Edit `src/util/DatabaseConfig.java`:
```java
private static DatabaseType DB_TYPE = DatabaseType.MYSQL;
// or
private static DatabaseType DB_TYPE = DatabaseType.SUPABASE_POSTGRESQL;
```

### Option 2: Command Line

```bash
java -cp bin WebMain mysql     # MySQL
java -cp bin WebMain supabase  # Supabase PostgreSQL
```

### Option 3: Web UI

Select database type in login screen dropdown.

---

## 🛡️ Security Verification

### Verify Server Never Sees Plaintext

1. Open browser Developer Tools (F12)
2. Go to Network tab
3. Insert a record
4. Inspect the request payload:
   - `encryptedSymptoms`: Base64 gibberish ✅
   - `encryptedDiagnosis`: Base64 gibberish ✅
   - `symptoms`: NOT present ✅

### Verify Client-Side Encryption

1. Open browser Console
2. Type: `E2EECrypto`
3. You should see the crypto module with encryption methods

### Verify RBAC

1. Login as nurse
2. Try to insert a record
3. Should get: "Only doctors can create records"

---

## 🔑 Key Management Best Practices

1. **Private keys are SECRET** - Never share, never upload to server
2. **Backup private keys** - If lost, data cannot be decrypted
3. **Use strong passwords** for .p12 files
4. **Rotate keys periodically** - Re-encrypt data with new keys

---

## 📝 API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/login` | POST | - | Authenticate user |
| `/api/logout` | POST | Session | End session |
| `/api/user` | GET | Session | Get current user info |
| `/api/keys` | GET | Session | Get public keys for encryption |
| `/api/insert` | POST | Doctor | Store encrypted record |
| `/api/search` | GET | Any | Search encrypted records |
| `/api/update` | POST | Doctor | Update encrypted record |

---

## ⚠️ Troubleshooting

### "Certificate error" in browser
- Accept self-signed certificate warning
- Or import CA certificate into browser trust store

### "Failed to load private key"
- Ensure key is in PKCS#8 PEM format
- Check for extra whitespace in key file

### "Cannot decrypt"
- Verify you're using the correct role's private key
- Check that the record was encrypted for your role

### "Database connection failed"
- Check MySQL/PostgreSQL is running
- Verify credentials in DatabaseConfig
- For Supabase: Check connection pooler settings

---

## 📚 Cryptographic Details

| Algorithm | Purpose | Key Size |
|-----------|---------|----------|
| AES-256-GCM | Medical data encryption | 256 bits |
| RSA-OAEP (SHA-256) | AES key encryption | 2048 bits |
| SHA-256 | Password hashing, Patient ID hashing | 256 bits |
| TLS 1.3 | Transport security | - |

---

## 📄 License

This project is for educational purposes demonstrating E2EE architecture in healthcare systems.
