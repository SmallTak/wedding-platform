#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_HOST="${TARGET_HOST:-iot}"
TLS_CERT_FILE="${TLS_CERT_FILE:-$HOME/Downloads/26141245_photo.shop-hz.top_nginx/photo.shop-hz.top.pem}"
TLS_KEY_FILE="${TLS_KEY_FILE:-$HOME/Downloads/26141245_photo.shop-hz.top_nginx/photo.shop-hz.top.key}"
TLS_ARCHIVE="${TLS_ARCHIVE:-$HOME/Downloads/26141245_photo.shop-hz.top_nginx.zip}"
DRY_RUN="${DRY_RUN:-0}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[wedding-platform] missing command: %s\n' "$1" >&2
    exit 1
  }
}

require_cmd openssl
require_cmd rsync
require_cmd ssh

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

if [[ -f "$TLS_CERT_FILE" && -f "$TLS_KEY_FILE" ]]; then
  install -m 0644 "$TLS_CERT_FILE" "$TEMP_DIR/fullchain.pem"
  install -m 0600 "$TLS_KEY_FILE" "$TEMP_DIR/privkey.pem"
elif [[ -f "$TLS_ARCHIVE" ]]; then
  require_cmd unzip
  pem_entry="$(unzip -Z1 "$TLS_ARCHIVE" | grep -E '\.pem$' | head -n 1)"
  key_entry="$(unzip -Z1 "$TLS_ARCHIVE" | grep -E '\.key$' | head -n 1)"
  [[ -n "$pem_entry" && -n "$key_entry" ]] || { printf '[wedding-platform] certificate archive is incomplete\n' >&2; exit 1; }
  unzip -p "$TLS_ARCHIVE" "$pem_entry" > "$TEMP_DIR/fullchain.pem"
  unzip -p "$TLS_ARCHIVE" "$key_entry" > "$TEMP_DIR/privkey.pem"
  chmod 0644 "$TEMP_DIR/fullchain.pem"
  chmod 0600 "$TEMP_DIR/privkey.pem"
else
  printf '[wedding-platform] certificate files were not found\n' >&2
  exit 1
fi

openssl x509 -in "$TEMP_DIR/fullchain.pem" -noout -checkend 86400 >/dev/null || {
  printf '[wedding-platform] certificate is expired or expires within 24 hours\n' >&2
  exit 1
}
if ! openssl x509 -in "$TEMP_DIR/fullchain.pem" -noout -checkend 1209600 >/dev/null; then
  printf '[wedding-platform] warning: certificate expires within 14 days\n' >&2
fi
openssl x509 -in "$TEMP_DIR/fullchain.pem" -noout -ext subjectAltName | grep -q 'DNS:photo.shop-hz.top' || {
  printf '[wedding-platform] certificate does not cover photo.shop-hz.top\n' >&2
  exit 1
}

cert_public_key="$(openssl x509 -in "$TEMP_DIR/fullchain.pem" -pubkey -noout | openssl pkey -pubin -outform der | shasum -a 256 | awk '{print $1}')"
private_public_key="$(openssl pkey -in "$TEMP_DIR/privkey.pem" -pubout -outform der | shasum -a 256 | awk '{print $1}')"
[[ "$cert_public_key" == "$private_public_key" ]] || {
  printf '[wedding-platform] certificate and private key do not match\n' >&2
  exit 1
}

install -m 0644 "$ROOT_DIR/deploy/nginx/photo.shop-hz.top.conf" "$TEMP_DIR/photo.shop-hz.top.conf"
install -m 0755 "$ROOT_DIR/deploy/scripts/install-nginx-assets.sh" "$TEMP_DIR/install-nginx-assets.sh"

if [[ "$DRY_RUN" == "1" ]]; then
  printf '[wedding-platform] certificate and nginx deployment inputs are valid\n'
  openssl x509 -in "$TEMP_DIR/fullchain.pem" -noout -subject -dates
  exit 0
fi

printf '[wedding-platform] uploading HTTPS assets to %s\n' "$TARGET_HOST"
ssh "$TARGET_HOST" "rm -rf /tmp/wedding-platform-nginx && install -d -m 0700 /tmp/wedding-platform-nginx"
rsync -a "$TEMP_DIR/fullchain.pem" "$TEMP_DIR/privkey.pem" "$TEMP_DIR/photo.shop-hz.top.conf" "$TEMP_DIR/install-nginx-assets.sh" "$TARGET_HOST:/tmp/wedding-platform-nginx/"
ssh "$TARGET_HOST" "bash /tmp/wedding-platform-nginx/install-nginx-assets.sh"
