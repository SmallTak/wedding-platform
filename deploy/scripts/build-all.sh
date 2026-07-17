#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NODE_DOCKER_IMAGE="${NODE_DOCKER_IMAGE:-node:22.18.0-bookworm-slim}"

node_supported() {
  command -v node >/dev/null 2>&1 || return 1
  node -e '
    const [major, minor] = process.versions.node.split(".").map(Number);
    process.exit(
      (major === 20 && minor >= 19) ||
      (major === 22 && minor >= 12) ||
      major > 22 ? 0 : 1
    );
  ' >/dev/null 2>&1
}

build_frontend() {
  local directory="$1"
  local base_path="${2:-}"

  if [[ -x /opt/node22/bin/node ]]; then
    PATH="/opt/node22/bin:$PATH"
    export PATH
  fi

  if node_supported && command -v npm >/dev/null 2>&1; then
    (
      cd "$directory"
      npm ci
      if [[ -n "$base_path" ]]; then
        VITE_BASE_PATH="$base_path" npm run build
      else
        npm run build
      fi
    )
    return
  fi

  command -v docker >/dev/null 2>&1 || {
    printf '[wedding-platform] Node.js 20.19+ or Docker is required for frontend builds\n' >&2
    exit 1
  }

  local docker_args=(
    run --rm
    --user "$(id -u):$(id -g)"
    -e HOME=/tmp
    -e npm_config_cache=/tmp/npm-cache
    -v "$directory:/workspace"
    -w /workspace
  )
  if [[ -n "$base_path" ]]; then
    docker_args+=(-e "VITE_BASE_PATH=$base_path")
  fi
  docker "${docker_args[@]}" "$NODE_DOCKER_IMAGE" sh -lc 'npm ci && npm run build'
}

if ! java -version 2>&1 | head -n 1 | grep -Eq '"(17|18|19|2[0-9])\.'; then
  if [[ -x /opt/java/jdk17/bin/java ]]; then
    JAVA_HOME=/opt/java/jdk17
    PATH="$JAVA_HOME/bin:$PATH"
    export JAVA_HOME PATH
  else
    printf '[wedding-platform] Java 17 or later is required for backend builds\n' >&2
    exit 1
  fi
fi

printf '[wedding-platform] building public website\n'
build_frontend "$ROOT_DIR/wedding-web"

printf '[wedding-platform] building console for /console/\n'
build_frontend "$ROOT_DIR/wedding-console" "/console/"

printf '[wedding-platform] building Spring Boot jar\n'
(
  cd "$ROOT_DIR/wedding-server"
  ./gradlew clean bootJar
)
