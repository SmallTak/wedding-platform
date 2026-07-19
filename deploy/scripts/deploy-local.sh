#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_ROOT="${APP_ROOT:-/home/apps/wedding-platform}"
APP_USER="${APP_USER:-games}"
APP_GROUP="${APP_GROUP:-games}"
SERVICE_NAME="${SERVICE_NAME:-wedding-platform}"
ENV_FILE="${ENV_FILE:-/etc/wedding-platform/wedding-platform.env}"
NGINX_BIN="${NGINX_BIN:-/usr/local/nginx/sbin/nginx}"
NGINX_CONF="${NGINX_CONF:-/usr/local/nginx/conf/conf.d/photo.shop-hz.top.conf}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://photo.shop-hz.top}"
LOCAL_HEALTH_URL="${LOCAL_HEALTH_URL:-http://127.0.0.1:8080/api/public/status}"
SKIP_TEST="${SKIP_TEST:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"
DEPLOY_NGINX="${DEPLOY_NGINX:-1}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[wedding-platform] missing command: %s\n' "$1" >&2
    exit 1
  }
}

[[ "$EUID" -eq 0 ]] || {
  printf '[wedding-platform] local production deployment must run as root\n' >&2
  exit 1
}

require_cmd curl
require_cmd rsync
require_cmd systemctl

[[ -f "$ENV_FILE" ]] || {
  printf '[wedding-platform] missing environment file: %s\n' "$ENV_FILE" >&2
  exit 1
}
grep -Eq '^[[:space:]]*BOOTSTRAP_ADMIN_ENABLED=false[[:space:]]*$' "$ENV_FILE" || {
  printf '[wedding-platform] BOOTSTRAP_ADMIN_ENABLED must explicitly remain false\n' >&2
  exit 1
}

if [[ "$SKIP_TEST" != "1" ]]; then
  printf '[wedding-platform] running backend tests\n'
  (
    cd "$ROOT_DIR/wedding-server"
    if [[ -x /opt/java/jdk17/bin/java ]]; then
      JAVA_HOME=/opt/java/jdk17
      PATH="$JAVA_HOME/bin:$PATH"
      export JAVA_HOME PATH
    fi
    ./gradlew clean test
  )
fi

if [[ "$SKIP_BUILD" != "1" ]]; then
  "$ROOT_DIR/deploy/scripts/build-all.sh"
fi

JAR_FILE="$(find "$ROOT_DIR/wedding-server/build/libs" -maxdepth 1 -type f \
  -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
[[ -n "$JAR_FILE" && -f "$JAR_FILE" ]] || {
  printf '[wedding-platform] backend jar not found\n' >&2
  exit 1
}
[[ -f "$ROOT_DIR/wedding-web/dist/index.html" ]] || {
  printf '[wedding-platform] website build output is missing\n' >&2
  exit 1
}
[[ -f "$ROOT_DIR/wedding-console/dist/index.html" ]] || {
  printf '[wedding-platform] console build output is missing\n' >&2
  exit 1
}

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$APP_ROOT/backups/$TIMESTAMP"
install -d -o root -g root -m 0750 "$BACKUP_DIR"

web_existed=0
console_existed=0
jar_existed=0
service_unit_existed=0
nginx_conf_existed=0

if [[ -d "$APP_ROOT/web" ]]; then
  web_existed=1
  cp -a "$APP_ROOT/web" "$BACKUP_DIR/web"
fi
if [[ -d "$APP_ROOT/console" ]]; then
  console_existed=1
  cp -a "$APP_ROOT/console" "$BACKUP_DIR/console"
fi
if [[ -f "$APP_ROOT/backend/wedding-server.jar" ]]; then
  jar_existed=1
  cp -a "$APP_ROOT/backend/wedding-server.jar" "$BACKUP_DIR/wedding-server.jar"
fi
if [[ -f "/etc/systemd/system/$SERVICE_NAME.service" ]]; then
  service_unit_existed=1
  cp -a "/etc/systemd/system/$SERVICE_NAME.service" "$BACKUP_DIR/$SERVICE_NAME.service"
fi
if [[ "$DEPLOY_NGINX" == "1" && -f "$NGINX_CONF" ]]; then
  nginx_conf_existed=1
  cp -a "$NGINX_CONF" "$BACKUP_DIR/nginx.conf"
fi

