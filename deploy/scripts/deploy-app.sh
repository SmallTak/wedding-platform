#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_HOST="${TARGET_HOST:-iot}"
REMOTE_ROOT="${REMOTE_ROOT:-/home/apps/wedding-platform}"
SERVICE_NAME="${SERVICE_NAME:-wedding-platform}"
DEPLOY_RESTART="${DEPLOY_RESTART:-1}"
SKIP_BUILD="${SKIP_BUILD:-0}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[wedding-platform] missing command: %s\n' "$1" >&2
    exit 1
  }
}

require_cmd ssh
require_cmd rsync

if [[ "$SKIP_BUILD" != "1" ]]; then
  "$ROOT_DIR/deploy/scripts/build-all.sh"
fi

JAR_FILE="$(find "$ROOT_DIR/wedding-server/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
[[ -n "$JAR_FILE" && -f "$JAR_FILE" ]] || {
  printf '[wedding-platform] backend jar not found\n' >&2
  exit 1
}

printf '[wedding-platform] preparing remote directories on %s\n' "$TARGET_HOST"
ssh "$TARGET_HOST" "install -d -o games -g games '$REMOTE_ROOT/web' '$REMOTE_ROOT/console' '$REMOTE_ROOT/backend' '$REMOTE_ROOT/storage'"

printf '[wedding-platform] uploading website\n'
rsync -a --delete --chmod=Du=rwx,Dgo=rx,Fu=rw,Fgo=r \
  "$ROOT_DIR/wedding-web/dist/" "$TARGET_HOST:$REMOTE_ROOT/web/"

printf '[wedding-platform] uploading console\n'
rsync -a --delete --chmod=Du=rwx,Dgo=rx,Fu=rw,Fgo=r \
  "$ROOT_DIR/wedding-console/dist/" "$TARGET_HOST:$REMOTE_ROOT/console/"

printf '[wedding-platform] uploading backend\n'
rsync -a "$JAR_FILE" "$TARGET_HOST:/tmp/wedding-server.jar.uploading"
ssh "$TARGET_HOST" "install -o games -g games -m 0644 /tmp/wedding-server.jar.uploading '$REMOTE_ROOT/backend/wedding-server.jar' && rm -f /tmp/wedding-server.jar.uploading"

printf '[wedding-platform] installing systemd service\n'
rsync -a "$ROOT_DIR/deploy/systemd/wedding-platform.service" "$TARGET_HOST:/tmp/wedding-platform.service.uploading"
ssh "$TARGET_HOST" "install -o root -g root -m 0644 /tmp/wedding-platform.service.uploading /etc/systemd/system/wedding-platform.service && rm -f /tmp/wedding-platform.service.uploading && systemctl daemon-reload"

if [[ "$DEPLOY_RESTART" == "1" ]]; then
  ssh "$TARGET_HOST" "test -f /etc/wedding-platform/wedding-platform.env || { printf '%s\n' 'missing /etc/wedding-platform/wedding-platform.env' >&2; exit 1; }; systemctl enable '$SERVICE_NAME'; systemctl restart '$SERVICE_NAME'; systemctl is-active '$SERVICE_NAME'"
fi
