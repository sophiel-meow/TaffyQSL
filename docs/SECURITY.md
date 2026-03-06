# TaffyQSL Security Architecture

This document provides a detailed overview of TaffyQSL's security architecture, focusing on cryptographic key management, credential storage, and data protection mechanisms.

---

## Table of Contents

1. [Overview](#overview)
2. [Android Keystore Integration](#android-keystore-integration)
3. [Certificate Management](#certificate-management)
4. [LoTW Credential Storage](#lotw-credential-storage)
5. [Cryptographic Algorithms](#cryptographic-algorithms)
6. [Threat Model](#threat-model)
7. [Security Best Practices](#security-best-practices)
8. [Compliance](#compliance)

---

## Overview

TaffyQSL implements a defense-in-depth security architecture to protect sensitive cryptographic materials and user credentials:

- **Amateur Radio Certificates**: X.509 certificates with private keys for signing QSO logs
- **LoTW Credentials**: Username and password for ARRL Logbook of The World access
- **QSO Data**: Amateur radio contact records (considered non-sensitive)

### Security Goals

1. **Confidentiality**: Protect private keys and credentials from unauthorized access
2. **Integrity**: Ensure cryptographic operations produce valid, tamper-proof signatures
3. **Availability**: Allow legitimate users to access their certificates and credentials
4. **Non-Extractability**: Prevent private keys from being extracted from the device (where possible)

---

## Android Keystore Integration

TaffyQSL leverages **Android Keystore System** as the primary security boundary. Android Keystore provides hardware-backed cryptographic key storage on supported devices.

### Key Features

- **Hardware Security Module (HSM)**: On devices with Trusted Execution Environment (TEE) or Secure Element (SE), keys are stored in hardware and never exposed to the Android OS
- **Non-Extractable Keys**: Keys generated or imported into Android Keystore cannot be extracted via `getEncoded()` — they can only be used for cryptographic operations

### Implementation Details

**Keystore Provider**: `AndroidKeyStore`

**Key Types**:
- **RSA Private Keys**: For signing QSO logs (SHA1withRSA, per LoTW specification)
- **AES-256 Keys**: For encrypting private key backups and LoTW credentials

**Storage Location**:
- Hardware-backed: Trusted Execution Environment (TEE) or Secure Element (SE)
- Software-backed: Android Keystore software implementation (fallback on older devices)

---

## Certificate Management

### Architecture

TaffyQSL uses a **dual-storage model** for amateur radio certificates:

1. **Android Keystore**: Stores the private key for signing operations (non-extractable)
2. **Encrypted Backup**: Stores an encrypted copy of the private key for export/backup purposes

This design balances security (non-extractable keys for signing) with usability (ability to export certificates for use on other devices).

### Import Process (`CertificateManager.importP12`)

When a user imports a `.p12` (PKCS#12) certificate file:

1. **Parse PKCS#12 File**:
   - Load the PKCS#12 keystore using the user-provided password
   - Extract the user's end-entity certificate (the one with a private key)
   - Extract the private key
   - Extract the full certificate chain (user cert + intermediate CA + root CA)

2. **Store Certificate**:
   - Save the user certificate as PEM: `<alias>.pem`
   - Save the full certificate chain as concatenated PEM: `<alias>.chain`
   - Location: `<app_data>/files/certs/`

3. **Import Private Key to Android Keystore**:
   ```kotlin
   KeyStore.getInstance("AndroidKeyStore")
   keyStore.setEntry(alias, PrivateKeyEntry(key, arrayOf(cert)), KeyProtection.Builder(
       KeyProperties.PURPOSE_SIGN
   )
       .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_NONE)
       .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
       .build()
   )
   ```
   - **Purpose**: `PURPOSE_SIGN` (signing only, not encryption)
   - **Digest**: `SHA1` (required by LoTW), `NONE` (for raw signing)
   - **Padding**: `RSA_PKCS1` (PKCS#1 v1.5 padding)

4. **Create Encrypted Private Key Backup**:
   - Generate a unique AES-256 wrapping key in Android Keystore: `<alias>_wrap`
   - Encrypt the PKCS#8 DER-encoded private key bytes using AES-GCM
   - Save encrypted key: `<alias>.key`

### Encrypted Private Key Backup Format

**File**: `<alias>.key`

**Layout**:
```
+----------------+------------------+------------------------+
| IV Length (1B) | IV (N bytes)     | Ciphertext (remaining) |
+----------------+------------------+------------------------+
```

- **IV Length**: 1 byte, specifies the length of the GCM initialization vector (typically 12 bytes)
- **IV**: The AES-GCM initialization vector (random, unique per encryption)
- **Ciphertext**: AES-GCM encrypted PKCS#8 DER bytes of the private key

**Encryption Algorithm**: AES-256-GCM (Galois/Counter Mode)
- **Key Size**: 256 bits
- **Block Mode**: GCM (provides both confidentiality and authenticity)
- **Padding**: None (GCM is a stream cipher mode)
- **Authentication Tag**: 128 bits (embedded in ciphertext by GCM)

**Wrapping Key**: Stored in Android Keystore under alias `<alias>_wrap`
- **Algorithm**: AES
- **Key Size**: 256 bits
- **Purpose**: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`
- **Non-Extractable**: Yes (key never leaves Android Keystore)

### Export Process (`CertificateManager.exportP12`)

When a user exports a certificate:

1. **Load Certificate**: Read `<alias>.pem`
2. **Decrypt Private Key**:
   - Read `<alias>.key`
   - Extract IV and ciphertext
   - Retrieve wrapping key from Android Keystore: `<alias>_wrap`
   - Decrypt using AES-GCM: `Cipher.getInstance("AES/GCM/NoPadding")`
   - Reconstruct `PrivateKey` from PKCS#8 bytes
3. **Load Certificate Chain**: Read `<alias>.chain`
4. **Create PKCS#12**:
   - Create a new PKCS#12 keystore
   - Store private key + full certificate chain
   - Encrypt with user-provided password
   - Return as byte array

### Deletion Process (`CertificateManager.deleteCert`)

When a certificate is deleted:

1. Delete files: `<alias>.pem`, `<alias>.key`, `<alias>.chain`
2. Delete Android Keystore entries: `<alias>`, `<alias>_wrap`

**Note**: Android Keystore entries are automatically deleted when the app is uninstalled.

### Certificate Alias Generation

The certificate alias is the **SHA-256 fingerprint** of the certificate (hex-encoded). This ensures:
- **Uniqueness**: No two certificates will have the same alias
- **Consistency**: Re-importing the same certificate produces the same alias
- **Collision Resistance**: SHA-256 provides strong collision resistance

---

## LoTW Credential Storage

### Architecture

LoTW credentials (username and password) are stored using **AES-256-GCM encryption** with a key stored in Android Keystore.

### Storage Process (`LotwCredentialManager.saveCredentials`)

1. **Serialize Credentials**:
   ```json
   {"u": "username", "p": "password"}
   ```
   - Format: JSON (UTF-8 encoded)

2. **Generate Wrapping Key**:
   - Create AES-256 key in Android Keystore: `lotw_credentials_wrap`
   - **Purpose**: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`
   - **Block Mode**: GCM
   - **Key Size**: 256 bits

3. **Encrypt**:
   - Algorithm: AES-256-GCM
   - Cipher: `AES/GCM/NoPadding`
   - Generate random IV (12 bytes)
   - Encrypt JSON plaintext

4. **Save to File**:
   - File: `<app_data>/files/lotw_credentials.enc`
   - Layout: `[IV Length (1B)] [IV] [Ciphertext]`

### Retrieval Process (`LotwCredentialManager.loadCredentials`)

1. **Read File**: `lotw_credentials.enc`
2. **Extract IV and Ciphertext**:
   - Read IV length (first byte)
   - Extract IV
   - Extract ciphertext
3. **Decrypt**:
   - Retrieve wrapping key from Android Keystore: `lotw_credentials_wrap`
   - Decrypt using AES-GCM with 128-bit authentication tag
   - Parse JSON to extract username and password
4. **Return**: `Pair<String, String>` (username, password)

### Deletion Process (`LotwCredentialManager.clearCredentials`)

1. Delete file: `lotw_credentials.enc`
2. Delete Android Keystore entry: `lotw_credentials_wrap`

---

## Cryptographic Algorithms

### Summary Table

| Use Case                  | Algorithm       | Key Size | Mode/Padding          | Authentication |
|---------------------------|-----------------|----------|-----------------------|----------------|
| QSO Log Signing           | RSA             | 2048-bit | PKCS#1 v1.5           | SHA-1 digest   |
| Private Key Backup        | AES             | 256-bit  | GCM                   | 128-bit tag    |
| LoTW Credential Storage   | AES             | 256-bit  | GCM                   | 128-bit tag    |
| Certificate Fingerprint   | SHA-256         | —        | —                     | —              |

### Algorithm Justification

#### RSA with SHA-1 (QSO Signing)

**Why SHA-1?**
- **LoTW Requirement**: ARRL's Logbook of The World requires SHA-1 for signature compatibility
- **Context**: SHA-1 is deprecated for general use due to collision attacks, but remains acceptable for digital signatures in closed systems where collision attacks are not a threat
- **Mitigation**: TaffyQSL only uses SHA-1 for signing QSO logs per LoTW specification; all other cryptographic operations use SHA-256 or stronger

#### AES-256-GCM (Encryption)

**Why AES-GCM?**
- **Confidentiality**: AES-256 provides strong symmetric encryption
- **Authenticity**: GCM mode provides authenticated encryption (AEAD), preventing tampering
- **Performance**: Hardware-accelerated on most modern Android devices
- **Standard**: NIST-approved, widely adopted in industry

**GCM Parameters**:
- **IV Size**: 12 bytes (96 bits) — recommended size for GCM
- **Tag Size**: 128 bits — provides strong authentication
- **No Padding**: GCM is a stream cipher mode, no padding required

#### SHA-256 (Fingerprinting)

**Why SHA-256?**
- **Collision Resistance**: Provides strong collision resistance for certificate fingerprinting
- **Standard**: Widely used for certificate fingerprints (e.g., TLS, SSH)

---

## Threat Model

### Threats Mitigated

1. **Malicious App Access**:
   - **Threat**: Another app on the device attempts to read TaffyQSL's private keys or credentials
   - **Mitigation**: Android Keystore keys are isolated per-app; file-based storage uses app-private directories (`Context.filesDir`)

2. **Physical Device Theft (Locked Device)**:
   - **Threat**: Attacker steals a locked device and attempts to extract keys
   - **Mitigation**: On hardware-backed devices, keys are stored in TEE/SE and cannot be extracted without unlocking the device

3. **Backup/Restore Attacks**:
   - **Threat**: Attacker extracts app data from an Android backup and attempts to decrypt credentials
   - **Mitigation**: Android Keystore keys are not included in backups; encrypted files are useless without the wrapping keys

4. **Memory Dump Attacks**:
   - **Threat**: Attacker with root access dumps app memory to extract plaintext keys
   - **Mitigation**: Private keys are only decrypted in memory during signing operations; credentials are only decrypted when needed

5. **Tampering with Encrypted Files**:
   - **Threat**: Attacker modifies encrypted credential or key files
   - **Mitigation**: AES-GCM provides authenticated encryption; tampering will cause decryption to fail

### Threats NOT Mitigated

1. **Rooted Device / Malware with Root Access**:
   - **Limitation**: Root access can bypass Android's app sandboxing and potentially extract keys from memory or hardware
   - **Recommendation**: Users should avoid rooting devices used for sensitive operations

2. **Screen Recording / Keylogging**:
   - **Limitation**: Malware with accessibility permissions can record screen content or keystrokes
   - **Recommendation**: Users should only install apps from trusted sources

3. **Physical Device Theft (Unlocked Device)**:
   - **Limitation**: If the device is unlocked, an attacker can access the app and use stored credentials
   - **Recommendation**: Users should enable device lock screen and use strong authentication

4. **Side-Channel Attacks**:
   - **Limitation**: Timing attacks, power analysis, or other side-channel attacks against cryptographic operations
   - **Mitigation**: Relies on Android Keystore's hardware-backed implementation to resist side-channel attacks

5. **Quantum Computing Attacks**:
   - **Limitation**: RSA-2048 and AES-256 are vulnerable to quantum attacks (Shor's algorithm, Grover's algorithm)
   - **Timeline**: Not a practical threat in 2026; post-quantum cryptography standards are still emerging

---

## Security Best Practices

1. **Use Strong Device Lock Screen**:
   - Enable PIN, pattern, or biometric authentication
   - Avoid "None" or "Swipe" lock screen

2. **Keep Android Updated**:
   - Install security patches promptly
   - Use Android 16 or higher

3. **Avoid Rooting**:
   - Rooting weakens Android's security model
   - Use TaffyQSL on non-rooted devices

4. **Use Strong Passwords**:
   - Use a strong password when exporting certificates from TQSL
   - Use a strong LoTW password

5. **Backup Certificates Securely**:
   - Export certificates to a secure location (e.g., encrypted USB drive)
   - Do not store unencrypted `.p12` files on cloud storage

6. **Verify App Authenticity**:
   - Download TaffyQSL only from official sources (GitHub Releases, F-Droid)
   - Verify APK signatures if possible

---

## Compliance

**GDPR (General Data Protection Regulation)**:
- TaffyQSL stores user credentials locally on the device
- No personal data is transmitted to third parties (except LoTW, per user action)
- Users can delete their data at any time (via app settings or uninstall)

**ARRL LoTW Terms of Service**:
- TaffyQSL complies with ARRL's LoTW terms of service
- Credentials are only used for authorized LoTW operations (upload, query)
- No credential sharing or unauthorized access

---

## Implementation Files

### `CertificateManager.kt`

**Location**: `app/src/main/java/moe/zzy040330/taffyqsl/data/crypto/CertificateManager.kt`

**Responsibilities**:
- Import PKCS#12 certificates
- Store certificates and private keys
- Encrypt private key backups
- Export certificates as PKCS#12
- Parse ARRL LoTW certificate extensions (callsign, DXCC entity, QSO dates)

**Key Methods**:
- `importP12(uri: Uri, password: String)`: Import a `.p12` file
- `exportP12(alias: String, password: String)`: Export a certificate as `.p12`
- `getPrivateKey(alias: String)`: Retrieve private key from Android Keystore
- `getCertificate(alias: String)`: Retrieve certificate from PEM file
- `deleteCert(alias: String)`: Delete certificate and associated keys

### `LotwCredentialManager.kt`

**Location**: `app/src/main/java/moe/zzy040330/taffyqsl/data/lotw/LotwCredentialManager.kt`

**Responsibilities**:
- Store LoTW credentials (username, password)
- Encrypt credentials with AES-256-GCM
- Decrypt credentials for LoTW operations
- Clear credentials on user request

**Key Methods**:
- `saveCredentials(username: String, password: String)`: Encrypt and save credentials
- `loadCredentials()`: Decrypt and return credentials
- `hasCredentials()`: Check if credentials are stored
- `clearCredentials()`: Delete credentials and wrapping key

---

## Changelog

### Version 1.0.0 (2026-03-07)

- Initial security architecture
- Android Keystore integration for private keys and wrapping keys
- AES-256-GCM encryption for private key backups and LoTW credentials
- SHA-256 fingerprinting for certificate aliases
- Dual-storage model for certificates (Keystore + encrypted backup)

---

## References

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [NIST SP 800-38D: Galois/Counter Mode (GCM)](https://csrc.nist.gov/publications/detail/sp/800-38d/final)
- [OWASP Mobile Security Testing Guide](https://owasp.org/www-project-mobile-security-testing-guide/)
- [ARRL Logbook of The World](https://lotw.arrl.org/)
- [TrustedQSL (TQSL) Source Code](https://sourceforge.net/projects/trustedqsl/)

---

## Contact

For security concerns or vulnerability reports, please open an issue on GitHub:
[https://github.com/sophiel-meow/TaffyQSL/issues](https://github.com/sophiel-meow/TaffyQSL/issues)
or by [email](mailto:sophiel@zzy040330.moe).

**Please do not disclose security vulnerabilities publicly until they have been addressed.**

---

*Last Updated: 2026-03-07*