rollback_required=1
original_probe_file=""
rollback() {
  local status="$?"
  trap - ERR
  set +e
  if [[ -n "$original_probe_file" ]]; then
    rm -f "$original_probe_file"
  fi
  if [[ "$rollback_required" == "1" ]]; then
    printf '[wedding-platform] deployment failed; restoring backup %s\n' "$BACKUP_DIR" >&2
    if [[ -d "$BACKUP_DIR/web" ]]; then
      rm -rf "$APP_ROOT/web"
      cp -a "$BACKUP_DIR/web" "$APP_ROOT/web"
    elif [[ "$web_existed" == "0" ]]; then
      rm -rf "$APP_ROOT/web"
    fi
    if [[ -d "$BACKUP_DIR/console" ]]; then
      rm -rf "$APP_ROOT/console"
      cp -a "$BACKUP_DIR/console" "$APP_ROOT/console"
    elif [[ "$console_existed" == "0" ]]; then
      rm -rf "$APP_ROOT/console"
    fi
    if [[ -f "$BACKUP_DIR/wedding-server.jar" ]]; then
      install -d -o "$APP_USER" -g "$APP_GROUP" "$APP_ROOT/backend"
      install -o "$APP_USER" -g "$APP_GROUP" -m 0644 \
        "$BACKUP_DIR/wedding-server.jar" "$APP_ROOT/backend/wedding-server.jar"
    elif [[ "$jar_existed" == "0" ]]; then
      rm -f "$APP_ROOT/backend/wedding-server.jar"
    fi
    if [[ -f "$BACKUP_DIR/$SERVICE_NAME.service" ]]; then
      install -o root -g root -m 0644 \
        "$BACKUP_DIR/$SERVICE_NAME.service" "/etc/systemd/system/$SERVICE_NAME.service"
    elif [[ "$service_unit_existed" == "0" ]]; then
      rm -f "/etc/systemd/system/$SERVICE_NAME.service"
    fi
    if [[ "$DEPLOY_NGINX" == "1" ]]; then
      if [[ -f "$BACKUP_DIR/nginx.conf" ]]; then
        install -o root -g root -m 0644 "$BACKUP_DIR/nginx.conf" "$NGINX_CONF"
      elif [[ "$nginx_conf_existed" == "0" ]]; then
        rm -f "$NGINX_CONF"
      fi
      "$NGINX_BIN" -t && "$NGINX_BIN" -s reload
    fi
    systemctl daemon-reload
    if [[ "$service_unit_existed" == "1" ]]; then
      systemctl restart "$SERVICE_NAME"
    else
      systemctl stop "$SERVICE_NAME"
    fi
  fi
  exit "$status"
}
trap rollback ERR

printf '[wedding-platform] deploying website and console\n'
install -d -o "$APP_USER" -g "$APP_GROUP" "$APP_ROOT/web" "$APP_ROOT/console"
rsync -a --delete "$ROOT_DIR/wedding-web/dist/" "$APP_ROOT/web/"
rsync -a --delete "$ROOT_DIR/wedding-console/dist/" "$APP_ROOT/console/"
chown -R "$APP_USER:$APP_GROUP" "$APP_ROOT/web" "$APP_ROOT/console"
find "$APP_ROOT/web" "$APP_ROOT/console" -type d -exec chmod 0755 {} +
find "$APP_ROOT/web" "$APP_ROOT/console" -type f -exec chmod 0644 {} +

printf '[wedding-platform] deploying backend and systemd unit\n'
install -d -o "$APP_USER" -g "$APP_GROUP" "$APP_ROOT/backend" "$APP_ROOT/storage"
install -o "$APP_USER" -g "$APP_GROUP" -m 0644 \
  "$JAR_FILE" "$APP_ROOT/backend/wedding-server.jar.uploading"
mv -f "$APP_ROOT/backend/wedding-server.jar.uploading" "$APP_ROOT/backend/wedding-server.jar"
install -o root -g root -m 0644 \
  "$ROOT_DIR/deploy/systemd/wedding-platform.service" "/etc/systemd/system/$SERVICE_NAME.service"
systemctl daemon-reload

if [[ "$DEPLOY_NGINX" == "1" ]]; then
  printf '[wedding-platform] validating nginx configuration\n'
  [[ -x "$NGINX_BIN" ]] || {
    printf '[wedding-platform] nginx binary not found: %s\n' "$NGINX_BIN" >&2
    false
  }
  install -o root -g root -m 0644 "$ROOT_DIR/deploy/nginx/photo.shop-hz.top.conf" "$NGINX_CONF"
  "$NGINX_BIN" -t
fi

printf '[wedding-platform] restarting backend service\n'
systemctl enable "$SERVICE_NAME" >/dev/null
systemctl restart "$SERVICE_NAME"
systemctl is-active --quiet "$SERVICE_NAME"

healthy=0
for _ in $(seq 1 45); do
  if curl -fsS "$LOCAL_HEALTH_URL" >/dev/null; then
    healthy=1
    break
  fi
  sleep 2
done
[[ "$healthy" == "1" ]] || {
  printf '[wedding-platform] backend health check timed out\n' >&2
  false
}

if [[ "$DEPLOY_NGINX" == "1" ]]; then
  "$NGINX_BIN" -s reload
fi

printf '[wedding-platform] validating public routes\n'
curl -fsS "$PUBLIC_BASE_URL/" >/dev/null
curl -fsS "$PUBLIC_BASE_URL/console/" >/dev/null
curl -fsS "$PUBLIC_BASE_URL/api/public/status" >/dev/null
original_probe_name="deployment-check-$TIMESTAMP.txt"
original_probe_body="wedding-platform-original-media-$TIMESTAMP"
original_probe_file="$APP_ROOT/storage/originals/$original_probe_name"
install -d -o "$APP_USER" -g "$APP_GROUP" -m 0755 "$APP_ROOT/storage/originals"
printf '%s' "$original_probe_body" > "$original_probe_file"
chown "$APP_USER:$APP_GROUP" "$original_probe_file"
chmod 0644 "$original_probe_file"
[[ "$(curl -fsS "$PUBLIC_BASE_URL/media/originals/$original_probe_name")" == "$original_probe_body" ]] || {
  printf '[wedding-platform] original media route validation failed\n' >&2
  false
}
rm -f "$original_probe_file"
original_probe_file=""

rollback_required=0
trap - ERR
printf '[wedding-platform] deployment completed; backup: %s\n' "$BACKUP_DIR"
