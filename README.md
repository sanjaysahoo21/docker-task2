# docker-task2

## 🚀 Overview

This project implements a **secure PKI-based Two-Factor Authentication (2FA) microservice** using:

* RSA 4096-bit cryptography
* OAEP SHA-256 decryption
* TOTP-based 6-digit codes
* Java backend
* Docker containerization

The service receives an encrypted seed from the instructor API, decrypts it using the student private key, stores the seed securely inside the container, and exposes REST endpoints to generate and verify TOTP codes.

---

# 📁 Folder Structure

```
docker-task2/
│
├── Dockerfile
├── entrypoint.sh
├── TotpApiServer.java
├── TotpUtil.java
│
├── student_private.pem       # REQUIRED (committed)
├── student_public.pem        # REQUIRED (committed)
├── instructor_public.pem     # REQUIRED (committed)
│
├── encrypted_seed.txt        # LOCAL ONLY (NOT committed)
├── data/                     # created at runtime
│   └── seed.txt              # decrypted seed written by container
│
└── .gitignore
```

### ❗ Do NOT commit:

```
encrypted_seed.txt
encrypted_seed.bin
decrypted*.txt
data/
*.class
```

---

# 🔐 Step 1 — Student RSA Key Pair

Generated using Java (4096-bit).
Public key submitted to instructor API.
Both keys are stored in PEM format:

```
student_private.pem  
student_public.pem
```

---

# ☁️ Step 2 — Request Encrypted Seed

Encrypted seed obtained using:

```
POST https://eajeyq4r3zljoq4rpovy2nthda0vtjqf.lambda-url.ap-south-1.on.aws/
```

Payload includes:

* student_id
* GitHub repository URL
* student public key

Response stored locally as:

```
encrypted_seed.txt
```

(Not committed to Git.)

---

# 🔓 Step 3 — Decrypt Seed (Inside Container)

Decryption uses:

* RSA
* OAEP
* SHA-256
* MGF1 SHA-256
* No label

The decrypted seed is a **64-character hexadecimal string**, e.g.:

```
7e0535bce1e72863...
```

The container writes the decrypted seed to:

```
/data/seed.txt
```

This is required for TOTP generation.

---

# 🧱 Docker Build

Run in project root:

```bash
docker build -t totp-app .
```

This:

* Copies code + PEM files
* Installs OpenSSL
* Compiles Java classes
* Adds entrypoint.sh

---

# ▶️ Run the Container

Git Bash:

```bash
docker run --rm -p 8080:8080 \
  -v "$(pwd)/encrypted_seed.txt:/tmp/encrypted_seed.txt:ro" \
  -v "$(pwd)/data:/data" \
  totp-app
```

PowerShell:

```powershell
docker run --rm -p 8080:8080 `
  -v "${PWD}\encrypted_seed.txt:/tmp/encrypted_seed.txt:ro" `
  -v "${PWD}\data:/data" `
  totp-app
```

The container will:

1. Check for student_private.pem
2. Check for encrypted_seed.txt
3. Base64-decode ciphertext
4. RSA/OAEP-SHA256 decrypt
5. Write `/data/seed.txt`
6. Start the Java API server

Console output:

```
[ENTRYPOINT] Decoding base64...
[ENTRYPOINT] Decrypting...
Wrote seed to /data/seed.txt
TOTP API server running on port 8080
```

---

# 🌐 API Endpoints

## 1️⃣ **Generate TOTP Code**

```
GET /generate-2fa
```

Response example:

```json
{
  "code": "123456",
  "valid_for": 27
}
```

* 6-digit TOTP code
* valid_for = seconds until next rotation (30 seconds total)

---

## 2️⃣ **Verify TOTP Code**

```
POST /verify-2fa
```

Body:

```json
{
  "code": "123456"
}
```

Response:

```json
{"valid": true}
```

or:

```json
{"valid": false}
```

Verification is tolerant ±1 time window.

---

# 📌 Testing Examples

### Generate:

```powershell
curl http://127.0.0.1:8080/generate-2fa
```

### Verify:

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/verify-2fa" `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"code":"123456"}'
```

---

# 🏁 Final Result

Your Dockerized microservice:

* Securely decrypts seeded secret
* Generates TOTP codes
* Verifies TOTP codes
* Runs fully inside a container
* Works exactly as required by your mentor

---

# 🎉 Completed Successfully

This project meets all requirements for:

**“Build Secure PKI-Based 2FA Microservice with Docker”**

---

# 📞 Need help?

If you want:

* A demonstration script
* A submission PDF
* A simplified explanation for viva
* A video-style walkthrough

Just ask:

👉 **“Generate submission PDF”** or
👉 **“Explain this in viva format”**

---

This README is final, clean, and ready to commit.
