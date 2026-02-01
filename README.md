# E2EE Hospital Records

End-to-end encrypted medical records. Server never sees plaintext.

## Permissions
| Role | Create | Read | Update | Delete |
|------|--------|------|--------|--------|
| Doctor | ✓ | ✓ | ✓ | ✗ |
| Nurse | ✓ | ✓ | ✗ | ✗ |

## Quick Setup

### 1. Admin Panel (run first)
Open `Admin/index.html` in browser.

**Generate in this order:**

```
Admin/index.html
├── RSA Keys tab     → doctor_public.key, doctor_private.key
│                    → nurse_public.key, nurse_private.key
├── Server Cert tab  → generate_server.bat, export_server_public.bat
├── Client Certs tab → generate_[name].bat, add_[name]_to_server.bat
├── Users tab        → users.sql
└── Database tab     → config.properties
```

### 2. Server Setup

```
Server/
├── certs/
│   ├── server.p12      ← Run generate_server.bat HERE
│   └── truststore.p12  ← Run add_[name]_to_server.bat HERE
├── config.properties   ← From Admin
├── Server.java
└── sql/
    └── mysql.sql       ← Run in MySQL + users.sql
```

**Commands (run in Server/certs/):**
```batch
# 1. Generate server cert
generate_server.bat

# 2. Export for clients
export_server_public.bat

# 3. For each client
add_doctor_smith_to_server.bat
```

### 3. Client Setup

```
Client/
├── certs/
│   ├── [name].p12     ← Client runs generate_[name].bat HERE
│   └── server.cer     ← Copy from Server/certs/
├── keys/
│   ├── [role]_private.key  ← Their private key only
│   ├── doctor_public.key   ← All public keys
│   └── nurse_public.key
└── index.html
```

**Give to each client:**
- `generate_[name].bat` - they run it to make their .p12
- `server.cer` - to verify server
- `[role]_private.key` - their private key
- All `*_public.key` files

## Folder Structure

```
mysql-Zero-trust/
├── Admin/          ← Admin tools (don't share)
│   └── index.html
├── Client/         ← Give to users
│   ├── index.html
│   ├── certs/
│   └── keys/
├── Server/         ← Your server
│   ├── Server.java
│   ├── config.properties
│   ├── certs/
│   ├── lib/
│   └── sql/
└── README.md
```

## mTLS Flow

```
1. Server proves identity → server.cer → clients verify
2. Client proves identity → [name].p12 → server checks truststore
```

Only clients in `truststore.p12` can connect.
