# E2EE Hospital Records

End-to-end encrypted medical records. Server never sees plaintext.

## Permissions
| Role | Create | Read | Update | Delete |
|------|--------|------|--------|--------|
| Doctor | ✓ | ✓ | ✓ | ✗ |
| Nurse | ✓ | ✓ | ✗ | ✗ |

---

## .BAT Files - Where & Order

### Step 1: Server Certificate (Admin runs)
| File | Run In | Output | Purpose |
|------|--------|--------|---------|
| `generate_server.bat` | `Server/certs/` | `server.p12` | Server's identity |
| `export_server_public.bat` | `Server/certs/` | `server.cer` | Give to ALL clients |

### Step 2: Client Certificates (Per user)
| File | Run In | Output | Purpose |
|------|--------|--------|---------|
| `generate_[name].bat` | `Client/certs/` | `[name].p12` | Client runs this themselves |
| `add_[name]_to_server.bat` | `Server/certs/` | Updates `truststore.p12` | Admin runs after getting client cert |

### Order of Operations
```
1. Admin: generate_server.bat        → Server/certs/server.p12
2. Admin: export_server_public.bat   → Server/certs/server.cer
3. Admin: Copy server.cer to each    → Client/certs/server.cer
4. Client: generate_[name].bat       → Client/certs/[name].p12
5. Client: Send [name].cer to Admin  
6. Admin: add_[name]_to_server.bat   → Server/certs/truststore.p12
```

---

## Where Keys Go

### Client Private Key Location
```
Client/
└── keys/
    └── [role]_private.key    ← YOUR private key (NEVER share!)
```

**Example for doctor_smith:**
```
Client/
├── certs/
│   ├── doctor_smith.p12      ← Your mTLS cert
│   └── server.cer            ← To verify server
└── keys/
    ├── doctor_private.key    ← YOUR decryption key (KEEP SECRET!)
    ├── doctor_public.key     ← For encrypting to doctors
    └── nurse_public.key      ← For encrypting to nurses
```

---

## Complete Folder Structure

```
mysql-Zero-trust/
├── Admin/                    ← ADMIN ONLY (don't distribute)
│   └── index.html           
│
├── Server/
│   ├── certs/               ← Run server .bats HERE
│   │   ├── server.p12       ← From generate_server.bat
│   │   ├── server.cer       ← From export_server_public.bat
│   │   └── truststore.p12   ← From add_[name]_to_server.bat
│   ├── config.properties
│   ├── Server.java
│   └── sql/
│
└── Client/                   ← Give to each user
    ├── certs/               ← Run client .bats HERE
    │   ├── [name].p12       ← From generate_[name].bat
    │   └── server.cer       ← Copy from Server/certs/
    ├── keys/
    │   ├── [role]_private.key   ← THEIR private key only
    │   ├── doctor_public.key    ← All public keys
    │   └── nurse_public.key
    └── index.html
```

---

## Quick Setup Checklist

### Admin Does:
- [ ] Open `Admin/index.html`
- [ ] RSA Keys tab → Generate for each role
- [ ] Server Cert tab → Download & run `generate_server.bat` in `Server/certs/`
- [ ] Server Cert tab → Download & run `export_server_public.bat` in `Server/certs/`
- [ ] For each user: Client Certs tab → Download scripts
- [ ] Users tab → Create users → Download `users.sql`
- [ ] Database tab → Download `config.properties`
- [ ] Run SQL scripts in database

### Each Client Gets:
- [ ] `generate_[name].bat` → Run in `Client/certs/`
- [ ] `server.cer` → Place in `Client/certs/`
- [ ] `[role]_private.key` → Place in `Client/keys/` (KEEP SECRET!)
- [ ] All `*_public.key` files → Place in `Client/keys/`
- [ ] `Client/index.html`

### Client Returns to Admin:
- [ ] `[name].cer` (exported from their .p12)

### Admin Finishes:
- [ ] Run `add_[name]_to_server.bat` in `Server/certs/` for each client

---

## How Certificate Authentication Works

1. **Role is embedded in certificate**: When generating client certificates, the role (doctor/nurse) is embedded in the OU (Organizational Unit) field
2. **Server extracts role**: The server automatically extracts the role from the certificate's OU field during mTLS handshake
3. **No manual selection needed**: Users don't choose their role - it's determined by their certificate
4. **Encryption is automatic**: Records are encrypted for all available public keys (doctor + nurse keys)