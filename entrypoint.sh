#!/usr/bin/env bash
set -euo pipefail

ENC_PATH="/tmp/encrypted_seed.txt"
PRIV_KEY="/app/student_private.pem"
OUT="/data/seed.txt"

echo "[ENTRYPOINT] Checking for private key..."
if [ ! -f "$PRIV_KEY" ]; then
  echo "ERROR: student_private.pem missing inside container!"
  exit 2
fi

echo "[ENTRYPOINT] Checking for encrypted seed..."
if [ ! -f "$ENC_PATH" ]; then
  echo "ERROR: encrypted_seed.txt NOT provided. Mount it using:"
  echo "  -v <path>/encrypted_seed.txt:/tmp/encrypted_seed.txt:ro"
  exit 3
fi

echo "[ENTRYPOINT] Decoding base64 ciphertext..."
openssl base64 -d -in "$ENC_PATH" -out /tmp/encrypted_seed.bin

echo "[ENTRYPOINT] Decrypting with RSA/OAEP SHA-256..."
openssl pkeyutl -decrypt \
  -inkey "$PRIV_KEY" \
  -in /tmp/encrypted_seed.bin \
  -pkeyopt rsa_padding_mode:oaep \
  -pkeyopt rsa_oaep_md:sha256 \
  -pkeyopt rsa_mgf1_md:sha256 \
  -out /tmp/decrypted_seed.txt

seed=$(tr -d '\r\n' < /tmp/decrypted_seed.txt)

echo "[ENTRYPOINT] Validating seed..."
if ! echo "$seed" | grep -Eq '^[0-9a-fA-F]{64}$'; then
  echo "ERROR: Seed invalid. Got: $seed"
  exit 4
fi

echo "[ENTRYPOINT] Writing seed to /data/seed.txt..."
mkdir -p /data
printf "%s" "$(echo "$seed" | tr 'A-F' 'a-f')" > "$OUT"

echo "[ENTRYPOINT] Starting Java Server..."
exec java -cp /app TotpApiServer
