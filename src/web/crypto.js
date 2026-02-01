/**
 * E2EE Crypto Module for Hospital Record System
 * 
 * SECURITY ARCHITECTURE:
 * - All encryption/decryption happens CLIENT-SIDE only
 * - Private keys NEVER leave this device
 * - Server only sees encrypted data
 * 
 * Algorithms:
 * - AES-256-GCM for data encryption
 * - RSA-OAEP (2048-bit) for key encryption
 * - SHA-256 for hashing
 */

const E2EECrypto = {

    // ==================== CONSTANTS ====================
    AES_KEY_LENGTH: 256,
    RSA_KEY_LENGTH: 2048,
    GCM_IV_LENGTH: 12,
    GCM_TAG_LENGTH: 128,

    // ==================== KEY MANAGEMENT ====================

    /**
     * Store private key securely in IndexedDB
     */
    async storePrivateKey(role, privateKey) {
        const db = await this.openKeyStore();
        const tx = db.transaction('keys', 'readwrite');
        const store = tx.objectStore('keys');
        
        // Export key to JWK for storage
        const jwk = await crypto.subtle.exportKey('jwk', privateKey);
        store.put({ id: `${role}_private`, key: jwk });
        
        return new Promise((resolve, reject) => {
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error);
        });
    },

    /**
     * Load private key from IndexedDB
     */
    async loadPrivateKey(role) {
        const db = await this.openKeyStore();
        const tx = db.transaction('keys', 'readonly');
        const store = tx.objectStore('keys');
        
        return new Promise((resolve, reject) => {
            const request = store.get(`${role}_private`);
            request.onsuccess = async () => {
                if (request.result) {
                    const key = await crypto.subtle.importKey(
                        'jwk',
                        request.result.key,
                        { name: 'RSA-OAEP', hash: 'SHA-256' },
                        true,
                        ['decrypt']
                    );
                    resolve(key);
                } else {
                    resolve(null);
                }
            };
            request.onerror = () => reject(request.error);
        });
    },

    /**
     * Import private key from PEM string
     */
    async importPrivateKeyFromPEM(pemString) {
        // Remove PEM headers and decode base64
        const pemContents = pemString
            .replace('-----BEGIN PRIVATE KEY-----', '')
            .replace('-----END PRIVATE KEY-----', '')
            .replace(/\s/g, '');
        
        const binaryDer = this.base64ToArrayBuffer(pemContents);
        
        return await crypto.subtle.importKey(
            'pkcs8',
            binaryDer,
            { name: 'RSA-OAEP', hash: 'SHA-256' },
            true,
            ['decrypt']
        );
    },

    /**
     * Import public key from PEM string
     */
    async importPublicKeyFromPEM(pemString) {
        // Remove PEM headers and decode base64
        const pemContents = pemString
            .replace('-----BEGIN PUBLIC KEY-----', '')
            .replace('-----END PUBLIC KEY-----', '')
            .replace(/\s/g, '');
        
        const binaryDer = this.base64ToArrayBuffer(pemContents);
        
        return await crypto.subtle.importKey(
            'spki',
            binaryDer,
            { name: 'RSA-OAEP', hash: 'SHA-256' },
            true,
            ['encrypt']
        );
    },

    /**
     * Open IndexedDB for key storage
     */
    openKeyStore() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open('E2EE_KeyStore', 1);
            request.onerror = () => reject(request.error);
            request.onsuccess = () => resolve(request.result);
            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                if (!db.objectStoreNames.contains('keys')) {
                    db.createObjectStore('keys', { keyPath: 'id' });
                }
            };
        });
    },

    // ==================== AES ENCRYPTION ====================

    /**
     * Generate a random AES-256 key
     */
    async generateAESKey() {
        return await crypto.subtle.generateKey(
            { name: 'AES-GCM', length: this.AES_KEY_LENGTH },
            true,
            ['encrypt', 'decrypt']
        );
    },

    /**
     * Encrypt data using AES-256-GCM
     * Returns: Base64(IV + Ciphertext + Tag)
     */
    async encryptWithAES(plaintext, aesKey) {
        const encoder = new TextEncoder();
        const data = encoder.encode(plaintext);
        
        // Generate random IV
        const iv = crypto.getRandomValues(new Uint8Array(this.GCM_IV_LENGTH));
        
        // Encrypt
        const ciphertext = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv: iv, tagLength: this.GCM_TAG_LENGTH },
            aesKey,
            data
        );
        
        // Combine IV + Ciphertext
        const result = new Uint8Array(iv.length + ciphertext.byteLength);
        result.set(iv, 0);
        result.set(new Uint8Array(ciphertext), iv.length);
        
        return this.arrayBufferToBase64(result.buffer);
    },

    /**
     * Decrypt AES-256-GCM encrypted data
     * Input: Base64(IV + Ciphertext + Tag)
     */
    async decryptWithAES(encryptedBase64, aesKey) {
        const encrypted = this.base64ToArrayBuffer(encryptedBase64);
        const encryptedArray = new Uint8Array(encrypted);
        
        // Extract IV and ciphertext
        const iv = encryptedArray.slice(0, this.GCM_IV_LENGTH);
        const ciphertext = encryptedArray.slice(this.GCM_IV_LENGTH);
        
        // Decrypt
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: iv, tagLength: this.GCM_TAG_LENGTH },
            aesKey,
            ciphertext
        );
        
        const decoder = new TextDecoder();
        return decoder.decode(decrypted);
    },

    /**
     * Encrypt binary data (images/videos) using AES-256-GCM
     */
    async encryptBytesWithAES(data, aesKey) {
        const iv = crypto.getRandomValues(new Uint8Array(this.GCM_IV_LENGTH));
        
        const ciphertext = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv: iv, tagLength: this.GCM_TAG_LENGTH },
            aesKey,
            data
        );
        
        const result = new Uint8Array(iv.length + ciphertext.byteLength);
        result.set(iv, 0);
        result.set(new Uint8Array(ciphertext), iv.length);
        
        return this.arrayBufferToBase64(result.buffer);
    },

    /**
     * Decrypt binary data using AES-256-GCM
     */
    async decryptBytesWithAES(encryptedBase64, aesKey) {
        const encrypted = this.base64ToArrayBuffer(encryptedBase64);
        const encryptedArray = new Uint8Array(encrypted);
        
        const iv = encryptedArray.slice(0, this.GCM_IV_LENGTH);
        const ciphertext = encryptedArray.slice(this.GCM_IV_LENGTH);
        
        return await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: iv, tagLength: this.GCM_TAG_LENGTH },
            aesKey,
            ciphertext
        );
    },

    // ==================== RSA KEY ENCRYPTION ====================

    /**
     * Encrypt AES key using RSA public key
     */
    async encryptAESKeyWithRSA(aesKey, rsaPublicKey) {
        // Export AES key to raw bytes
        const rawKey = await crypto.subtle.exportKey('raw', aesKey);
        
        // Encrypt with RSA-OAEP
        const encrypted = await crypto.subtle.encrypt(
            { name: 'RSA-OAEP' },
            rsaPublicKey,
            rawKey
        );
        
        return this.arrayBufferToBase64(encrypted);
    },

    /**
     * Decrypt AES key using RSA private key
     */
    async decryptAESKeyWithRSA(encryptedKeyBase64, rsaPrivateKey) {
        const encryptedKey = this.base64ToArrayBuffer(encryptedKeyBase64);
        
        // Decrypt with RSA-OAEP
        const rawKey = await crypto.subtle.decrypt(
            { name: 'RSA-OAEP' },
            rsaPrivateKey,
            encryptedKey
        );
        
        // Import as AES key
        return await crypto.subtle.importKey(
            'raw',
            rawKey,
            { name: 'AES-GCM', length: this.AES_KEY_LENGTH },
            true,
            ['encrypt', 'decrypt']
        );
    },

    // ==================== HIGH-LEVEL ENCRYPT/DECRYPT ====================

    /**
     * Encrypt medical data for multiple recipients
     * 
     * @param {Object} data - { symptoms, diagnosis, images[], videos[] }
     * @param {Object} publicKeys - { doctor: CryptoKey, nurse: CryptoKey }
     * @returns {Object} - Encrypted payload for server
     */
    async encryptMedicalData(data, publicKeys) {
        // Generate a single AES key for this record
        const aesKey = await this.generateAESKey();
        
        // Encrypt text data
        const encryptedSymptoms = data.symptoms ? 
            await this.encryptWithAES(data.symptoms, aesKey) : '';
        const encryptedDiagnosis = data.diagnosis ? 
            await this.encryptWithAES(data.diagnosis, aesKey) : '';
        
        // Encrypt images (if any)
        let encryptedImages = '';
        if (data.images && data.images.length > 0) {
            // For simplicity, encrypt first image
            const imageData = await this.fileToArrayBuffer(data.images[0]);
            encryptedImages = await this.encryptBytesWithAES(imageData, aesKey);
        }
        
        // Encrypt videos (if any)
        let encryptedVideos = '';
        if (data.videos && data.videos.length > 0) {
            const videoData = await this.fileToArrayBuffer(data.videos[0]);
            encryptedVideos = await this.encryptBytesWithAES(videoData, aesKey);
        }
        
        // Encrypt AES key for each recipient
        const doctorEncryptedAesKey = publicKeys.doctor ? 
            await this.encryptAESKeyWithRSA(aesKey, publicKeys.doctor) : '';
        const nurseEncryptedAesKey = publicKeys.nurse ? 
            await this.encryptAESKeyWithRSA(aesKey, publicKeys.nurse) : '';
        
        return {
            encryptedSymptoms,
            encryptedDiagnosis,
            encryptedImages,
            encryptedVideos,
            doctorEncryptedAesKey,
            nurseEncryptedAesKey
        };
    },

    /**
     * Decrypt medical data using private key
     * 
     * @param {Object} encryptedData - Encrypted payload from server
     * @param {CryptoKey} privateKey - User's RSA private key
     * @param {String} role - 'doctor' or 'nurse'
     * @returns {Object} - Decrypted data
     */
    async decryptMedicalData(encryptedData, privateKey, role) {
        // Get the correct encrypted AES key based on role
        const encryptedAesKey = role === 'doctor' ? 
            encryptedData.doctorEncryptedAesKey : 
            encryptedData.nurseEncryptedAesKey;
        
        if (!encryptedAesKey) {
            throw new Error('No encrypted key available for your role');
        }
        
        // Decrypt AES key
        const aesKey = await this.decryptAESKeyWithRSA(encryptedAesKey, privateKey);
        
        // Decrypt text data
        const symptoms = encryptedData.encryptedSymptoms ? 
            await this.decryptWithAES(encryptedData.encryptedSymptoms, aesKey) : '';
        const diagnosis = encryptedData.encryptedDiagnosis ? 
            await this.decryptWithAES(encryptedData.encryptedDiagnosis, aesKey) : '';
        
        // Decrypt images
        let imageData = null;
        if (encryptedData.encryptedImages) {
            imageData = await this.decryptBytesWithAES(encryptedData.encryptedImages, aesKey);
        }
        
        // Decrypt videos
        let videoData = null;
        if (encryptedData.encryptedVideos) {
            videoData = await this.decryptBytesWithAES(encryptedData.encryptedVideos, aesKey);
        }
        
        return { symptoms, diagnosis, imageData, videoData };
    },

    // ==================== UTILITIES ====================

    /**
     * Convert ArrayBuffer to Base64 string
     */
    arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    },

    /**
     * Convert Base64 string to ArrayBuffer
     */
    base64ToArrayBuffer(base64) {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes.buffer;
    },

    /**
     * Read file as ArrayBuffer
     */
    fileToArrayBuffer(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = () => reject(reader.error);
            reader.readAsArrayBuffer(file);
        });
    },

    /**
     * SHA-256 hash
     */
    async sha256(message) {
        const encoder = new TextEncoder();
        const data = encoder.encode(message);
        const hash = await crypto.subtle.digest('SHA-256', data);
        return this.arrayBufferToBase64(hash);
    }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = E2EECrypto;
}
