# Quick Start Guide

## For Supabase

### 1. Database Setup
1. Open your Supabase project SQL Editor
2. Run `Server/sql/postgresql.sql`
3. Run `Server/sql/users.sql`

### 2. Start Server
```bash
SUPABASE_START.bat
```

### 3. Accept Certificate
Open `https://localhost:8000` in browser → accept self-signed cert warning

### 4. Open Client
Open `Client/index.html` → Login: `doctor_bob` / `password123`

---

## For MySQL

### 1. Database Setup
```sql
SOURCE Server/sql/mysql.sql
SOURCE Server/sql/users.sql
```

### 2. Edit config.properties
Change `db.type=supabase` to `db.type=mysql` and uncomment MySQL settings

### 3. Start
```bash
cd Server
START_SERVER.bat
```

---

## Troubleshooting

**"Failed to fetch"** → Visit https://localhost:8000 first to accept cert

**Supabase errors** → Check RLS policies are created (postgresql.sql includes them)

**Login fails** → Verify user in Supabase: `SELECT * FROM users;`
