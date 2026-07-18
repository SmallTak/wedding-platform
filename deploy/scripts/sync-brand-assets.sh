#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

install -D -m 0644 "$ROOT_DIR/brand/mark-dark.svg" \
  "$ROOT_DIR/wedding-web/src/assets/brand/mark-dark.svg"
install -D -m 0644 "$ROOT_DIR/brand/mark-light.svg" \
  "$ROOT_DIR/wedding-web/src/assets/brand/mark-light.svg"
install -D -m 0644 "$ROOT_DIR/brand/favicon.svg" \
  "$ROOT_DIR/wedding-web/public/favicon.svg"

install -D -m 0644 "$ROOT_DIR/brand/mark-dark.svg" \
  "$ROOT_DIR/wedding-console/src/assets/brand/mark-dark.svg"
install -D -m 0644 "$ROOT_DIR/brand/mark-light.svg" \
  "$ROOT_DIR/wedding-console/src/assets/brand/mark-light.svg"
install -D -m 0644 "$ROOT_DIR/brand/favicon.svg" \
  "$ROOT_DIR/wedding-console/public/favicon.svg"

install -D -m 0644 "$ROOT_DIR/brand/watermark.png" \
  "$ROOT_DIR/wedding-server/src/main/resources/brand/watermark.png"
