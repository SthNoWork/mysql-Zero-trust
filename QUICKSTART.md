# Quick Start Guide

## 1. Database Setup (MySQL)

Run in MySQL:
```sql
SOURCE Server/sql/mysql.sql
SOURCE Server/sql/users.sql
```

## 2. Start Server

```bash
cd Server
START_SERVER.bat
```

Server runs on `https://localhost:8000`

**IMPORTANT:** Open `https://localhost:8000` in your browser and accept the self-signed certificate warning before using the client.

## 3. Open Client

Open `Client/index.html` in browser

**Default test user (if you ran users.sql):**
- Check the users.sql file for actual credentials
- Password is hashed with SHA-256

## 4. Load Keys

After login, the app will prompt you to load your private key from `Client/keys/[role]_private.key`

---

## Troubleshooting

**"Failed to fetch"** → Server not running or cert not accepted. Visit https://localhost:8000 first.

**MySQL connection error** → Check `db.user` and `db.pass` in config.properties

**Login fails** → Verify user exists in database with: `SELECT * FROM users;`
