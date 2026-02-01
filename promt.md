Try to use small amount of tokens

Secure Hospital Record System – REFACTORED SPECIFICATION
Objective

Refactor the existing hospital record system to remove all server-side decryption and eliminate storage or use of client private keys on the web server. Implement a true end-to-end encryption (E2EE) architecture where all medical data encryption and decryption occur only on client devices, while the server enforces authentication, authorization (RBAC), routing, and persistence without accessing plaintext data.

Core Security Rules (Non-Negotiable)

Client private keys must never leave client devices

Server must never decrypt medical data

Database must only store encrypted data

Transport security is handled by mTLS, not application RSA

RBAC is enforced using metadata, not decrypted content

Cryptography Model

Symmetric encryption: AES-256 (for medical data)

Asymmetric encryption: RSA-2048 (for encrypting AES keys)

Hashing: SHA-256 or stronger (for passwords only)

Transport security: mTLS (TLS certificates)

CLIENT LOGIC (Doctor / Nurse Devices)
Client Security Assets

Each client device has:

Its own RSA key pair

Private key stored locally on the device

Public key registered with the server

A client TLS certificate for mTLS

Client trusts:

Server TLS certificate

Public RSA keys of other authorized clients

Client Authentication Flow

Client connects to server using mTLS

Client presents certificate

Server verifies certificate

Client submits login credentials (username + password)

Server authenticates credentials and returns:

User role (doctor / nurse / admin)

List of authorized recipient public keys

Client Encryption Flow (WRITE)

Client generates a random AES-256 key

Client encrypts medical data using AES-256

Client encrypts the AES key separately using:

Doctor public RSA key

Nurse public RSA key

Client sends to server:

Encrypted medical data

Encrypted AES keys (per recipient)

Metadata (record ID, sender ID, recipient IDs)

Client NEVER sends private keys

Client Decryption Flow (READ)

Client receives encrypted medical data and encrypted AES key

Client decrypts AES key using its private RSA key

Client decrypts medical data locally using AES key

SERVER LOGIC (Web Server)
Server Security Assets

Server holds:

TLS private key (for HTTPS/mTLS only)

User credentials (username + hashed password)

Client public RSA keys

Server does NOT hold:

Client private keys

Application-level RSA keys for medical data

Server Authentication & RBAC

Server authenticates users using:

Valid client TLS certificate

Username/password verification

Server enforces RBAC using metadata only:

Doctors → create and read records

Nurses → read records

Admins → manage users, roles, keys

Server validates role permissions BEFORE database access

Server Data Handling

Server receives encrypted payloads from clients

Server:

Verifies sender authorization

Stores encrypted data and encrypted AES keys in database

Retrieves encrypted records when authorized

Forwards encrypted data to clients unchanged

Server NEVER decrypts or encrypts medical data

DATABASE LOGIC

Database stores:

Encrypted medical data (ciphertext)

Encrypted AES keys

Metadata (timestamps, sender, recipients, roles)

Database has:

Full encrypted backups

Incremental encrypted backups

Database never sees plaintext data

RBAC WITHOUT DECRYPTION

RBAC is enforced using metadata fields, e.g.:

record_owner_role

allowed_roles

recipient_ids

Example rule:

IF user.role ∈ allowed_roles
ALLOW fetch
ELSE
DENY access

TRANSPORT SECURITY

All client ↔ server communication uses mTLS

TLS keys are strictly for transport

TLS is NOT used for application data encryption

FINAL ARCHITECTURE SUMMARY

Encryption/decryption: Client-side only

Server role: authentication, RBAC, routing, logging

Database role: encrypted storage only

Private keys: stored only on client devices

Server cannot read medical data even if compromised

Extra requirement:

Tell me how to set up the client and the webserver to test
DOnt remove any credentials cuz i will use it again
Also make it easy to switch between mysql server and supabase postgressql
