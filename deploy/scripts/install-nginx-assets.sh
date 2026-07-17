#!/usr/bin/env bash
set -euo pipefail

NGINX_BIN="${NGINX_BIN:-/usr/local/nginx/sbin/nginx}"
NGINX_CONF="${NGINX_CONF:-/usr/local/nginx/conf/conf.d/photo.shop-hz.top.conf}"
TLS_DIR="${TLS_DIR:-/usr/local/nginx/conf/ssl/photo.shop-hz.top}"
UPLOAD_DIR="${UPLOAD_DIR:-/tmp/wedding-platform-nginx}"
BACKUP_DIR="${BACKUP_DIR:-/usr/local/nginx/conf/backups/photo.shop-hz.top-$(date +%Y%m%d%H%M%S)}"

[[ -x "$NGINX_BIN" ]] || { printf 'nginx binary not found: %s\n' "$NGINX_BIN" >&2; exit 1; }
[[ -f "$UPLOAD_DIR/photo.shop-hz.top.conf" ]] || { printf 'uploaded nginx config is missing\n' >&2; exit 1; }
[[ -f "$UPLOAD_DIR/fullchain.pem" ]] || { printf 'uploaded certificate is missing\n' >&2; exit 1; }
[[ -f "$UPLOAD_DIR/privkey.pem" ]] || { printf 'uploaded private key is missing\n' >&2; exit 1; }

install -d -m 0700 "$BACKUP_DIR"
[[ -f "$NGINX_CONF" ]] && cp -a "$NGINX_CONF" "$BACKUP_DIR/nginx.conf"
[[ -f "$TLS_DIR/fullchain.pem" ]] && cp -a "$TLS_DIR/fullchain.pem" "$BACKUP_DIR/fullchain.pem"
[[ -f "$TLS_DIR/privkey.pem" ]] && cp -a "$TLS_DIR/privkey.pem" "$BACKUP_DIR/privkey.pem"

install -d -m 0700 "$TLS_DIR"
install -o root -g root -m 0644 "$UPLOAD_DIR/fullchain.pem" "$TLS_DIR/fullchain.pem"
install -o root -g root -m 0600 "$UPLOAD_DIR/privkey.pem" "$TLS_DIR/privkey.pem"
install -o root -g root -m 0644 "$UPLOAD_DIR/photo.shop-hz.top.conf" "$NGINX_CONF"

if ! "$NGINX_BIN" -t; then
  if [[ -f "$BACKUP_DIR/nginx.conf" ]]; then
    cp -a "$BACKUP_DIR/nginx.conf" "$NGINX_CONF"
  else
    rm -f "$NGINX_CONF"
  fi
  [[ -f "$BACKUP_DIR/fullchain.pem" ]] && cp -a "$BACKUP_DIR/fullchain.pem" "$TLS_DIR/fullchain.pem"
  [[ -f "$BACKUP_DIR/privkey.pem" ]] && cp -a "$BACKUP_DIR/privkey.pem" "$TLS_DIR/privkey.pem"
  "$NGINX_BIN" -t
  printf 'nginx validation failed; previous configuration restored\n' >&2
  exit 1
fi

"$NGINX_BIN" -s reload
rm -rf "$UPLOAD_DIR"
printf 'nginx HTTPS configuration installed; backup: %s\n' "$BACKUP_DIR"
